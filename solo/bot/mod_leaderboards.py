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
        submitted_at INTEGER NOT NULL
    )""")
    c.execute("CREATE INDEX IF NOT EXISTS idx_community_mod_board ON community_mod_runs(context,game,mode,stage DESC,time_ms ASC)")
    c.execute("CREATE INDEX IF NOT EXISTS idx_community_mod_player ON community_mod_runs(uuid,submitted_at DESC)")
    c.commit()


def _entry(row, context=None, source="community"):
    # Caller selects this stable shape for community and authoritative records.
    keys = ("uuid", "name", "context", "game", "mode", "pattern", "kit", "stage", "timeMs", "submittedAt")
    item = dict(zip(keys, row[:10]))
    if context is not None:
        item["context"] = context
    item["source"] = source
    item["verified"] = source == "server"
    return item


def handle_get(path, query, db_fn, allowed_modes):
    """Return (status, payload) for the two public mod routes, or None if unmatched."""
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
                    if context not in ("MPS", "MIN", "ALL"):
                        raise ValueError("context must be MPS, MIN, or ALL")
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
                    sql = """SELECT uuid,name,context,game,mode,pattern,kit,stage,time_ms,submitted_at
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
                rows = c.execute("""SELECT uuid,name,context,game,mode,pattern,kit,stage,time_ms,submitted_at
                                     FROM community_mod_runs WHERE lower(uuid)=?
                                     ORDER BY stage DESC,time_ms ASC,submitted_at DESC LIMIT 500""",
                                 (player_uuid,)).fetchall()
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
        if stage < 1 or stage > 500:
            raise ValueError("stage must be between 1 and 500")
        if time_ms < 0 or time_ms > 24 * 60 * 60 * 1000:
            raise ValueError("invalid run time")
        now = int(time.time() * 1000)
        # A public client API cannot prove a run was genuine; throttle obvious spam.
        monotonic = time.monotonic()
        with _LOCK:
            ip_last = _IP_LAST.get(client_ip, 0.0)
            player_last = _PLAYER_LAST.get(player_uuid, 0.0)
            if monotonic - ip_last < 0.25:
                return 429, {"ok": False, "error": "rate_limited"}
            if monotonic - player_last < 5.0:
                return 429, {"ok": False, "error": "player_rate_limited"}
            _IP_LAST[client_ip] = monotonic
            _PLAYER_LAST[player_uuid] = monotonic
        c = db_fn()
        try:
            _ensure_schema(c)
            existing = c.execute("SELECT 1 FROM community_mod_runs WHERE submission_id=?", (sid,)).fetchone()
            if existing:
                return 200, {"ok": True, "accepted": True, "duplicate": True, "submissionId": sid}
            c.execute("""INSERT INTO community_mod_runs
                         (submission_id,uuid,name,context,game,mode,pattern,kit,stage,time_ms,submitted_at)
                         VALUES(?,?,?,?,?,?,?,?,?,?,?)""",
                      (sid, player_uuid, name, context, game, mode, pattern, kit, stage, time_ms, now))
            c.commit()
            return 200, {"ok": True, "accepted": True, "verified": False, "submissionId": sid}
        finally:
            c.close()
    except (ValueError, TypeError, KeyError, json.JSONDecodeError) as exc:
        return 400, {"ok": False, "error": str(exc)}
    except Exception as exc:
        print(f"[mod-api] POST failed: {exc}", flush=True)
        return 500, {"ok": False, "error": "internal_error"}
