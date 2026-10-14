"""Public, rate-limited Halloween Havoc/HHC community run uploads and leaderboards.

All submitted values are client-reported and unverified. This data is kept in
its own tables; it is never part of authoritative Monster Maze PB/MMR records.
"""
from __future__ import annotations

import hashlib
import json
import re
import threading
import time
import uuid as uuidlib

_LOCK = threading.Lock()
_IP_LAST = {}
_PLAYER_LAST = {}
_RATE_SECONDS = 5.0
_NAME_RE = re.compile(r"^[A-Za-z0-9_]{1,16}$")
_UUID_RE = re.compile(r"^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$")
_CONTEXTS = {"HH", "HHC"}
_SERVER_TYPES = {"STANDARD", "MPS"}
_RUN_STATUSES = {"COMPLETED", "FAILED", "PARTIAL"}
_MAX_BODY = 64 * 1024
_MAX_SPLITS = 100
_MAX_TIME_MS = 24 * 60 * 60 * 1000


def _value(query, name, default=""):
    values = query.get(name, [])
    return str(values[0]).strip() if values else default


def _uuid(value, field):
    raw = str(value or "").strip()
    if not _UUID_RE.fullmatch(raw):
        raise ValueError(field + " must be a UUID")
    return str(uuidlib.UUID(raw)).lower()


def _bounded_int(value, field, minimum, maximum):
    if isinstance(value, bool):
        raise ValueError(field + " must be an integer")
    try:
        out = int(value)
    except (ValueError, TypeError, OverflowError) as exc:
        raise ValueError(field + " must be an integer") from exc
    if out < minimum or out > maximum:
        raise ValueError(field + " is out of range")
    return out


def _safe_text(value, field, maximum, allow_empty=False):
    if not isinstance(value, str):
        raise ValueError(field + " must be text")
    result = value.strip()
    if (not result and not allow_empty) or len(result) > maximum:
        raise ValueError(field + " is empty or too long")
    if any(ord(ch) < 32 for ch in result):
        raise ValueError(field + " contains control characters")
    return result


def _ensure_schema(c):
    c.execute("""CREATE TABLE IF NOT EXISTS community_hh_runs (
        submission_id TEXT PRIMARY KEY,
        run_id TEXT NOT NULL,
        uuid TEXT NOT NULL,
        name TEXT NOT NULL,
        context TEXT NOT NULL,
        server_type TEXT NOT NULL,
        server_node TEXT NOT NULL DEFAULT '',
        started_at INTEGER NOT NULL,
        ended_at INTEGER NOT NULL,
        time_ms INTEGER NOT NULL,
        reported_time_ms INTEGER NOT NULL DEFAULT 0,
        split_count INTEGER NOT NULL DEFAULT 0,
        team_members_json TEXT NOT NULL,
        team_signature TEXT NOT NULL,
        shared_run_key TEXT NOT NULL DEFAULT '',
        finish_reason TEXT NOT NULL DEFAULT '',
        successful INTEGER NOT NULL,
        run_status TEXT NOT NULL,
        submitted_at INTEGER NOT NULL
    )""")
    c.execute("""CREATE TABLE IF NOT EXISTS community_hh_splits (
        submission_id TEXT NOT NULL,
        split_index INTEGER NOT NULL,
        run_id TEXT NOT NULL,
        uuid TEXT NOT NULL,
        name TEXT NOT NULL,
        context TEXT NOT NULL,
        server_type TEXT NOT NULL,
        server_node TEXT NOT NULL DEFAULT '',
        team_members_json TEXT NOT NULL,
        team_signature TEXT NOT NULL,
        objective TEXT NOT NULL,
        split_ms INTEGER NOT NULL,
        elapsed_ms INTEGER NOT NULL,
        status TEXT NOT NULL DEFAULT 'PASSED',
        run_successful INTEGER NOT NULL DEFAULT 0,
        run_status TEXT NOT NULL DEFAULT 'PARTIAL',
        submitted_at INTEGER NOT NULL,
        PRIMARY KEY (submission_id, split_index)
    )""")
    c.execute("CREATE INDEX IF NOT EXISTS idx_community_hh_runs_board ON community_hh_runs(context,server_type,successful,time_ms,submitted_at)")
    c.execute("CREATE INDEX IF NOT EXISTS idx_community_hh_runs_player ON community_hh_runs(uuid,submitted_at DESC)")
    c.execute("CREATE INDEX IF NOT EXISTS idx_community_hh_splits_board ON community_hh_splits(context,server_type,objective,split_ms,submitted_at)")
    c.execute("CREATE INDEX IF NOT EXISTS idx_community_hh_splits_team ON community_hh_splits(team_signature,context,objective,split_ms)")
    c.commit()


def _normalize_payload(body):
    if not isinstance(body, (bytes, bytearray)) or not body or len(body) > _MAX_BODY:
        raise ValueError("invalid_payload_size")
    try:
        payload = json.loads(body.decode("utf-8"))
    except (UnicodeDecodeError, json.JSONDecodeError) as exc:
        raise ValueError("invalid_json") from exc
    if not isinstance(payload, dict):
        raise ValueError("payload must be an object")

    submission_id = _uuid(payload.get("submissionId"), "submissionId")
    run_id = _uuid(payload.get("runId"), "runId")
    player_uuid = _uuid(payload.get("uuid"), "uuid")
    name = _safe_text(payload.get("name"), "name", 16)
    if not _NAME_RE.fullmatch(name):
        raise ValueError("name must be a Minecraft username")
    context = str(payload.get("context", "")).strip().upper()
    if context not in _CONTEXTS:
        raise ValueError("context must be HH or HHC")
    server_type = str(payload.get("serverType", "STANDARD")).strip().upper()
    if server_type not in _SERVER_TYPES:
        raise ValueError("serverType must be STANDARD or MPS")

    started_at = _bounded_int(payload.get("startedAt"), "startedAt", 0, 9_999_999_999_999)
    ended_at = _bounded_int(payload.get("endedAt"), "endedAt", 1, 9_999_999_999_999)
    if ended_at < started_at:
        raise ValueError("endedAt cannot precede startedAt")
    time_ms = _bounded_int(payload.get("timeMs"), "timeMs", 1, _MAX_TIME_MS)
    reported_time_ms = _bounded_int(payload.get("reportedTimeMs", 0), "reportedTimeMs", 0, _MAX_TIME_MS)
    start_players = _bounded_int(payload.get("lobbyPlayerCount", -1), "lobbyPlayerCount", -1, 64)
    max_players = _bounded_int(payload.get("maxLobbyPlayerCount", -1), "maxLobbyPlayerCount", -1, 64)
    if server_type == "MPS":
        # The current mod only records verified MPS groups of up to four. The report
        # remains unverified, but reject obviously invalid population claims.
        if start_players < 1 or max_players < 1 or max_players > 4 or start_players > max_players:
            raise ValueError("invalid MPS lobby population")

    status = str(payload.get("runStatus", "")).strip().upper()
    successful_value = payload.get("successful")
    if not isinstance(successful_value, bool):
        raise ValueError("successful must be a boolean")
    successful = 1 if successful_value else 0
    if status not in _RUN_STATUSES:
        raise ValueError("runStatus must be COMPLETED, FAILED, or PARTIAL")
    if successful and status != "COMPLETED":
        raise ValueError("successful runs must have COMPLETED status")
    if not successful and status == "COMPLETED":
        raise ValueError("unsuccessful runs cannot have COMPLETED status")

    members = payload.get("teamMembers")
    if not isinstance(members, list) or not 1 <= len(members) <= 4:
        raise ValueError("teamMembers must contain one to four members")
    normalized_members = []
    seen_uuids = set()
    for member in members:
        if not isinstance(member, dict):
            raise ValueError("team member must be an object")
        member_uuid = _uuid(member.get("uuid"), "team member uuid")
        member_name = _safe_text(member.get("name"), "team member name", 16)
        if not _NAME_RE.fullmatch(member_name) or member_uuid in seen_uuids:
            raise ValueError("invalid or duplicate team member")
        seen_uuids.add(member_uuid)
        normalized_members.append({"uuid": member_uuid, "name": member_name})
    if player_uuid not in seen_uuids:
        raise ValueError("teamMembers must include the uploading player")

    signature = _safe_text(payload.get("teamSignature", ""), "teamSignature", 256, allow_empty=True)
    if not signature:
        signature = hashlib.sha256("|".join(sorted(seen_uuids)).encode("ascii")).hexdigest()
    shared_run_key = _safe_text(payload.get("sharedRunKey", ""), "sharedRunKey", 256, allow_empty=True)
    server_node = _safe_text(payload.get("serverNode", ""), "serverNode", 80, allow_empty=True)
    finish_reason = _safe_text(payload.get("finishReason", ""), "finishReason", 240, allow_empty=True)

    splits = payload.get("splits")
    if not isinstance(splits, list) or len(splits) > _MAX_SPLITS:
        raise ValueError("splits must be an array of at most 100 entries")
    normalized_splits = []
    previous_elapsed = 0
    for position, split in enumerate(splits, start=1):
        if not isinstance(split, dict):
            raise ValueError("split must be an object")
        index = _bounded_int(split.get("index", position), "split index", 1, _MAX_SPLITS)
        if index != position:
            raise ValueError("split indexes must be sequential starting at 1")
        objective = _safe_text(split.get("objective"), "objective", 120)
        split_ms = _bounded_int(split.get("splitMs"), "splitMs", 1, _MAX_TIME_MS)
        elapsed_ms = _bounded_int(split.get("elapsedMs"), "elapsedMs", 1, _MAX_TIME_MS)
        if elapsed_ms < split_ms or elapsed_ms < previous_elapsed:
            raise ValueError("split elapsed time is inconsistent")
        if split_ms > elapsed_ms - previous_elapsed:
            raise ValueError("split duration exceeds elapsed interval")
        split_status = str(split.get("status", "PASSED")).strip().upper()
        if split_status != "PASSED":
            raise ValueError("only completed objective splits may be submitted")
        normalized_splits.append({
            "index": index, "objective": objective, "splitMs": split_ms,
            "elapsedMs": elapsed_ms, "status": "PASSED",
            "completedAt": _bounded_int(split.get("completedAt", 0), "completedAt", 0, 9_999_999_999_999),
        })
        previous_elapsed = elapsed_ms
    if int(payload.get("splitCount", len(normalized_splits))) != len(normalized_splits):
        raise ValueError("splitCount does not match submitted splits")

    submitted_at = int(time.time() * 1000)
    return {
        "submission_id": submission_id,
        "run_id": run_id,
        "uuid": player_uuid,
        "name": name,
        "context": context,
        "server_type": server_type,
        "server_node": server_node,
        "started_at": started_at,
        "ended_at": ended_at,
        "time_ms": time_ms,
        "reported_time_ms": reported_time_ms,
        "split_count": len(normalized_splits),
        "team_members": normalized_members,
        "team_members_json": json.dumps(normalized_members, separators=(",", ":"), ensure_ascii=False),
        "team_signature": signature,
        "shared_run_key": shared_run_key,
        "finish_reason": finish_reason,
        "successful": successful,
        "run_status": status,
        "splits": normalized_splits,
        "submitted_at": submitted_at,
    }


def handle_submit(body, client_ip, db_fn):
    """Return (HTTP status, JSON object) for POST /api/v1/mod/halloween-havoc/runs."""
    try:
        run = _normalize_payload(body)
    except (ValueError, TypeError, KeyError, OverflowError) as exc:
        return 400, {"ok": False, "error": str(exc)}
    player_uuid = run["uuid"]
    submission_id = run["submission_id"]
    now = time.monotonic()

    with _LOCK:
        c = db_fn()
        try:
            _ensure_schema(c)
            existing = c.execute(
                "SELECT uuid FROM community_hh_runs WHERE submission_id=?", (submission_id,)
            ).fetchone()
            if existing:
                if str(existing[0]).lower() != player_uuid:
                    return 409, {"ok": False, "error": "submission_id_conflict"}
                # A retry following a lost ACK is accepted before rate limiting.
                return 200, {"ok": True, "accepted": True, "duplicate": True, "submissionId": submission_id}
            last_player = _PLAYER_LAST.get(player_uuid, 0.0)
            if now - last_player < _RATE_SECONDS:
                return 429, {"ok": False, "error": "player_rate_limited", "retryAfterSeconds": max(1, int(_RATE_SECONDS - (now - last_player)))}
            last_ip = _IP_LAST.get(str(client_ip), 0.0)
            if now - last_ip < 0.25:
                return 429, {"ok": False, "error": "ip_rate_limited", "retryAfterSeconds": 1}

            c.execute("BEGIN IMMEDIATE")
            c.execute("""INSERT INTO community_hh_runs (
                submission_id,run_id,uuid,name,context,server_type,server_node,
                started_at,ended_at,time_ms,reported_time_ms,split_count,
                team_members_json,team_signature,shared_run_key,finish_reason,
                successful,run_status,submitted_at
            ) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)""", (
                run["submission_id"],run["run_id"],run["uuid"],run["name"],run["context"],
                run["server_type"],run["server_node"],run["started_at"],run["ended_at"],
                run["time_ms"],run["reported_time_ms"],run["split_count"],run["team_members_json"],
                run["team_signature"],run["shared_run_key"],run["finish_reason"],run["successful"],
                run["run_status"],run["submitted_at"]
            ))
            for split in run["splits"]:
                c.execute("""INSERT INTO community_hh_splits (
                    submission_id,split_index,run_id,uuid,name,context,server_type,server_node,
                    team_members_json,team_signature,objective,split_ms,elapsed_ms,status,
                    run_successful,run_status,submitted_at
                ) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)""", (
                    run["submission_id"],split["index"],run["run_id"],run["uuid"],run["name"],
                    run["context"],run["server_type"],run["server_node"],run["team_members_json"],
                    run["team_signature"],split["objective"],split["splitMs"],split["elapsedMs"],
                    split["status"],run["successful"],run["run_status"],run["submitted_at"]
                ))
            c.commit()
            _PLAYER_LAST[player_uuid] = now
            _IP_LAST[str(client_ip)] = now
            return 200, {"ok": True, "accepted": True, "duplicate": False, "submissionId": submission_id,
                         "storedSplits": len(run["splits"])}
        except Exception as exc:
            c.rollback()
            print("[hh-api] submit failed: %s" % exc, flush=True)
            return 500, {"ok": False, "error": "internal_error"}
        finally:
            c.close()


def _json_members(raw):
    try:
        parsed = json.loads(raw or "[]")
        return parsed if isinstance(parsed, list) else []
    except (json.JSONDecodeError, TypeError):
        return []


def _run_row(row):
    keys = ("submissionId", "runId", "uuid", "name", "context", "serverType",
            "serverNode", "startedAt", "endedAt", "timeMs", "reportedTimeMs",
            "splitCount", "teamSignature", "sharedRunKey", "finishReason",
            "successful", "runStatus", "submittedAt", "teamMembers")
    data = dict(zip(keys, row))
    data["successful"] = bool(data["successful"])
    data["isMpsRun"] = str(data["serverType"]).upper() == "MPS"
    data["verified"] = False
    data["source"] = "community"
    return data


def _run_select(c, where_sql, args):
    sql = """SELECT submission_id,run_id,uuid,name,context,server_type,server_node,
                     started_at,ended_at,time_ms,reported_time_ms,split_count,
                     team_signature,shared_run_key,finish_reason,successful,
                     run_status,submitted_at,team_members_json
             FROM community_hh_runs"""
    if where_sql:
        sql += " WHERE " + where_sql
    return c.execute(sql, tuple(args)).fetchall()


def _split_dict(row):
    keys = ("submissionId", "splitIndex", "runId", "uuid", "name", "context", "serverType",
            "serverNode", "teamSignature", "objective", "splitMs", "elapsedMs", "status",
            "runSuccessful", "runStatus", "submittedAt", "teamMembers")
    data = dict(zip(keys, row))
    data["runSuccessful"] = bool(data["runSuccessful"])
    data["successful"] = data["runSuccessful"]
    data["isMpsRun"] = str(data["serverType"]).upper() == "MPS"
    data["index"] = data["splitIndex"]
    data["verified"] = False
    data["source"] = "community"
    return data


def _split_select(c, where_sql, args):
    sql = """SELECT submission_id,split_index,run_id,uuid,name,context,server_type,
                     server_node,team_signature,objective,split_ms,elapsed_ms,status,
                     run_successful,run_status,submitted_at,team_members_json
             FROM community_hh_splits"""
    if where_sql:
        sql += " WHERE " + where_sql
    return c.execute(sql, tuple(args)).fetchall()


def _best_runs(rows):
    best = {}
    for row in rows:
        run = _run_row(row)
        run["teamMembers"] = _json_members(run.get("teamMembers"))
        key = (run["context"], run["serverType"], run["teamSignature"] or run["uuid"])
        existing = best.get(key)
        if existing is None or (int(run["timeMs"]), -int(run["submittedAt"])) < (int(existing["timeMs"]), -int(existing["submittedAt"])):
            best[key] = run
    result = sorted(best.values(), key=lambda item: (int(item["timeMs"]), item["teamSignature"], item["name"].lower()))
    return result[:100]


def _best_splits(rows, objective=None):
    best = {}
    for row in rows:
        split = _split_dict(row)
        split["teamMembers"] = _json_members(split.get("teamMembers"))
        if objective and split["objective"].casefold() != objective.casefold():
            continue
        key = (split["context"], split["serverType"], split["teamSignature"] or split["uuid"], split["objective"].casefold())
        existing = best.get(key)
        candidate_key = (int(split["splitMs"]), int(split["elapsedMs"]), -int(split["submittedAt"]))
        if existing is None or candidate_key < (int(existing["splitMs"]), int(existing["elapsedMs"]), -int(existing["submittedAt"])):
            best[key] = split
    result = sorted(best.values(), key=lambda item: (int(item["splitMs"]), item["objective"].casefold(), item["teamSignature"], item["name"].lower()))
    return result[:500]


def handle_get(path, query, db_fn):
    """Return (status, payload) for HH/HHC community boards and group profiles."""
    if path != "/api/v1/mod/halloween-havoc/leaderboard":
        return None
    try:
        context = _value(query, "context", "HH").upper()
        if context not in ("HH", "HHC", "ALL"):
            raise ValueError("context must be HH, HHC, or ALL")
        server_type = _value(query, "serverType", "ALL").upper()
        if server_type not in ("STANDARD", "MPS", "ALL"):
            raise ValueError("serverType must be STANDARD, MPS, or ALL")
        kind = _value(query, "kind", "runs").lower()
        if kind not in ("runs", "splits", "group"):
            raise ValueError("kind must be runs, splits, or group")
        objective = _value(query, "objective", "")
        if len(objective) > 120 or any(ord(ch) < 32 for ch in objective):
            raise ValueError("invalid objective filter")
        team_signature = _value(query, "teamSignature", "")
        if len(team_signature) > 256:
            raise ValueError("teamSignature is too long")
        c = db_fn()
        try:
            _ensure_schema(c)
            clauses, args = [], []
            if context != "ALL":
                clauses.append("context=?"); args.append(context)
            if server_type != "ALL":
                clauses.append("server_type=?"); args.append(server_type)
            if kind == "runs":
                clauses.append("successful=1")
                rows = _run_select(c, " AND ".join(clauses), args + [])
                return 200, {"ok": True, "source": "community", "verified": False,
                             "context": context, "serverType": server_type, "kind": "runs",
                             "rows": _best_runs(rows)}
            if kind == "splits":
                split_clauses, split_args = [], []
                if context != "ALL":
                    split_clauses.append("context=?"); split_args.append(context)
                if server_type != "ALL":
                    split_clauses.append("server_type=?"); split_args.append(server_type)
                # Objective Splits deliberately include rows from unsuccessful/partial runs.
                rows = _split_select(c, " AND ".join(split_clauses), split_args)
                return 200, {"ok": True, "source": "community", "verified": False,
                             "context": context, "serverType": server_type, "kind": "splits",
                             "objective": objective, "rows": _best_splits(rows, objective or None)}
            if not team_signature:
                raise ValueError("teamSignature is required for group profile")
            group_clauses, group_args = ["team_signature=?"], [team_signature]
            if context != "ALL":
                group_clauses.append("context=?"); group_args.append(context)
            if server_type != "ALL":
                group_clauses.append("server_type=?"); group_args.append(server_type)
            runs = _run_select(c, " AND ".join(group_clauses), group_args)
            completed = _best_runs([row for row in runs if bool(row[15])])
            splits = _split_select(c, " AND ".join(group_clauses), group_args)
            return 200, {"ok": True, "source": "community", "verified": False,
                         "context": context, "serverType": server_type, "kind": "group",
                         "run": completed[0] if completed else None,
                         "splits": _best_splits(splits),
                         "attemptCount": len(runs)}
        finally:
            c.close()
    except (ValueError, TypeError) as exc:
        return 400, {"ok": False, "error": str(exc)}
