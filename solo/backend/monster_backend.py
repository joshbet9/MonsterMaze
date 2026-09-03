#!/usr/bin/env python3
"""
Monster Maze SOLO - leaderboard backend.

A tiny, dependency-free service (Python stdlib only) that:
  1. Receives PB run records from the solo submitter (POST /ingest).
  2. Stores them in SQLite (best per mode/pattern/kit/player).
  3. Routes each PB to its per-mode Discord leaderboard channel and keeps a
     single pinned/edited leaderboard embed current per mode.

Run it (on your always-on machine / VPS):
    python monster_backend.py [port]      (default port 8123)

Config file: channels.json  (same folder)
    {
      "modern":   "https://discord.com/api/webhooks/...</id>/<token>",
      "lagless":  "...",
      "original": "..."
    }

The submitter posts runs to  http://<this-host>:<port>/ingest
"""

import json
import os
import re
import sqlite3
import sys
import threading
import urllib.request
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

HERE = os.path.dirname(os.path.abspath(__file__))
DB = os.path.join(HERE, "leaderboard.db")
CHANNELS = os.path.join(HERE, "channels.json")
STATE = os.path.join(HERE, "state.json")   # mode -> last leaderboard message id

TOP_N = 10
EMBED_COLOR = 0x33aa66


# --------------------------------------------------------------------------
# storage
# --------------------------------------------------------------------------
def db():
    conn = sqlite3.connect(DB)
    conn.execute(
        "CREATE TABLE IF NOT EXISTS runs ("
        "mode TEXT, pattern INTEGER, kit TEXT, uuid TEXT, name TEXT, "
        "stage INTEGER, time_ms INTEGER, ts INTEGER, PRIMARY KEY (mode, pattern, kit, uuid))"
    )
    return conn


def upsert_run(run):
    """Insert/update a PB. Returns True if it changed (new PB) or False if no change."""
    mode = str(run.get("mode", ""))
    pattern = int(run.get("pattern", 0))
    kit = str(run.get("kit", "") or "")
    uuid = str(run.get("uuid", ""))
    name = str(run.get("name", ""))
    stage = int(run.get("stage", 0))
    time_ms = int(run.get("timeMs", 0))
    ts = int(run.get("submittedAt", 0) or 0)

    c = db()
    row = c.execute(
        "SELECT stage FROM runs WHERE mode=? AND pattern=? AND kit=? AND uuid=?",
        (mode, pattern, kit, uuid),
    ).fetchone()
    changed = False
    if row is None or stage > row[0]:
        c.execute(
            "INSERT INTO runs (mode, pattern, kit, uuid, name, stage, time_ms, ts) "
            "VALUES (?,?,?,?,?,?,?,?) "
            "ON CONFLICT(mode, pattern, kit, uuid) DO UPDATE SET "
            "name=excluded.name, stage=excluded.stage, time_ms=excluded.time_ms, ts=excluded.ts",
            (mode, pattern, kit, uuid, name, stage, time_ms, ts),
        )
        c.commit()
        changed = True
    c.close()
    return changed


def board_for_mode(mode):
    """Best stage per player across all pattern+kit for a mode, ranked desc."""
    c = db()
    rows = c.execute(
        "SELECT name, MAX(stage) AS best FROM runs WHERE mode=? "
        "GROUP BY uuid ORDER BY best DESC, MAX(time_ms) ASC LIMIT ?",
        (mode, TOP_N),
    ).fetchall()
    c.close()
    return rows


# --------------------------------------------------------------------------
# discord posting
# --------------------------------------------------------------------------
def load_channels():
    try:
        with open(CHANNELS, "r", encoding="utf-8") as fh:
            return json.load(fh)
    except Exception:
        return {}


def load_state():
    try:
        with open(STATE, "r", encoding="utf-8") as fh:
            return json.load(fh)
    except Exception:
        return {}


def save_state(state):
    with open(STATE, "w", encoding="utf-8") as fh:
        json.dump(state, fh)


def _embed_payload(mode, rows):
    if not rows:
        return {"content": f"**{mode} leaderboard** - no runs yet.", "embeds": []}
    lines = []
    for i, (name, best) in enumerate(rows, 1):
        medal = {1: ":first_place:", 2: ":second_place:", 3: ":third_place:"}.get(i, f"{i}.")
        lines.append(f"{medal} **{name}** - stage {best}")
    return {
        "content": f"**{mode} - Monster Maze leaderboard**",
        "embeds": [{"color": EMBED_COLOR, "description": "\n".join(lines)}],
    }


def _post_embed(mode, webhook_url, payload):
    """Post (or edit our last) leaderboard message for a mode."""
    data = json.dumps(payload).encode("utf-8")
    state = load_state()
    mode = mode.lower()
    msg_id = state.get(mode)
    try:
        if msg_id:
            try:
                urllib.request.urlopen(
                    urllib.request.Request(
                        f"{webhook_url}/messages/{msg_id}", data=data, method="PATCH"
                    ),
                    timeout=15,
                )
                return True
            except urllib.error.HTTPError as e:
                if e.code != 404:
                    print(f"edit failed for {mode} ({e.code}); will repost")
        with urllib.request.urlopen(
            urllib.request.Request(webhook_url, data=data, method="POST"), timeout=15
        ) as resp:
            body = json.loads(resp.read().decode("utf-8"))
        state[mode] = body.get("id")
        save_state(state)
        return True
    except Exception as e:
        print(f"discord post failed for {mode}: {e}")
        return False


def post_board(mode, rows):
    """Post (or update) the leaderboard embed for a mode, returning success."""
    webhook_url = load_channels().get(mode.lower())
    if not webhook_url:
        print(f"[{mode}] no webhook configured in channels.json; skipping Discord post")
        return
    return _post_embed(mode, webhook_url, _embed_payload(mode, rows))


# --------------------------------------------------------------------------
# http server
# --------------------------------------------------------------------------
class Handler(BaseHTTPRequestHandler):
    def do_GET(self):
        if self.path.startswith("/health"):
            self._send(200, "ok")
        elif self.path.startswith("/board"):
            mode = self.path.split("?", 1)[1].split("=")[1] if "=" in self.path else ""
            self._send(200, json.dumps([list(r) for r in board_for_mode(mode)]))
        else:
            self._send(404, "not found")

    def do_POST(self):
        if not self.path.startswith("/ingest"):
            self._send(404, "not found")
            return
        try:
            length = int(self.headers.get("Content-Length", 0))
            body = json.loads(self.rfile.read(length).decode("utf-8"))
        except Exception as e:
            self._send(400, "bad request: %s" % e)
            return

        mode = str(body.get("mode", ""))
        changed = upsert_run(body)
        # Always refresh the mode board if this was a PB change.
        ok = post_board(mode, board_for_mode(mode)) if changed else True
        self._send(200, json.dumps({"ok": True, "mode": mode, "changed": changed}))

    def _send(self, code, text):
        data = text.encode("utf-8") if isinstance(text, str) else text
        self.send_response(code)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(data)))
        self.end_headers()
        self.wfile.write(data)

    def log_message(self, fmt, *args):
        print(fmt % args)


def main():
    port = int(sys.argv[1]) if len(sys.argv) > 1 else 8123
    server = ThreadingHTTPServer(("0.0.0.0", port), Handler)
    print(f"Monster Maze backend listening on 0.0.0.0:{port}")
    print(f"Channels file: {CHANNELS}")
    try:
        server.serve_forever()
    except KeyboardInterrupt:
        print("\nshutting down")
        server.server_close()


if __name__ == "__main__":
    main()
