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


def configure(*, db_fn, insert_submission, upsert_run, create_competition,
              board_rows, refresh_bot=None):
    global DB, INSERT_SUBMISSION, UPSERT_RUN, CREATE_COMPETITION, BOARD_ROWS, REFRESH_BOT
    DB = db_fn
    INSERT_SUBMISSION = insert_submission
    UPSERT_RUN = upsert_run
    CREATE_COMPETITION = create_competition
    BOARD_ROWS = board_rows
    REFRESH_BOT = refresh_bot


def token_ok(handler: BaseHTTPRequestHandler) -> bool:
    expected = os.getenv("MM_API_TOKEN", "").strip()
    if not expected:
        return False
    actual = handler.headers.get("Authorization", "")
    return actual == "Bearer " + expected


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
            if len(parts) == 4 and parts[0:3] == ["api", "v1", "challenge"]:
                platform = parts[3]
                if platform not in ("1.8", "1.21"):
                    raise ValueError("unsupported platform")
                comp = CREATE_COMPETITION(platform)
                send_json(self, 200, {
                    "ok": True,
                    "platform": platform,
                    "week": comp["week_key"],
                    "number": comp["number"],
                    "mode": comp["mode"],
                    "pattern": int(comp["pattern"]),
                    "kit": comp["kit"],
                    "start": comp["start_ts"],
                    "end": comp["end_ts"],
                    "status": comp["status"],
                })
                return

            if len(parts) == 7 and parts[:3] == ["api", "v1", "player"] and parts[4] == "pb":
                platform = parts[3]
                uuid = parts[4]  # retained below for readability if the route evolves
                raise ValueError("use /api/v1/player/{platform}/{uuid}/pb/{mode}/{pattern}")

            if len(parts) == 7 and parts[:3] == ["api", "v1", "player"] and parts[5] == "pb":
                platform, uuid, _, mode, pattern_text = parts[3], parts[4], parts[5], parts[6], ""
                raise ValueError("invalid player route")

            if path.startswith("/api/v1/leaderboard/"):
                # /api/v1/leaderboard/{platform}/{mode}/{kind}/{pattern}
                if len(parts) != 8:
                    raise ValueError("invalid leaderboard route")
                platform, mode, kind, pattern_text = parts[3], parts[4], parts[5], parts[6]
                if platform not in ("1.8", "1.21"):
                    raise ValueError("unsupported platform")
                limit = 10
                if kind == "overall":
                    rows = BOARD_ROWS("platform=? AND mode=?", [platform, mode], limit)
                elif kind == "pattern":
                    pattern = int(pattern_text)
                    rows = BOARD_ROWS("platform=? AND mode=? AND pattern=?", [platform, mode, pattern], limit)
                elif kind == "kit":
                    # Pattern + kit is deliberately left to a later UI endpoint.
                    raise ValueError("kit leaderboard endpoint not implemented")
                else:
                    raise ValueError("unsupported leaderboard kind")
                send_json(self, 200, {"ok": True, "platform": platform, "mode": mode,
                                      "kind": kind, "pattern": pattern_text,
                                      "rows": [{"name": n, "kit": k, "stage": s} for n, k, s in rows]})
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
            if length <= 0 or length > 64 * 1024:
                raise ValueError("invalid content length")
            raw = self.rfile.read(length)
            run = json.loads(raw.decode("utf-8"))
            required = ("submissionId", "platform", "mode", "pattern", "kit", "uuid", "name", "stage", "timeMs")
            missing = [key for key in required if key not in run]
            if missing:
                raise ValueError("missing fields: " + ",".join(missing))
            if str(run["platform"]) not in ("1.8", "1.21"):
                raise ValueError("unsupported platform")
            pattern = int(run["pattern"])
            if not 0 <= pattern < 3:
                raise ValueError("invalid pattern")
            stage = int(run["stage"])
            if stage < 1 or stage > 10000:
                raise ValueError("invalid stage")
            kit = str(run["kit"])
            valid_kits = ("Jumper", "Slowball", "Body Builder", "Repulsor", "Maverick")
            if kit == "Slowballer":
                kit = "Slowball"
            if kit not in valid_kits:
                raise ValueError("invalid kit")
            mode = str(run["mode"]).lower()[:64]
            uuid = str(run["uuid"]).lower()
            name = str(run["name"])[:256]
            submission_id = str(run["submissionId"])[:256]
            time_ms = max(0, int(run.get("timeMs", 0)))
            submitted_at = max(0, int(run.get("submittedAt", 0)))
            if not submitted_at:
                import time
                submitted_at = int(time.time() * 1000)

            normalized = {
                "submission_id": submission_id,
                "platform": str(run["platform"]),
                "mode": mode,
                "pattern": pattern,
                "kit": kit,
                "uuid": uuid,
                "name": name,
                "stage": stage,
                "time_ms": time_ms,
                "submitted_at": submitted_at,
            }
            inserted = INSERT_SUBMISSION(normalized)
            improved = UPSERT_RUN(normalized)
            refresh_bot(normalized["platform"])
            send_json(self, 200, {"ok": True, "accepted": True,
                                  "newSubmission": bool(inserted), "newLifetimePB": bool(improved)})
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
