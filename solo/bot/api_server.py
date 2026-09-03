"""Small HTTP API used by public Monster Maze servers.

The Discord bot remains the source of truth. This module deliberately uses only
Python's standard library so it adds no runtime dependency to the bot.
"""
from __future__ import annotations

import json
import os
import threading
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from urllib.parse import unquote

DB = None
INSERT_SUBMISSION = None
UPSERT_RUN = None
CREATE_COMPETITION = None
BOARD_ROWS = None
REFRESH_BOT = None
POST_FEED = None


def configure(*, db_fn, insert_submission, upsert_run, create_competition,
              board_rows, refresh_bot=None, post_feed=None):
    global DB, INSERT_SUBMISSION, UPSERT_RUN, CREATE_COMPETITION, BOARD_ROWS, REFRESH_BOT, POST_FEED
    DB = db_fn
    INSERT_SUBMISSION = insert_submission
    UPSERT_RUN = upsert_run
    CREATE_COMPETITION = create_competition
    BOARD_ROWS = board_rows
    REFRESH_BOT = refresh_bot
    POST_FEED = post_feed


def token_ok(handler: BaseHTTPRequestHandler) -> bool:
    expected = os.getenv("MM_API_TOKEN", "").strip()
    if not expected:
        return False
    return handler.headers.get("Authorization", "") == "Bearer " + expected


def send_json(handler, status: int, payload: dict):
    body = json.dumps(payload, separators=(",", ":"), ensure_ascii=False).encode("utf-8")
    handler.send_response(status)
    handler.send_header("Content-Type", "application/json; charset=utf-8")
    handler.send_header("Content-Length", str(len(body)))
    handler.send_header("Cache-Control", "no-store")
    handler.end_headers()
    handler.wfile.write(body)


def refresh_bot(platform: str):
    if REFRESH_BOT:
        try:
            REFRESH_BOT(platform)
        except Exception as exc:
            print(f"[api] leaderboard refresh failed: {exc}", flush=True)


def post_feed(run: dict):
    if POST_FEED:
        try:
            POST_FEED(run)
        except Exception as exc:
            print(f"[api] feed post failed: {exc}", flush=True)


def background_updates(platform: str, run: dict, should_feed: bool):
    """Perform Discord work after the HTTP client has received its acknowledgement."""
    def work():
        if should_feed:
            post_feed(run)
        refresh_bot(platform)
    threading.Thread(target=work, name="MonsterMazeAPIUpdate", daemon=True).start()


class Handler(BaseHTTPRequestHandler):
    server_version = "MonsterMazeAPI/1.0"

    def log_message(self, fmt, *args):
        print(f"[api] {self.address_string()} - {fmt % args}", flush=True)

    def do_GET(self):
        path = unquote(self.path.split("?", 1)[0]).rstrip("/")
        if path == "/health":
            send_json(self, 200, {"ok": True, "service": "monstermaze-api"})
            return

        if not token_ok(self):
            send_json(self, 401, {"ok": False, "error": "unauthorized"})
            return

        parts = path.strip("/").split("/")
        try:
            if len(parts) == 4 and parts[:3] == ["api", "v1", "challenge"]:
                platform = parts[3]
                if platform not in ("1.8", "1.21"):
                    raise ValueError("unsupported platform")
                comp = CREATE_COMPETITION(platform)
                send_json(self, 200, {
                    "ok": True, "platform": platform, "week": comp["week_key"], "number": comp["number"],
                    "mode": comp["mode"], "pattern": int(comp["pattern"]), "kit": comp["kit"],
                    "start": comp["start_ts"], "end": comp["end_ts"], "status": comp["status"],
                })
                return

            if len(parts) in (6, 7) and parts[:3] == ["api", "v1", "leaderboard"]:
                platform, mode = parts[3], parts[4]
                if platform not in ("1.8", "1.21"):
                    raise ValueError("unsupported platform")
                if len(parts) == 6 and parts[5] == "overall":
                    rows = BOARD_ROWS("platform=? AND mode=?", [platform, mode], 10)
                    kind, pattern = "overall", None
                elif len(parts) == 7 and parts[5] == "pattern":
                    pattern = int(parts[6])
                    if not 0 <= pattern < 3: raise ValueError("invalid pattern")
                    rows = BOARD_ROWS("platform=? AND mode=? AND pattern=?", [platform, mode, pattern], 10)
                    kind = "pattern"
                elif len(parts) == 7 and parts[5] == "kit":
                    kit = str(parts[6])
                    if kit.lower() == "slowballer": kit = "Slowball"
                    if kit not in ("Jumper", "Slowball", "Body Builder", "Repulsor", "Maverick"):
                        raise ValueError("invalid kit")
                    rows = BOARD_ROWS("platform=? AND mode=? AND kit=?", [platform, mode, kit], 10)
                    kind, pattern = "kit", None
                else:
                    raise ValueError("unsupported leaderboard route")
                send_json(self, 200, {
                    "ok": True, "platform": platform, "mode": mode, "kind": kind, "pattern": pattern,
                    "rows": [{"name": n, "kit": k, "stage": s} for n, k, s in rows],
                })
                return

            if len(parts) == 6 and parts[:3] == ["api", "v1", "pb"]:
                platform, mode, uuid = parts[3], parts[4], parts[5]
                if platform not in ("1.8", "1.21"):
                    raise ValueError("unsupported platform")
                if not uuid or len(uuid) > 64:
                    raise ValueError("invalid uuid")
                c = DB()
                rows = c.execute(
                    "SELECT pattern,kit,stage,time_ms FROM runs WHERE platform=? AND mode=? AND uuid=? "
                    "ORDER BY pattern ASC, stage DESC, time_ms ASC, kit ASC",
                    (platform, mode.lower(), uuid.lower()),
                ).fetchall()
                c.close()
                send_json(self, 200, {
                    "ok": True, "platform": platform, "mode": mode.lower(), "uuid": uuid.lower(),
                    "rows": [{"pattern": int(p), "kit": k, "stage": int(s), "timeMs": int(t)} for p, k, s, t in rows],
                })
                return
        except (ValueError, TypeError, KeyError, IndexError) as exc:
            send_json(self, 400, {"ok": False, "error": str(exc)})
            return
        except Exception as exc:
            print(f"[api] GET failed: {exc}", flush=True)
            send_json(self, 500, {"ok": False, "error": "internal_error"})
            return

        send_json(self, 404, {"ok": False, "error": "not_found"})

    def do_POST(self):
        path = unquote(self.path.split("?", 1)[0]).rstrip("/")
        if path != "/api/v1/runs":
            send_json(self, 404, {"ok": False, "error": "not_found"})
            return
        if not token_ok(self):
            send_json(self, 401, {"ok": False, "error": "unauthorized"})
            return
        try:
            length = int(self.headers.get("Content-Length", "0"))
            if length <= 0 or length > 64 * 1024: raise ValueError("invalid content length")
            raw = self.rfile.read(length)
            run = json.loads(raw.decode("utf-8"))
            required = ("submissionId", "platform", "mode", "pattern", "kit", "uuid", "name", "stage", "timeMs")
            missing = [key for key in required if key not in run]
            if missing: raise ValueError("missing fields: " + ",".join(missing))
            platform = str(run["platform"])
            if platform not in ("1.8", "1.21"): raise ValueError("unsupported platform")
            pattern = int(run["pattern"])
            if not 0 <= pattern < 3: raise ValueError("invalid pattern")
            stage = int(run["stage"])
            if stage < 1 or stage > 10000: raise ValueError("invalid stage")
            kit = str(run["kit"])
            if kit.lower() == "slowballer": kit = "Slowball"
            if kit not in ("Jumper", "Slowball", "Body Builder", "Repulsor", "Maverick"):
                raise ValueError("invalid kit")
            normalized = {
                "submission_id": str(run["submissionId"])[:256], "platform": platform,
                "plugin": str(run.get("plugin", "1.0.0"))[:64], "mode": str(run["mode"]).lower()[:64],
                "pattern": pattern, "kit": kit, "uuid": str(run["uuid"]).lower()[:64],
                "name": str(run["name"])[:256], "stage": stage,
                "time_ms": max(0, int(run.get("timeMs", 0))), "config_hash": str(run.get("configHash", ""))[:128],
                "submitted_at": max(0, int(run.get("submittedAt", 0))),
            }
            if not normalized["submitted_at"]:
                import time
                normalized["submitted_at"] = int(time.time() * 1000)
            inserted = INSERT_SUBMISSION(normalized)
            improved = UPSERT_RUN(normalized)
            send_json(self, 200, {"ok": True, "accepted": True,
                                  "newSubmission": bool(inserted), "newLifetimePB": bool(improved)})
            background_updates(platform, normalized, bool(inserted))
        except (ValueError, TypeError, json.JSONDecodeError) as exc:
            send_json(self, 400, {"ok": False, "error": str(exc)})
        except Exception as exc:
            print(f"[api] POST failed: {exc}", flush=True)
            send_json(self, 500, {"ok": False, "error": "internal_error"})


def start_server(*, host="0.0.0.0", port=8090):
    server = ThreadingHTTPServer((host, int(port)), Handler)
    thread = threading.Thread(target=server.serve_forever, name="MonsterMazeAPI", daemon=True)
    thread.start()
    print(f"[api] listening on {host}:{port}", flush=True)
    return server
