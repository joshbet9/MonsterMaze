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

# Stable seven-section Halloween Havoc route. Raw objective labels are aliases;
# leaderboard identity is checkpoint_id/order, never a display label or list index.
_CANONICAL_SPLIT_SCHEMA = 1
_CANONICAL_CHECKPOINTS = (
    ("GIANT", "Giant", ("defeat the giant", "defeat giant", "the giant", "giant")),
    ("GENDO", "Gendo", ("gendo", "defeat gendo")),
    ("CLEAR_PATH", "Find a way to clear the path",
     ("find a way to clear the path", "clear the path")),
    ("CHALLENGES_COMPLETE", "Complete the 4 challenges",
     ("complete the 4 challenges", "complete four challenges")),
    ("MINECART_LAVA", "Guide the minecart across the lava",
     ("guide the minecart across the lava", "guide minecart across the lava")),
    ("MAZE_EXIT", "Maze Exit",
     ("find an exit to the maze", "maze exit", "exit to the maze", "exit the maze")),
    ("BOARD_GREEN_SHIP", "Board Green Ship",
     ("board the green ship", "board green ship")),
    ("BOTTOM_DECK_PORTAL", "Bottom Deck Portal",
     ("enter the portal on the bottom deck", "bottom deck portal")),
    ("PUMPKIN_KING_MINIONS", "Pumpkin King's Minions",
     ("defeat the pumpkin king's minions", "defeat pumpkin king's minions",
      "pumpkin king's minions", "pumpkin kings minions", "pumpkin king minions")),
    ("PUMPKIN_KING", "Pumpkin King",
     ("pumpkin king", "defeat the pumpkin king")),
)
_CANONICAL_IDS = tuple(item[0] for item in _CANONICAL_CHECKPOINTS)
_CANONICAL_LABELS = tuple(item[1] for item in _CANONICAL_CHECKPOINTS)


def _normalized_objective(value):
    return re.sub(r"[^a-z0-9]+", " ", str(value or "").casefold().replace("’", "'")).strip()


def _checkpoint_order(objective):
    normalized = _normalized_objective(objective)
    if not normalized:
        return -1
    for order, (checkpoint_id, _label, aliases) in enumerate(_CANONICAL_CHECKPOINTS):
        for alias in aliases:
            candidate = _normalized_objective(alias)
            if normalized == candidate or normalized.endswith(" " + candidate):
                return order
    return -1


def _split_has_missing_boundary(split):
    return bool(split.get("missingCheckpointIds") or [])


def _canonicalize_one_run(raw_splits, total_elapsed_ms=0, completed=False):
    """Return canonical intervals for one run, falling back for non-HH test/legacy labels."""
    if not raw_splits:
        return []
    ordered = sorted(raw_splits, key=lambda item: (
        int(item.get("splitIndex", item.get("index", 0)) or 0),
        int(item.get("elapsedMs", 0) or 0)))
    # New submissions already carry canonical IDs and skip-boundary metadata. Preserve
    # those intervals exactly instead of trying to reconstruct them from their labels.
    if any(str(item.get("checkpointId") or "") in _CANONICAL_IDS for item in ordered):
        return ordered

    recognized_orders = { _checkpoint_order(item.get("objective", "")) for item in ordered }
    recognized_orders.discard(-1)
    # A terminal-only "Pumpkin King" label appears in generic/legacy data; require
    # at least one non-terminal milestone to identify an HH route. This still lets a
    # short partial run with only the Giant checkpoint use the canonical schema.
    if not any(order < len(_CANONICAL_CHECKPOINTS) - 1 for order in recognized_orders):
        return ordered

    result = []
    last_elapsed = 0
    last_order = -1
    for raw in ordered:
        order = _checkpoint_order(raw.get("objective", ""))
        elapsed = int(raw.get("elapsedMs") or 0)
        if order < 0 or order <= last_order or elapsed <= last_elapsed:
            continue
        checkpoint_id, label, _aliases = _CANONICAL_CHECKPOINTS[order]
        row = dict(raw)
        row.update({
            "schemaVersion": _CANONICAL_SPLIT_SCHEMA,
            "index": order + 1,
            "splitIndex": order + 1,
            "checkpointId": checkpoint_id,
            "fromCheckpointId": "START" if last_order < 0 else _CANONICAL_IDS[last_order],
            "objective": label,
            "elapsedMs": elapsed,
            "splitMs": elapsed - last_elapsed,
            "status": "PASSED",
            "boundarySource": "OBJECTIVE",
            "missingCheckpointIds": list(_CANONICAL_IDS[last_order + 1:order]),
        })
        result.append(row)
        last_elapsed = elapsed
        last_order = order

    terminal_order = len(_CANONICAL_CHECKPOINTS) - 1
    if completed and total_elapsed_ms > 0 and total_elapsed_ms > last_elapsed:
        if last_order == terminal_order and result:
            # A trusted completion can adjust a late/rounded Pumpkin King boundary
            # without changing the local timer or inventing any prior checkpoint.
            prior_elapsed = int(result[-2].get("elapsedMs") or 0) if len(result) > 1 else 0
            result[-1]["elapsedMs"] = int(total_elapsed_ms)
            result[-1]["splitMs"] = int(total_elapsed_ms) - prior_elapsed
            result[-1]["boundarySource"] = "CONFIRMED_RUN_END"
        else:
            checkpoint_id, label, _aliases = _CANONICAL_CHECKPOINTS[terminal_order]
            row = dict(ordered[-1])
            row.update({
                "schemaVersion": _CANONICAL_SPLIT_SCHEMA,
                "index": terminal_order + 1,
                "splitIndex": terminal_order + 1,
                "checkpointId": checkpoint_id,
                "fromCheckpointId": "START" if last_order < 0 else _CANONICAL_IDS[last_order],
                "objective": label,
                "elapsedMs": int(total_elapsed_ms),
                "splitMs": int(total_elapsed_ms) - last_elapsed,
                "status": "PASSED",
                "boundarySource": "CONFIRMED_RUN_END",
                # Missing intermediate checkpoints are flagged, but Pumpkin King itself
                # is known to be the terminal completion boundary.
                "missingCheckpointIds": list(_CANONICAL_IDS[last_order + 1:terminal_order]),
            })
            result.append(row)
    return result


def _canonicalize_split_rows(rows):
    """Canonicalize split DB rows in per-submission groups, including legacy submissions."""
    groups = {}
    order = []
    for raw in rows:
        item = _split_dict(raw) if not isinstance(raw, dict) else dict(raw)
        key = str(item.get("submissionId") or item.get("runId") or item.get("teamSignature") or "unknown")
        if key not in groups:
            groups[key] = []
            order.append(key)
        groups[key].append(item)

    out = []
    for key in order:
        items = groups[key]
        first = items[0]
        completed = bool(first.get("runSuccessful") or first.get("runRecordSuccessful"))
        duration = max([int(item.get("runTimeMs") or 0) for item in items] + [0])
        out.extend(_canonicalize_one_run(items, duration, completed))
    return sorted(out, key=lambda item: (
        str(item.get("context") or ""), str(item.get("serverType") or ""),
        int(item.get("splitIndex", item.get("index", 999)) or 999),
        str(item.get("objective") or "").casefold(),
        int(item.get("submittedAt") or 0)))


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
        checkpoint_id TEXT NOT NULL DEFAULT '',
        from_checkpoint_id TEXT NOT NULL DEFAULT '',
        missing_checkpoint_ids_json TEXT NOT NULL DEFAULT '[]',
        boundary_source TEXT NOT NULL DEFAULT 'OBJECTIVE',
        schema_version INTEGER NOT NULL DEFAULT 0,
        PRIMARY KEY (submission_id, split_index)
    )""")
    # Upgrade databases created by an earlier review build that did not yet retain
    # the outcome status on each split row. SQLite's CREATE IF NOT EXISTS does not
    # add new columns to an existing table.
    split_columns = {row[1] for row in c.execute("PRAGMA table_info(community_hh_splits)").fetchall()}
    if "run_status" not in split_columns:
        c.execute("ALTER TABLE community_hh_splits ADD COLUMN run_status TEXT NOT NULL DEFAULT 'PARTIAL'")
    # Persist canonical interval identity and skipped-boundary evidence. Defaults make
    # legacy rows safe to read; their canonical view is derived without rewriting them.
    migrations = (
        ("checkpoint_id", "TEXT NOT NULL DEFAULT ''"),
        ("from_checkpoint_id", "TEXT NOT NULL DEFAULT ''"),
        ("missing_checkpoint_ids_json", "TEXT NOT NULL DEFAULT '[]'"),
        ("boundary_source", "TEXT NOT NULL DEFAULT 'OBJECTIVE'"),
        ("schema_version", "INTEGER NOT NULL DEFAULT 0"),
    )
    for column, declaration in migrations:
        if column not in split_columns:
            c.execute("ALTER TABLE community_hh_splits ADD COLUMN %s %s" % (column, declaration))

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
    previous_index = 0
    for position, split in enumerate(splits, start=1):
        if not isinstance(split, dict):
            raise ValueError("split must be an object")
        index = _bounded_int(split.get("index", position), "split index", 1, _MAX_SPLITS)
        # Canonical route indices are stable checkpoint positions, so an index may be
        # absent when a checkpoint was skipped. They must remain strictly increasing.
        if index <= previous_index:
            raise ValueError("split indexes must be strictly increasing")
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

        checkpoint_id = _safe_text(split.get("checkpointId", ""), "checkpointId", 40, allow_empty=True)
        if checkpoint_id and checkpoint_id not in _CANONICAL_IDS:
            raise ValueError("unknown canonical checkpointId")
        from_checkpoint_id = _safe_text(split.get("fromCheckpointId", ""), "fromCheckpointId", 40, allow_empty=True)
        if from_checkpoint_id and from_checkpoint_id != "START" and from_checkpoint_id not in _CANONICAL_IDS:
            raise ValueError("unknown fromCheckpointId")
        missing_raw = split.get("missingCheckpointIds", [])
        if not isinstance(missing_raw, list) or len(missing_raw) > len(_CANONICAL_IDS):
            raise ValueError("missingCheckpointIds must be a short array")
        missing_ids = []
        for missing_id in missing_raw:
            missing_id = _safe_text(missing_id, "missing checkpoint ID", 40)
            if missing_id not in _CANONICAL_IDS or missing_id in missing_ids:
                raise ValueError("invalid or duplicate missingCheckpointIds entry")
            missing_ids.append(missing_id)
        boundary_source = _safe_text(split.get("boundarySource", "OBJECTIVE"), "boundarySource", 32)
        if boundary_source not in ("OBJECTIVE", "CONFIRMED_RUN_END"):
            raise ValueError("invalid boundarySource")
        schema_version = _bounded_int(split.get("schemaVersion", 0), "schemaVersion", 0, _CANONICAL_SPLIT_SCHEMA)

        normalized_splits.append({
            "index": index, "objective": objective, "splitMs": split_ms,
            "elapsedMs": elapsed_ms, "status": "PASSED",
            "completedAt": _bounded_int(split.get("completedAt", 0), "completedAt", 0, 9_999_999_999_999),
            "checkpointId": checkpoint_id, "fromCheckpointId": from_checkpoint_id,
            "missingCheckpointIds": missing_ids, "boundarySource": boundary_source,
            "schemaVersion": schema_version,
        })
        previous_elapsed = elapsed_ms
        previous_index = index
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
                    run_successful,run_status,submitted_at,checkpoint_id,from_checkpoint_id,
                    missing_checkpoint_ids_json,boundary_source,schema_version
                ) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)""", (
                    run["submission_id"],split["index"],run["run_id"],run["uuid"],run["name"],
                    run["context"],run["server_type"],run["server_node"],run["team_members_json"],
                    run["team_signature"],split["objective"],split["splitMs"],split["elapsedMs"],
                    split["status"],run["successful"],run["run_status"],run["submitted_at"],
                    split["checkpointId"],split["fromCheckpointId"],
                    json.dumps(split["missingCheckpointIds"], separators=(",", ":")),
                    split["boundarySource"],split["schemaVersion"]
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
            "runSuccessful", "runStatus", "submittedAt", "teamMembers", "checkpointId",
            "fromCheckpointId", "missingCheckpointIdsJson", "boundarySource", "schemaVersion",
            "runTimeMs", "runRecordSuccessful")
    data = dict(zip(keys, row))
    data["runSuccessful"] = bool(data.get("runSuccessful"))
    data["runRecordSuccessful"] = bool(data.get("runRecordSuccessful"))
    data["successful"] = data["runSuccessful"]
    data["isMpsRun"] = str(data.get("serverType") or "").upper() == "MPS"
    data["index"] = data.get("splitIndex")
    try:
        missing = json.loads(data.get("missingCheckpointIdsJson") or "[]")
        data["missingCheckpointIds"] = missing if isinstance(missing, list) else []
    except (json.JSONDecodeError, TypeError):
        data["missingCheckpointIds"] = []
    data["checkpointId"] = str(data.get("checkpointId") or "")
    data["fromCheckpointId"] = str(data.get("fromCheckpointId") or "")
    data["boundarySource"] = str(data.get("boundarySource") or "OBJECTIVE")
    data["schemaVersion"] = int(data.get("schemaVersion") or 0)
    data["runTimeMs"] = int(data.get("runTimeMs") or 0)
    data["verified"] = False
    data["source"] = "community"
    return data


def _split_select(c, where_sql, args):
    sql = """SELECT submission_id,split_index,run_id,uuid,name,context,server_type,
                     server_node,team_signature,objective,split_ms,elapsed_ms,status,
                     run_successful,run_status,submitted_at,team_members_json,
                     checkpoint_id,from_checkpoint_id,missing_checkpoint_ids_json,
                     boundary_source,schema_version,
                     (SELECT time_ms FROM community_hh_runs r WHERE r.submission_id=community_hh_splits.submission_id),
                     (SELECT successful FROM community_hh_runs r WHERE r.submission_id=community_hh_splits.submission_id)
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


def _best_splits(rows, objective=None, split_index=None):
    """Return fastest clean canonical intervals per group; do not compare merged gaps."""
    best = {}
    for split in _canonicalize_split_rows(rows):
        split["teamMembers"] = _json_members(split.get("teamMembers"))
        if _split_has_missing_boundary(split):
            continue
        if objective and split["objective"].casefold() != objective.casefold():
            continue
        occurrence = int(split.get("splitIndex", split.get("index", 0)) or 0)
        if split_index is not None and occurrence != split_index:
            continue
        key = (split["context"], split["serverType"], split["teamSignature"] or split["uuid"],
               occurrence, str(split.get("checkpointId") or split["objective"]).casefold())
        existing = best.get(key)
        candidate_key = (int(split["splitMs"]), int(split["elapsedMs"]), -int(split["submittedAt"]))
        if existing is None or candidate_key < (int(existing["splitMs"]), int(existing["elapsedMs"]), -int(existing["submittedAt"])):
            best[key] = split
    return sorted(best.values(), key=lambda item: (
        int(item.get("splitIndex", item.get("index", 999)) or 999), item["objective"].casefold(),
        int(item["splitMs"]), item["teamSignature"], item["name"].lower()))



def _best_known_split_benchmarks(rows, required_objectives):
    """Build a chronological BKS composite, treating repeated names as separate stages."""
    best = {}
    for split in _canonicalize_split_rows(rows):
        split["teamMembers"] = _json_members(split.get("teamMembers"))
        objective = str(split.get("objective", "")).strip()
        split_ms = int(split.get("splitMs") or 0)
        elapsed_ms = int(split.get("elapsedMs") or 0)
        split_index = int(split.get("splitIndex", split.get("index", 0)) or 0)
        if _split_has_missing_boundary(split):
            continue
        if not objective or split_ms <= 0 or elapsed_ms <= 0 or split_index <= 0:
            continue
        scope = (str(split["context"]).upper(), str(split["serverType"]).upper())
        key = scope + (split_index, objective.casefold())
        prior = best.get(key)
        score = (split_ms, elapsed_ms, -int(split.get("submittedAt") or 0))
        prior_score = ((int(prior.get("splitMs") or 0), int(prior.get("elapsedMs") or 0),
                        -int(prior.get("submittedAt") or 0)) if prior else None)
        if prior is None or score < prior_score:
            best[key] = split

    scopes = sorted(set((key[0], key[1]) for key in best))
    benchmarks = []
    for scope in scopes:
        scope_key = scope[0] + "|" + scope[1]
        required = list(required_objectives.get(scope_key) or [])
        available = [item for key, item in best.items() if key[:2] == scope]
        required_keys = {(index, str(name).casefold()) for index, name in enumerate(required, 1)}
        if required:
            ordered_entries = [(index, name, best.get(scope + (index, str(name).casefold())))
                               for index, name in enumerate(required, 1)]
            extras = [item for item in available
                      if (int(item.get("splitIndex") or 0), str(item["objective"]).casefold()) not in required_keys]
            extras.sort(key=lambda item: (int(item.get("splitIndex") or 999), str(item["objective"]).casefold()))
            ordered_entries.extend((int(item.get("splitIndex") or 999), str(item["objective"]), item) for item in extras)
        else:
            available.sort(key=lambda item: (int(item.get("splitIndex") or 999), str(item["objective"]).casefold()))
            ordered_entries = [(int(item.get("splitIndex") or position), str(item["objective"]), item)
                               for position, item in enumerate(available, 1)]

        composite_rows = []
        cumulative = 0
        present = set()
        missing = []
        for index, name, split in ordered_entries:
            if split is None:
                if required and index <= len(required):
                    missing.append(name)
                continue
            cumulative += int(split["splitMs"])
            identity = (int(split.get("splitIndex") or index), str(split["objective"]).casefold())
            present.add(identity)
            split["compositeIndex"] = index
            split["compositeSplitMs"] = int(split["splitMs"])
            split["compositeElapsedMs"] = cumulative
            split["sourceRunId"] = split.get("runId", "")
            split["sourceSubmissionId"] = split.get("submissionId", "")
            split["sourceAttemptStatus"] = split.get("runStatus", "UNKNOWN")
            composite_rows.append(split)

        complete = bool(required) and not missing and len(composite_rows) >= len(required)
        benchmarks.append({
            "context": scope[0],
            "serverType": scope[1],
            "kind": "bks",
            "verified": False,
            "complete": complete,
            "requiredObjectives": required,
            "requiredObjectiveCount": len(required),
            "knownObjectiveCount": len(composite_rows),
            "knownCompositeTimeMs": cumulative,
            "totalTimeMs": cumulative if complete else None,
            "missingObjectives": missing,
            "rows": composite_rows,
        })
    return benchmarks



def _required_objectives_from_completed_runs(c, context, server_type):
    """Use the fixed seven-stage HH route when real canonical milestones are present."""
    clauses = ["successful=1"]
    args = []
    if context != "ALL":
        clauses.append("context=?")
        args.append(context)
    if server_type != "ALL":
        clauses.append("server_type=?")
        args.append(server_type)
    completed_rows = _run_select(c, " AND ".join(clauses), args)
    fallback_sequences = {}
    canonical_scopes = set()
    for raw_run in completed_rows:
        run = _run_row(raw_run)
        scope = (str(run.get("context", "HH")).upper(), str(run.get("serverType", "STANDARD")).upper())
        scope_key = scope[0] + "|" + scope[1]
        raw_splits = _split_select(c, "submission_id=?", [run["submissionId"]])
        normalized = _canonicalize_split_rows(raw_splits)
        has_canonical_route = any(
            str(item.get("checkpointId") or "") in _CANONICAL_IDS
            for item in normalized
        )
        if has_canonical_route:
            canonical_scopes.add(scope_key)
            continue
        ordered = []
        for item in normalized:
            objective = str(item.get("objective", "")).strip()
            if objective:
                ordered.append(objective)
        if len(ordered) > len(fallback_sequences.get(scope_key, [])):
            fallback_sequences[scope_key] = ordered

    chosen = dict(fallback_sequences)
    for scope_key in canonical_scopes:
        chosen[scope_key] = list(_CANONICAL_LABELS)
    return chosen


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
        if kind not in ("runs", "splits", "group", "history", "bks"):
            raise ValueError("kind must be runs, splits, group, history, or bks")
        objective = _value(query, "objective", "")
        if len(objective) > 120 or any(ord(ch) < 32 for ch in objective):
            raise ValueError("invalid objective filter")
        split_index_text = _value(query, "splitIndex", "").strip()
        split_index = None
        if split_index_text:
            try:
                split_index = int(split_index_text)
            except (TypeError, ValueError) as exc:
                raise ValueError("splitIndex must be a positive integer") from exc
            if split_index < 1 or split_index > 500:
                raise ValueError("splitIndex must be a positive integer")
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
            if kind == "history":
                # History is personal, not a leaderboard aggregate. Return recent records
                # for this player (including successful, failed, and partial attempts) so
                # the mod can display runs which were uploaded and acknowledged earlier.
                player_uuid = _uuid(_value(query, "uuid", ""), "uuid")
                history_rows = c.execute("""SELECT submission_id,run_id,uuid,name,context,server_type,
                                                   server_node,started_at,ended_at,time_ms,reported_time_ms,
                                                   split_count,team_signature,shared_run_key,finish_reason,
                                                   successful,run_status,submitted_at,team_members_json
                                            FROM community_hh_runs
                                            WHERE lower(uuid)=?
                                            ORDER BY ended_at DESC, submitted_at DESC
                                            LIMIT 100""", (player_uuid,)).fetchall()
                history = []
                for raw_run in history_rows:
                    run = _run_row(raw_run)
                    run["teamMembers"] = _json_members(run.get("teamMembers"))
                    raw_splits = _split_select(c, "submission_id=?", [run["submissionId"]])
                    raw_split_dicts = [_split_dict(raw_split) for raw_split in raw_splits]
                    raw_split_dicts.sort(key=lambda item: int(item.get("splitIndex", 0)))
                    canonical_splits = _canonicalize_split_rows(raw_splits)
                    run["splits"] = raw_split_dicts
                    run["rawSplits"] = raw_split_dicts
                    run["canonicalSplits"] = canonical_splits
                    run["rawSplitCount"] = len(raw_split_dicts)
                    run["splitCount"] = len(canonical_splits)
                    run["splitSchemaVersion"] = _CANONICAL_SPLIT_SCHEMA if any(
                        str(item.get("checkpointId") or "") in _CANONICAL_IDS for item in canonical_splits
                    ) else 0
                    history.append(run)
                return 200, {"ok": True, "source": "community", "verified": False,
                             "kind": "history", "playerUuid": player_uuid, "rows": history}
            if kind == "runs":
                clauses.append("successful=1")
                rows = _run_select(c, " AND ".join(clauses), args + [])
                return 200, {"ok": True, "source": "community", "verified": False,
                             "context": context, "serverType": server_type, "kind": "runs",
                             "rows": _best_runs(rows)}
            if kind == "bks":
                split_clauses, split_args = [], []
                if context != "ALL":
                    split_clauses.append("context=?"); split_args.append(context)
                if server_type != "ALL":
                    split_clauses.append("server_type=?"); split_args.append(server_type)
                # Partial/failed attempts are eligible because only completed objectives matter.
                split_rows = _split_select(c, " AND ".join(split_clauses), split_args)
                required = _required_objectives_from_completed_runs(c, context, server_type)
                benchmarks = _best_known_split_benchmarks(split_rows, required)

                # Preserve the genuine fastest completed run as a separate WR target. The
                # composite BKS timeline is not itself a completed player/team run.
                run_clauses = ["successful=1"]
                run_args = []
                if context != "ALL":
                    run_clauses.append("context=?"); run_args.append(context)
                if server_type != "ALL":
                    run_clauses.append("server_type=?"); run_args.append(server_type)
                world_records = {}
                for raw_run in _run_select(c, " AND ".join(run_clauses), run_args):
                    run = _run_row(raw_run)
                    scope_key = str(run.get("context", "HH")).upper() + "|" + str(run.get("serverType", "STANDARD")).upper()
                    prior = world_records.get(scope_key)
                    if prior is None or int(run.get("timeMs") or 0) < int(prior.get("timeMs") or 0):
                        run["teamMembers"] = _json_members(run.get("teamMembers"))
                        raw_run_splits = _split_select(c, "submission_id=?", [run["submissionId"]])
                        run_splits = [_split_dict(item) for item in sorted(raw_run_splits, key=lambda item: int(item[1]))]
                        run["splits"] = run_splits
                        world_records[scope_key] = run
                for benchmark in benchmarks:
                    scope_key = benchmark["context"] + "|" + benchmark["serverType"]
                    world_record = world_records.get(scope_key)
                    # Only the completed run's time is needed to compare BKS vs WR.
                    # Embedding the entire WR run and its splits here duplicates data
                    # already represented by the BKS objective rows and can push this
                    # public response beyond the mod client's bounded JSON-read limit.
                    benchmark["worldRecordTimeMs"] = int(world_record["timeMs"]) if world_record else None
                # The per-objective rows are already nested under each benchmark. Avoid
                # returning a second flattened copy; consumers should read benchmarks[].rows.
                return 200, {"ok": True, "source": "community", "verified": False,
                             "context": context, "serverType": server_type, "kind": "bks",
                             "benchmarks": benchmarks}
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
                             "objective": objective, "splitIndex": split_index, "rows": _best_splits(rows, objective or None, split_index)}
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

            # The group profile shows global BKS and per-member best sections.
            global_clauses, global_args = [], []
            if context != "ALL":
                global_clauses.append("context=?"); global_args.append(context)
            if server_type != "ALL":
                global_clauses.append("server_type=?"); global_args.append(server_type)
            global_splits = _split_select(c, " AND ".join(global_clauses), global_args)
            required = _required_objectives_from_completed_runs(c, context, server_type)
            global_benchmarks = _best_known_split_benchmarks(global_splits, required)
            global_best_splits = [row for benchmark in global_benchmarks for row in benchmark["rows"]]

            team_members = {}
            for raw_group_run in runs:
                group_run = _run_row(raw_group_run)
                members = _json_members(group_run.get("teamMembers"))
                for member in members:
                    member_uuid = str(member.get("uuid", "")).strip().lower()
                    if member_uuid and member_uuid not in team_members:
                        team_members[member_uuid] = str(member.get("name", "Unknown"))

            member_best = {}
            if team_members:
                clauses = ["(" + " OR ".join(["team_members_json LIKE ?" for _ in team_members]) + ")"]
                args = ["%" + member_uuid + "%" for member_uuid in team_members]
                if context != "ALL":
                    clauses.append("context=?"); args.append(context)
                if server_type != "ALL":
                    clauses.append("server_type=?"); args.append(server_type)
                related_runs = _run_select(c, " AND ".join(clauses), args)
                roster_ids = set(team_members.keys())
                for raw_related in related_runs:
                    related = _run_row(raw_related)
                    related_members = _json_members(related.get("teamMembers"))
                    observed_ids = {str(member.get("uuid", "")).strip().lower() for member in related_members}
                    observed_ids.discard("")
                    matching_members = roster_ids.intersection(observed_ids)
                    if not matching_members:
                        continue
                    related_splits = _canonicalize_split_rows(
                        _split_select(c, "submission_id=?", [related["submissionId"]]))
                    for split in related_splits:
                        split["teamMembers"] = _json_members(split.get("teamMembers"))
                        if int(split.get("splitMs") or 0) <= 0 or _split_has_missing_boundary(split):
                            continue
                        key_base = (str(split["context"]).upper(), str(split["serverType"]).upper(),
                                    int(split.get("splitIndex") or 0), str(split["objective"]).casefold())
                        for member_uuid in matching_members:
                            key = (member_uuid,) + key_base
                            candidate = {
                                "uuid": member_uuid,
                                "name": team_members.get(member_uuid, "Unknown"),
                                "context": split["context"],
                                "serverType": split["serverType"],
                                "objective": split["objective"],
                                "index": int(split.get("splitIndex") or 0),
                                "splitIndex": int(split.get("splitIndex") or 0),
                                "splitMs": int(split["splitMs"]),
                                "elapsedMs": int(split["elapsedMs"]),
                                "teamSignature": split["teamSignature"],
                                "runId": split["runId"],
                                "submissionId": split["submissionId"],
                                "submittedAt": int(split["submittedAt"]),
                                "runStatus": split["runStatus"],
                            }
                            prior = member_best.get(key)
                            if prior is None or (candidate["splitMs"], candidate["elapsedMs"], -candidate["submittedAt"]) < (
                                    int(prior["splitMs"]), int(prior["elapsedMs"]), -int(prior["submittedAt"])):
                                member_best[key] = candidate
            member_best_splits = sorted(member_best.values(), key=lambda item: (
                item["context"], item["serverType"], int(item.get("splitIndex") or 999),
                item["objective"].casefold(), item["name"].casefold()))

            return 200, {"ok": True, "source": "community", "verified": False,
                         "context": context, "serverType": server_type, "kind": "group",
                         "run": completed[0] if completed else None,
                         "splits": _best_splits(splits),
                         "globalBestSplits": global_best_splits,
                         "memberBestSplits": member_best_splits,
                         "attemptCount": len(runs)}
        finally:
            c.close()
    except (ValueError, TypeError) as exc:
        return 400, {"ok": False, "error": str(exc)}
