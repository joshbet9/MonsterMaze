#!/usr/bin/env python3
"""Server status API for the Discord status display.

The API queries Fly Machine state, but NEVER connects to the Minecraft service.
Running Monster Maze servers push short-lived player-count heartbeats here.
This keeps the Oracle gateway entirely out of the Minecraft status path and
prevents Discord refreshes from creating traffic to Fly's public service.
"""
import json
import logging
import os
import threading
import time
import urllib.error
import urllib.request
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

LOG = logging.getLogger("monstermaze-status")
HOST = os.getenv("MM_STATUS_HOST", "0.0.0.0")
PORT = int(os.getenv("MM_STATUS_PORT", "8765"))
FLY_API_HOST = os.getenv("FLY_API_HOST", "https://api.machines.dev").rstrip("/")
FLY_API_TOKEN = os.environ.get("FLY_API_TOKEN", "")
FLY_APP = os.getenv("FLY_APP", "monstermaze")
FLY_API_TIMEOUT = float(os.getenv("FLY_API_TIMEOUT", "10"))
HEARTBEAT_TOKEN = os.getenv("MM_STATUS_HEARTBEAT_TOKEN", "")
HEARTBEAT_MAX_AGE = float(os.getenv("MM_STATUS_HEARTBEAT_MAX_AGE", "45"))

TARGETS = {
    "1.8": {"machine_id": os.getenv("MM18_MACHINE_ID", "84503ef24605e8"), "version": "1.8.9"},
    "1.21": {"machine_id": os.getenv("MM21_MACHINE_ID", "85d3e1b44dd7e8"), "version": "1.21.11"},
}

HEARTBEATS = {}
HEARTBEATS_LOCK = threading.Lock()


def fly_request(path):
    if not FLY_API_TOKEN:
        raise RuntimeError("FLY_API_TOKEN is not configured")
    req = urllib.request.Request(FLY_API_HOST + path, method="GET", headers={"Authorization": "Bearer " + FLY_API_TOKEN, "User-Agent": "MonsterMaze-Status/2"})
    try:
        with urllib.request.urlopen(req, timeout=FLY_API_TIMEOUT) as response:
            return response.status, response.read()
    except urllib.error.HTTPError as exc:
        return exc.code, exc.read()


def machine_state(target):
    status, body = fly_request(f"/v1/apps/{FLY_APP}/machines/{target['machine_id']}")
    if status != 200:
        raise RuntimeError(f"Fly machine GET returned HTTP {status}")
    return str(json.loads(body.decode("utf-8")).get("state", "unknown"))


def heartbeat_status(name):
    with HEARTBEATS_LOCK:
        heartbeat = HEARTBEATS.get(name)
    if heartbeat is None or time.monotonic() - heartbeat["received"] > HEARTBEAT_MAX_AGE:
        return None
    return heartbeat["players"]


def one_status(name, target):
    try:
        state = machine_state(target)
    except Exception as exc:
        LOG.warning("Could not query %s Machine: %s", name, exc)
        return {"name": name, "state": "unknown", "players": None, "version": target["version"]}
    if state in ("stopped", "suspended"):
        return {"name": name, "state": "offline", "players": None, "version": target["version"]}
    if state in ("starting", "restarting"):
        return {"name": name, "state": "starting", "players": None, "version": target["version"]}
    if state != "started":
        return {"name": name, "state": "starting", "players": None, "version": target["version"]}
    players = heartbeat_status(name)
    if players is None:
        return {"name": name, "state": "starting", "players": None, "version": target["version"]}
    return {"name": name, "state": "online", "players": players, "version": target["version"]}


class Handler(BaseHTTPRequestHandler):
    def do_GET(self):
        if self.path not in ("/status", "/status/"):
            self.send_error(404)
            return
        payload = {"servers": {name: one_status(name, target) for name, target in TARGETS.items()}}
        body = json.dumps(payload, separators=(",", ":")).encode("utf-8")
        self.send_response(200)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(body)))
        self.send_header("Cache-Control", "no-store")
        self.end_headers()
        self.wfile.write(body)

    def do_POST(self):
        if self.path not in ("/heartbeat", "/heartbeat/"):
            self.send_error(404)
            return
        if not HEARTBEAT_TOKEN:
            self.send_error(503, "Heartbeat endpoint is not configured")
            return
        if self.headers.get("Authorization", "") != "Bearer " + HEARTBEAT_TOKEN:
            self.send_error(401, "Unauthorized")
            return
        try:
            length = int(self.headers.get("Content-Length", "0"))
            if length <= 0 or length > 4096:
                raise ValueError("invalid request size")
            data = json.loads(self.rfile.read(length).decode("utf-8"))
            name = str(data.get("server", ""))
            players = int(data.get("players", -1))
            if name not in TARGETS or players < 0 or players > 100:
                raise ValueError("invalid heartbeat")
            with HEARTBEATS_LOCK:
                HEARTBEATS[name] = {"players": players, "received": time.monotonic()}
            body = b"{\"ok\":true}"
            self.send_response(200)
            self.send_header("Content-Type", "application/json")
            self.send_header("Content-Length", str(len(body)))
            self.end_headers()
            self.wfile.write(body)
        except Exception as exc:
            LOG.info("Invalid heartbeat from %s: %s", self.address_string(), exc)
            self.send_error(400, "Invalid heartbeat")

    def log_message(self, fmt, *args):
        LOG.debug("%s - %s", self.address_string(), fmt % args)


def main():
    if not FLY_API_TOKEN:
        raise SystemExit("FLY_API_TOKEN is required")
    if not HEARTBEAT_TOKEN:
        raise SystemExit("MM_STATUS_HEARTBEAT_TOKEN is required")
    logging.basicConfig(level=os.getenv("MM_STATUS_LOG_LEVEL", "INFO").upper(), format="%(asctime)s [%(levelname)s] [MonsterMazeStatus] %(message)s")
    server = ThreadingHTTPServer((HOST, PORT), Handler)
    LOG.info("Status API listening on http://%s:%s/status and /heartbeat", HOST, PORT)
    server.serve_forever()


if __name__ == "__main__":
    main()
