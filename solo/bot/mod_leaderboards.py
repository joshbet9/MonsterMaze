"""Public, rate-limited JNP Mod community leaderboards.

These rows are client-reported and must never be mixed into the server-authoritative
Monster Maze runs/submissions tables used for competition and MMR.
"""
from __future__ import annotations

import json
import re
import threading
import time
import uuid as uuidlib

_LOCK = threading.Lock()
_IP_LAST = {}
_PLAYER_LAST = {}
_NAME_RE = re.compile(r"^[A-Za-z0-9_]{1,16}$")
_UUID_RE = re.compile(r"^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$")
_ALLOWED_COMMUNITY_MODES = {"unknown", "original", "modern", "speed", "classic"}
_ALLOWED_KITS = {"Unknown", "Jumper", "Slowball", "Body Builder", "Repulsor", "Maverick"}


def _value(query, name, default=""):
    values = query.get(name, [])
    return str(values[0]).strip() if values else default


def _ensure_schema(c):
    c.execute("""CREATE TABLE IF NOT EXISTS community_mod_runs (
        submission_id TEXT PRIMARY KEY,
        uuid TEXT NOT NULL,
        name TEXT NOT NULL,
        context TEXT NOT NULL,
        game TEXT NOT NULL,
        mode TEXT NOT NULL DEFAULT 'unknown',
        pattern INTEGER NOT NULL DEFAULT -1,
        kit TEXT NOT NULL DEFAULT 'Unknown',
        stage INTEGER NOT NULL,
        time_ms INTEGER NOT NULL DEFAULT 0,
        submitted_at INTEGER NOT NULL,
        started_at INTEGER NOT NULL DEFAULT 0,
        kit_confirmed INTEGER NOT NULL DEFAULT 0
    )""")
    # Backward-compatible migration for Oracle databases created by older releases.
    columns = {row[1] for row in c.execute("PRAGMA table_info(community_mod_runs)").fetchall()}
    if "started_at" not in columns:
        c.execute("ALTER TABLE community_mod_runs ADD COLUMN started_at INTEGER NOT NULL DEFAULT 0")
    if "kit_confirmed" not in columns:
        c.execute("ALTER TABLE community_mod_runs ADD COLUMN kit_confirmed INTEGER NOT NULL DEFAULT 0")
    c.execute("CREATE INDEX IF NOT EXISTS idx_community_mod_board ON community_mod_runs(context,game,mode,stage DESC,time_ms ASC)")
    c.execute("CREATE INDEX IF NOT EXISTS idx_community_mod_player ON community_mod_runs(uuid,submitted_at DESC)")
    c.execute("CREATE INDEX IF NOT EXISTS idx_community_mod_start ON community_mod_runs(uuid,context,started_at)")
    c.commit()


def _entry(row, context=None, source="community"):
    # Caller selects this stable shape for community and authoritative records.
    keys = ("uuid", "name", "context", "game", "mode", "pattern", "kit", "stage", "timeMs", "submittedAt", "startedAt")
    item = dict(zip(keys, row[:11]))
    if context is not None:
        item["context"] = context
    item["source"] = source
    item["verified"] = source == "server"
    return item


def handle_game_info(query, db_fn):
    """Return every observed participant result for a nearby, timestamp-anchored MM round."""
    try:
        player_uuid = _value(query, "uuid").lower()
        if not _UUID_RE.match(player_uuid):
            raise ValueError("a valid anchor player UUID is required")
        try:
            started_at = int(_value(query, "startedAt", "0"))
        except (TypeError, ValueError, OverflowError):
            raise ValueError("startedAt must be Unix epoch milliseconds")
        if started_at <= 0 or started_at > 9_999_999_999_999:
            raise ValueError("startedAt must be a positive Unix epoch millisecond timestamp")
        context = _value(query, "context", "").upper()
        if context in ("ARCADE", "PUBLIC"):
            context = "MIN"
        if context not in ("MPS", "MIN"):
            raise ValueError("context must be MPS or MIN/ARCADE")
        c = db_fn()
        try:
            _ensure_schema(c)
            # Require the clicked record itself to exist before showing a round profile.
            anchor = c.execute(
                "SELECT 1 FROM community_mod_runs WHERE lower(uuid)=? AND context=? "
                "AND started_at BETWEEN ? AND ? LIMIT 1",
                (player_uuid, context, started_at - 30_000, started_at + 30_000),
            ).fetchone()
            if not anchor:
                return 200, {"ok": True, "source": "community", "startedAt": started_at, "rows": []}
            rows = c.execute(
                """SELECT uuid,name,context,game,mode,pattern,kit,stage,time_ms,submitted_at,started_at
                   FROM community_mod_runs
                   WHERE game='Monster Maze' AND context=? AND started_at BETWEEN ? AND ?
                   ORDER BY started_at ASC, stage DESC, time_ms ASC, name COLLATE NOCASE ASC
                   LIMIT 100""",
                (context, started_at - 30_000, started_at + 30_000),
            ).fetchall()
            return 200, {
                "ok": True, "source": "community", "context": context,
                "startedAt": started_at, "windowMs": 30_000,
                "verified": False, "rows": [_entry(row) for row in rows],
            }
        finally:
            c.close()
    except ValueError as exc:
        return 400, {"ok": False, "error": str(exc)}
    except Exception as exc:
        print(f"[mod-api] game-info GET failed: {exc}", flush=True)
        return 500, {"ok": False, "error": "internal_error"}


def handle_history(query, db_fn):
    """Return paginated, chronological community game history from VM-stored records.

    Timestamped participant submissions are grouped into round summaries using the
    same +/-30-second window as handle_game_info. Legacy submissions without a game
    start timestamp are retained as individual, clearly marked entries; they cannot
    be safely combined into a round or opened as participant Game Info.
    """
    try:
        context = _value(query, "context", "ALL").upper()
        if context in ("ARCADE", "PUBLIC"):
            context = "MIN"
        if context not in ("ALL", "MIN", "MPS"):
            raise ValueError("context must be ALL, MPS, or MIN/ARCADE")

        try:
            limit = int(_value(query, "limit", "100"))
            offset = int(_value(query, "offset", "0"))
        except (TypeError, ValueError, OverflowError):
            raise ValueError("limit and offset must be integers")
        if limit < 1 or limit > 100:
            raise ValueError("limit must be between 1 and 100")
        if offset < 0 or offset > 1_000_000:
            raise ValueError("offset must be between 0 and 1000000")

        c = db_fn()
        try:
            _ensure_schema(c)
            clauses = ["game='Monster Maze'"]
            args = []
            if context != "ALL":
                clauses.append("context=?")
                args.append(context)
            where = " AND ".join(clauses)
            columns = ("uuid,name,context,game,mode,pattern,kit,stage,time_ms,"
                       "submitted_at,started_at,submission_id")
            timed = c.execute(
                "SELECT " + columns + " FROM community_mod_runs WHERE " + where
                + " AND started_at>0 ORDER BY started_at ASC,context ASC,"
                  "submitted_at ASC,stage DESC,time_ms ASC,name COLLATE NOCASE ASC",
                tuple(args),
            ).fetchall()
            legacy = c.execute(
                "SELECT " + columns + " FROM community_mod_runs WHERE " + where
                + " AND started_at<=0 ORDER BY submitted_at ASC,context ASC,"
                  "stage DESC,time_ms ASC,name COLLATE NOCASE ASC",
                tuple(args),
            ).fetchall()

            history = []
            latest_by_context = {}
            for raw in timed:
                item = _entry(raw, source="community")
                started_at = int(item.get("startedAt") or 0)
                row_context = str(item.get("context") or "").upper()
                group = latest_by_context.get(row_context)
                if group is None or started_at - group["startedAt"] > 30_000:
                    group = {
                        "uuid": item["uuid"],
                        "name": item["name"],
                        "context": row_context,
                        "game": "Monster Maze",
                        "mode": item["mode"],
                        "pattern": item["pattern"],
                        "kit": item["kit"],
                        "stage": item["stage"],
                        "maxStage": item["stage"],
                        "timeMs": item["timeMs"],
                        "submittedAt": item["submittedAt"],
                        "startedAt": started_at,
                        "historyTimestamp": started_at,
                        "timestampSource": "startedAt",
                        "gameInfoAvailable": True,
                        "legacyUnknownStart": False,
                        "participantCount": 0,
                        "recordCount": 0,
                        "source": "community",
                        "verified": False,
                        "_players": set(),
                    }
                    history.append(group)
                    latest_by_context[row_context] = group
                player_key = str(item.get("uuid") or "").lower()
                if player_key and player_key not in group["_players"]:
                    group["_players"].add(player_key)
                    group["participantCount"] += 1
                group["recordCount"] += 1
                group["submittedAt"] = max(int(group["submittedAt"]), int(item.get("submittedAt") or 0))
                # Use the strongest stage for the visible round summary while keeping
                # the UUID/start-time anchor from a real row for the Game Info request.
                try:
                    stage = int(item.get("stage") or 0)
                except (TypeError, ValueError, OverflowError):
                    stage = 0
                if stage > int(group["maxStage"]):
                    group["maxStage"] = stage
                    group["stage"] = stage
                    group["name"] = item["name"]
                    group["kit"] = item["kit"]
                    group["pattern"] = item["pattern"]
                    group["mode"] = item["mode"]
                    group["timeMs"] = item["timeMs"]

            for raw in legacy:
                item = _entry(raw, source="community")
                submitted_at = int(item.get("submittedAt") or 0)
                history.append({
                    "uuid": item["uuid"],
                    "name": item["name"],
                    "context": str(item.get("context") or "").upper(),
                    "game": "Monster Maze",
                    "mode": item["mode"],
                    "pattern": item["pattern"],
                    "kit": item["kit"],
                    "stage": item["stage"],
                    "maxStage": item["stage"],
                    "timeMs": item["timeMs"],
                    "submittedAt": submitted_at,
                    "startedAt": 0,
                    "historyTimestamp": submitted_at,
                    "timestampSource": "submittedAt",
                    "gameInfoAvailable": False,
                    "legacyUnknownStart": True,
                    "participantCount": 1,
                    "recordCount": 1,
                    "source": "community",
                    "verified": False,
                })

            # Oldest first is chronological. Rows without a start time appear at their
            # submission time and are explicitly marked as legacy/unknown-start entries.
            history.sort(key=lambda row: (
                int(row.get("historyTimestamp") or 0),
                str(row.get("context") or ""),
                str(row.get("name") or "").lower(),
            ))
            total = len(history)
            page = history[offset:offset + limit]
            for item in page:
                item.pop("_players", None)
            return 200, {
                "ok": True,
                "source": "community",
                "verified": False,
                "context": context,
                "chronological": "oldest-first",
                "limit": limit,
                "offset": offset,
                "total": total,
                "hasMore": offset + len(page) < total,
                "rows": page,
            }
        finally:
            c.close()
    except ValueError as exc:
        return 400, {"ok": False, "error": str(exc)}
    except Exception as exc:
        print(f"[mod-api] game-history GET failed: {exc}", flush=True)
        return 500, {"ok": False, "error": "internal_error"}


def handle_get(path, query, db_fn, allowed_modes):
    """Return (status, payload) for the public mod routes, or None if unmatched."""
    if path == "/api/v1/mod/history":
        return handle_history(query, db_fn)
    if path == "/api/v1/mod/game":
        return handle_game_info(query, db_fn)
    if path not in ("/api/v1/mod/leaderboard", "/api/v1/mod/player"):
        return None
    try:
        source = _value(query, "source", "community").lower()
        if source not in ("community", "server"):
            raise ValueError("source must be community or server")
        c = db_fn()
        try:
            if source == "community":
                _ensure_schema(c)
                if path.endswith("/leaderboard"):
                    context = _value(query, "context", "MPS").upper()
                    if context in ("ARCADE", "PUBLIC"):
                        context = "MIN"
                    if context not in ("MPS", "MIN", "ALL"):
                        raise ValueError("context must be MPS, MIN/ARCADE, or ALL")
                    mode = _value(query, "mode", "all").lower()
                    if mode != "all" and mode not in _ALLOWED_COMMUNITY_MODES:
                        raise ValueError("unsupported community mode")
                    kind = _value(query, "kind", "overall").lower()
                    filt = _value(query, "filter", "all")
                    if kind not in ("overall", "pattern", "kit", "combination"):
                        raise ValueError("kind must be overall, pattern, kit, or combination")
                    where = ["game='Monster Maze'"]
                    args = []
                    if context != "ALL":
                        where.append("context=?")
                        args.append(context)
                    if mode != "all":
                        where.append("mode=?")
                        args.append(mode)
                    if kind in ("pattern", "combination"):
                        pattern_raw = _value(query, "pattern", filt) if kind == "combination" else filt
                        try:
                            pattern = int(pattern_raw)
                        except ValueError:
                            raise ValueError("pattern filter must be 0, 1, or 2")
                        if pattern not in (0, 1, 2):
                            raise ValueError("pattern filter must be 0, 1, or 2")
                        where.append("pattern=?")
                        args.append(pattern)
                    if kind in ("kit", "combination"):
                        kit_raw = _value(query, "kit", filt) if kind == "combination" else filt
                        kit = "Slowball" if kit_raw.lower() == "slowballer" else kit_raw
                        if kit not in _ALLOWED_KITS - {"Unknown"}:
                            raise ValueError("invalid kit filter")
                        where.append("kit=?")
                        args.append(kit)
                    sql = """SELECT uuid,name,context,game,mode,pattern,kit,stage,time_ms,submitted_at,started_at
                             FROM community_mod_runs WHERE %s
                             ORDER BY stage DESC,time_ms ASC,submitted_at ASC,name COLLATE NOCASE ASC
                             LIMIT 100""" % " AND ".join(where)
                    rows = c.execute(sql, tuple(args)).fetchall()
                    return 200, {"ok": True, "source": "community", "verified": False,
                                 "context": context, "mode": mode, "kind": kind,
                                 "rows": [_entry(r) for r in rows]}
                player_uuid = _value(query, "uuid").lower()
                if not _UUID_RE.match(player_uuid):
                    raise ValueError("a valid player UUID is required")
                where = ["lower(uuid)=?"]
                args = [player_uuid]
                context = _value(query, "context", "ALL").upper()
                if context in ("ARCADE", "PUBLIC"):
                    context = "MIN"
                if context != "ALL":
                    if context not in ("MPS", "MIN"):
                        raise ValueError("context must be MPS, MIN/ARCADE, or ALL")
                    where.append("context=?"); args.append(context)
                mode = _value(query, "mode", "all").lower()
                if mode != "all":
                    if mode not in _ALLOWED_COMMUNITY_MODES:
                        raise ValueError("unsupported community mode")
                    where.append("mode=?"); args.append(mode)
                pattern_raw = _value(query, "pattern", "all")
                if pattern_raw != "all":
                    try: pattern = int(pattern_raw)
                    except ValueError: raise ValueError("pattern filter must be 0, 1, or 2")
                    if pattern not in (0, 1, 2): raise ValueError("pattern filter must be 0, 1, or 2")
                    where.append("pattern=?"); args.append(pattern)
                kit_raw = _value(query, "kit", "all")
                if kit_raw.lower() != "all":
                    kit = "Slowball" if kit_raw.lower() in ("slowball", "slowballer") else kit_raw
                    if kit not in _ALLOWED_KITS - {"Unknown"}:
                        raise ValueError("invalid kit filter")
                    where.append("kit=?"); args.append(kit)
                rows = c.execute("""SELECT uuid,name,context,game,mode,pattern,kit,stage,time_ms,submitted_at,started_at
                                     FROM community_mod_runs WHERE %s
                                     ORDER BY stage DESC,time_ms ASC,submitted_at DESC LIMIT 500""" % " AND ".join(where),
                                 tuple(args)).fetchall()
                if not rows:
                    return 200, {"ok": True, "source": "community", "player": None, "rows": []}
                # Profile rows are personal bests per context/mode/pattern/kit, not every attempt.
                best = {}
                for row in rows:
                    key = (row[2], row[4], row[5], row[6])
                    if key not in best or (int(row[7]), -int(row[8])) > (int(best[key][7]), -int(best[key][8])):
                        best[key] = row
                profile_rows = sorted(best.values(), key=lambda r: (-int(r[7]), int(r[8]), r[2], r[4], r[5], r[6]))
                return 200, {"ok": True, "source": "community",
                             "player": {"uuid": player_uuid, "name": rows[0][1]},
                             "rows": [_entry(r) for r in profile_rows[:100]],
                             "attemptCount": len(rows)}
            else:
                if path.endswith("/leaderboard"):
                    platform = _value(query, "platform", "1.8")
                    mode = _value(query, "mode", "original").lower()
                    if platform not in allowed_modes or mode not in allowed_modes[platform]:
                        raise ValueError("unsupported platform/mode")
                    kind = _value(query, "kind", "overall").lower()
                    filt = _value(query, "filter", "all")
                    if kind not in ("overall", "pattern", "kit", "combination"):
                        raise ValueError("kind must be overall, pattern, kit, or combination")
                    where = ["platform=?", "lower(mode)=?"]
                    args = [platform, mode]
                    if kind in ("pattern", "combination"):
                        pattern_raw = _value(query, "pattern", filt) if kind == "combination" else filt
                        try: pattern = int(pattern_raw)
                        except ValueError: raise ValueError("pattern filter must be 0, 1, or 2")
                        if pattern not in (0, 1, 2): raise ValueError("pattern filter must be 0, 1, or 2")
                        where.append("pattern=?"); args.append(pattern)
                    if kind in ("kit", "combination"):
                        kit_raw = _value(query, "kit", filt) if kind == "combination" else filt
                        kit = "Slowball" if kit_raw.lower() == "slowballer" else kit_raw
                        if kit not in ("Jumper", "Slowball", "Body Builder", "Repulsor", "Maverick"):
                            raise ValueError("invalid kit filter")
                        where.append("kit=?"); args.append(kit)
                    sql = """WITH ranked AS (
                               SELECT uuid,name,platform,mode,pattern,kit,stage,time_ms,
                                      ROW_NUMBER() OVER(PARTITION BY uuid ORDER BY stage DESC,time_ms ASC,kit ASC,name ASC) rn
                               FROM runs WHERE %s
                             )
                             SELECT uuid,name,platform,mode,pattern,kit,stage,time_ms
                             FROM ranked WHERE rn=1 ORDER BY stage DESC,time_ms ASC,name COLLATE NOCASE ASC LIMIT 100""" % " AND ".join(where)
                    rows = c.execute(sql, tuple(args)).fetchall()
                    result = []
                    for r in rows:
                        result.append({"uuid": r[0], "name": r[1], "platform": r[2], "mode": r[3],
                                       "pattern": int(r[4]), "kit": r[5], "stage": int(r[6]),
                                       "timeMs": int(r[7]), "source": "server", "verified": True})
                    return 200, {"ok": True, "source": "server", "verified": True,
                                 "platform": platform, "mode": mode, "kind": kind, "rows": result}
                player_uuid = _value(query, "uuid").lower()
                if not _UUID_RE.match(player_uuid):
                    raise ValueError("a valid player UUID is required")
                where = ["lower(uuid)=?"]
                args = [player_uuid]
                platform = _value(query, "platform", "")
                mode = _value(query, "mode", "all").lower()
                pattern_raw = _value(query, "pattern", "all")
                kit_raw = _value(query, "kit", "all")
                if platform:
                    if platform not in allowed_modes: raise ValueError("unsupported platform")
                    where.append("platform=?"); args.append(platform)
                if mode != "all":
                    if platform and mode not in allowed_modes[platform]: raise ValueError("unsupported platform/mode")
                    where.append("lower(mode)=?"); args.append(mode)
                if pattern_raw != "all":
                    try: pattern = int(pattern_raw)
                    except ValueError: raise ValueError("pattern filter must be 0, 1, or 2")
                    if pattern not in (0, 1, 2): raise ValueError("pattern filter must be 0, 1, or 2")
                    where.append("pattern=?"); args.append(pattern)
                if kit_raw.lower() != "all":
                    kit = "Slowball" if kit_raw.lower() in ("slowball", "slowballer") else kit_raw
                    if kit not in ("Jumper", "Slowball", "Body Builder", "Repulsor", "Maverick"):
                        raise ValueError("invalid kit filter")
                    where.append("kit=?"); args.append(kit)
                rows = c.execute("""SELECT uuid,name,platform,mode,pattern,kit,stage,time_ms
                                    FROM runs WHERE %s
                                    ORDER BY stage DESC,time_ms ASC,platform,mode,pattern,kit LIMIT 100""" % " AND ".join(where),
                                 tuple(args)).fetchall()
                result = [{"uuid": r[0], "name": r[1], "platform": r[2], "mode": r[3],
                           "pattern": int(r[4]), "kit": r[5], "stage": int(r[6]),
                           "timeMs": int(r[7]), "source": "server", "verified": True} for r in rows]
                profile = {"uuid": player_uuid, "name": result[0]["name"]} if result else None
                return 200, {"ok": True, "source": "server", "verified": True, "player": profile, "rows": result}
        finally:
            c.close()
    except ValueError as exc:
        return 400, {"ok": False, "error": str(exc)}
    except Exception as exc:
        print(f"[mod-api] GET failed: {exc}", flush=True)
        return 500, {"ok": False, "error": "internal_error"}


def handle_submit(body, client_ip, db_fn):
    """Accept opt-in client-reported runs; deliberately separate from server scores."""
    try:
        if len(body) > 8192:
            return 413, {"ok": False, "error": "payload_too_large"}
        payload = json.loads(body.decode("utf-8"))
        if not isinstance(payload, dict):
            raise ValueError("JSON object required")
        required = ("submissionId", "uuid", "name", "context", "game", "mode", "pattern", "kit", "stage", "timeMs")
        missing = [name for name in required if name not in payload]
        if missing:
            raise ValueError("missing fields: " + ",".join(missing))
        try:
            sid = str(uuidlib.UUID(str(payload["submissionId"])))
            player_uuid = str(uuidlib.UUID(str(payload["uuid"]))).lower()
        except (ValueError, AttributeError, TypeError):
            raise ValueError("submissionId and uuid must be UUIDs")
        name = str(payload["name"]).strip()
        if not _NAME_RE.match(name):
            raise ValueError("invalid Minecraft username")
        context = str(payload["context"]).upper()
        if context in ("ARCADE", "PUBLIC"):
            context = "MIN"
        if context not in ("MPS", "MIN"):
            raise ValueError("context must be MPS/MIN (ARCADE is accepted as a MIN alias)")
        game = str(payload["game"]).strip()
        if game != "Monster Maze":
            raise ValueError("unsupported game")
        mode = str(payload["mode"]).lower()
        if mode not in _ALLOWED_COMMUNITY_MODES:
            mode = "unknown"
        try:
            pattern = int(payload["pattern"])
            stage = int(payload["stage"])
            time_ms = int(payload["timeMs"])
        except (TypeError, ValueError):
            raise ValueError("pattern, stage and timeMs must be integers")
        if pattern not in (-1, 0, 1, 2):
            pattern = -1
        kit = str(payload["kit"]).strip()
        if kit.lower() == "slowballer":
            kit = "Slowball"
        if kit not in _ALLOWED_KITS:
            kit = "Unknown"
        raw_kit_confirmed = payload.get("kitConfirmed", False)
        kit_confirmed = raw_kit_confirmed is True or (type(raw_kit_confirmed) is int and raw_kit_confirmed == 1)
        kit_confirmed = kit_confirmed and kit != "Unknown"
        if stage < 1 or stage > 500:
            raise ValueError("stage must be between 1 and 500")
        if time_ms < 0 or time_ms > 24 * 60 * 60 * 1000:
            raise ValueError("invalid run time")
        try:
            started_at = int(payload.get("startedAt", 0))
        except (TypeError, ValueError, OverflowError):
            raise ValueError("startedAt must be Unix epoch milliseconds")
        if isinstance(payload.get("startedAt", 0), bool) or not 0 <= started_at <= 9_999_999_999_999:
            raise ValueError("startedAt must be between 0 and 9999999999999 milliseconds (0 means not supplied)")
        now = int(time.time() * 1000)
        # Check idempotency before rate limiting: a retry after a lost HTTP response
        # must receive an ACK rather than being stuck behind the per-player throttle.
        # Keep duplicate lookup + insert under the process lock so concurrent copies of
        # the same submission ID cannot race past the uniqueness check.
        c = db_fn()
        try:
            _ensure_schema(c)
            with _LOCK:
                existing = c.execute("SELECT 1 FROM community_mod_runs WHERE submission_id=?", (sid,)).fetchone()
                if existing:
                    return 200, {"ok": True, "accepted": True, "duplicate": True, "submissionId": sid}

                # Independent clients may restart a tracker after a padskip or stale sidebar,
                # so one real attempt can have start times over 30 seconds apart. Match the
                # usual close starts OR overlapping player-run intervals, never unanchored rows.
                if started_at > 0:
                    earliest_candidate_start = max(1, started_at - 24 * 60 * 60 * 1000)
                    latest_candidate_start = started_at + max(time_ms, 30_000)
                    candidates = c.execute(
                        """SELECT submission_id, kit, pattern, started_at, time_ms, stage, kit_confirmed, context
                           FROM community_mod_runs
                           WHERE lower(uuid)=? AND started_at BETWEEN ? AND ?
                           ORDER BY ABS(started_at - ?) ASC""",
                        (player_uuid, earliest_candidate_start, latest_candidate_start, started_at),
                    ).fetchall()
                    same_round = None
                    for candidate in candidates:
                        old_start = int(candidate[3] or 0)
                        old_duration = int(candidate[4] or 0)
                        start_delta = abs(old_start - started_at)
                        overlaps = False
                        if old_start > 0 and old_duration > 0 and time_ms > 0:
                            old_end = old_start + old_duration
                            new_end = started_at + time_ms
                            overlap = min(old_end, new_end) - max(old_start, started_at)
                            end_delta = abs(old_end - new_end)
                            shorter_duration = min(old_duration, time_ms)
                            overlaps = overlap > 0 and (
                                end_delta <= 60_000
                                or (shorter_duration > 0 and overlap * 10 >= shorter_duration * 7)
                            )
                        if start_delta <= 30_000 or overlaps:
                            same_round = candidate
                            break
                    if same_round:
                        # A confirmed kit can correct a known-but-unconfirmed observation.
                        # Never let an unconfirmed duplicate overwrite stronger kit metadata.
                        updates = {}
                        existing_kit = str(same_round[1] or "Unknown")
                        existing_confirmed = bool(same_round[6])
                        existing_stage = int(same_round[5] or 0)
                        if stage > existing_stage:
                            # Higher stage is the stronger score snapshot; update its paired
                            # elapsed time while retaining the canonical submission ID/start.
                            updates["stage"] = stage
                            updates["time_ms"] = time_ms
                        higher_stage_snapshot = stage > existing_stage
                        if kit != "Unknown" and (
                                existing_kit == "Unknown"
                                or (kit_confirmed and not existing_confirmed)
                                or (kit_confirmed and kit != existing_kit and higher_stage_snapshot)):
                            # When a padskip tracker attached early with a stale kit but a
                            # later full-run snapshot supplies a stronger stage, keep the kit
                            # from the stronger score snapshot alongside that stage.
                            updates["kit"] = kit
                            updates["kit_confirmed"] = 1 if kit_confirmed else 0
                        elif kit_confirmed and existing_kit == kit and not existing_confirmed:
                            updates["kit_confirmed"] = 1
                        if int(same_round[2]) == -1 and pattern in (0, 1, 2):
                            updates["pattern"] = pattern
                        existing_context = str(same_round[7] or "MPS").upper()
                        # Same UUID + overlapping run timing is stronger identity evidence
                        # than a client context that may have been resolved late after padskip.
                        # Prefer MIN when either observer saw the Arcade node.
                        if context == "MIN" and existing_context != "MIN":
                            updates["context"] = "MIN"
                        if updates:
                            assignments = ", ".join(field + "=?" for field in updates)
                            c.execute("UPDATE community_mod_runs SET " + assignments + " WHERE submission_id=?",
                                      list(updates.values()) + [same_round[0]])
                            c.commit()
                        return 200, {
                            "ok": True, "accepted": True, "duplicate": True, "deduplicated": True,
                            "submissionId": sid, "matchedSubmissionId": same_round[0],
                        }

                # Public client reports cannot be verified; throttle obvious spam.
                monotonic = time.monotonic()
                ip_last = _IP_LAST.get(client_ip, 0.0)
                player_last = _PLAYER_LAST.get(player_uuid, 0.0)
                if monotonic - ip_last < 0.25:
                    return 429, {"ok": False, "error": "rate_limited"}
                if monotonic - player_last < 5.0:
                    return 429, {"ok": False, "error": "player_rate_limited"}

                c.execute("""INSERT INTO community_mod_runs
                             (submission_id,uuid,name,context,game,mode,pattern,kit,stage,time_ms,submitted_at,started_at,kit_confirmed)
                             VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?)""",
                          (sid, player_uuid, name, context, game, mode, pattern, kit, stage, time_ms, now,
                           started_at, 1 if kit_confirmed else 0))
                c.commit()
                _IP_LAST[client_ip] = monotonic
                _PLAYER_LAST[player_uuid] = monotonic
                return 200, {"ok": True, "accepted": True, "verified": False, "submissionId": sid}
        finally:
            c.close()
    except (ValueError, TypeError, KeyError, json.JSONDecodeError) as exc:
        return 400, {"ok": False, "error": str(exc)}
    except Exception as exc:
        print(f"[mod-api] POST failed: {exc}", flush=True)
        return 500, {"ok": False, "error": "internal_error"}
