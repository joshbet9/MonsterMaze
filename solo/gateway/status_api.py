#!/usr/bin/env python3
"""Local-only server status API for the Discord server-status display.

This service is deliberately separate from the Minecraft wake listener. It
queries Fly Machine state first and only connects to the Minecraft backend
when Fly already reports the Machine as started. A stopped Machine is never
contacted, so Discord status refreshes cannot wake Fly.
"""
import json
import logging
import os
import socket
import struct
import urllib.error
import urllib.request
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

LOG = logging.getLogger("monstermaze-status")
HOST = os.getenv("MM_STATUS_HOST", "127.0.0.1")
PORT = int(os.getenv("MM_STATUS_PORT", "8765"))
FLY_API_HOST = os.getenv("FLY_API_HOST", "https://api.machines.dev").rstrip("/")
FLY_API_TOKEN = os.environ.get("FLY_API_TOKEN", "")
FLY_APP = os.getenv("FLY_APP", "monstermaze")
FLY_BACKEND_HOST = os.getenv("FLY_BACKEND_HOST", "monstermaze.fly.dev")
FLY_API_TIMEOUT = float(os.getenv("FLY_API_TIMEOUT", "10"))
BACKEND_TIMEOUT = float(os.getenv("MM_STATUS_BACKEND_TIMEOUT", "5"))

TARGETS = {
    "1.8": {
        "protocol": 47,
        "port": 25565,
        "machine_id": os.getenv("MM18_MACHINE_ID", "84503ef24605e8"),
        "version": "1.8.9",
    },
    "1.21": {
        "protocol": int(os.getenv("MM21_STATUS_PROTOCOL", "774")),
        "port": 25566,
        "machine_id": os.getenv("MM21_MACHINE_ID", "85d3e1b44dd7e8"),
        "version": "1.21.11",
    },
}


def fly_request(path):
    if not FLY_API_TOKEN:
        raise RuntimeError("FLY_API_TOKEN is not configured")
    req = urllib.request.Request(
        FLY_API_HOST + path,
        method="GET",
        headers={
            "Authorization": "Bearer " + FLY_API_TOKEN,
            "User-Agent": "MonsterMaze-Status/1",
        },
    )
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


def encode_varint(value):
    out = bytearray()
    value &= 0xFFFFFFFF
    while True:
        b = value & 0x7F
        value >>= 7
        if value:
            out.append(b | 0x80)
        else:
            out.append(b)
            return bytes(out)


def read_varint(sock):
    value = 0
    shift = 0
    for _ in range(5):
        b = sock.recv(1)
        if not b:
            raise RuntimeError("backend closed while reading VarInt")
        b = b[0]
        value |= (b & 0x7F) << shift
        if not (b & 0x80):
            return value
        shift += 7
    raise RuntimeError("backend VarInt too long")


def read_exact(sock, size):
    data = bytearray()
    while len(data) < size:
        chunk = sock.recv(size - len(data))
        if not chunk:
            raise RuntimeError("backend closed early")
        data.extend(chunk)
    return bytes(data)


def backend_player_count(target):
    """Perform a normal server-list status request, never a login/wake request."""
    host_bytes = FLY_BACKEND_HOST.encode("utf-8")
    handshake_payload = (
        encode_varint(0)
        + encode_varint(target["protocol"])
        + encode_varint(len(host_bytes))
        + host_bytes
        + struct.pack(">H", target["port"])
        + encode_varint(1)
    )
    handshake = encode_varint(len(handshake_payload)) + handshake_payload
    status_request = b"\x01\x00"
    with socket.create_connection((FLY_BACKEND_HOST, target["port"]), timeout=BACKEND_TIMEOUT) as sock:
        sock.sendall(handshake + status_request)
        packet_length = read_varint(sock)
        packet = read_exact(sock, packet_length)
    packet_id = read_varint_from_bytes(packet)
    if packet_id != 0:
        raise RuntimeError(f"unexpected status packet id {packet_id}")
    json_length, offset = read_varint_from_bytes(packet, return_offset=True)
    payload = packet[offset:offset + json_length]
    status = json.loads(payload.decode("utf-8"))
    return int(status.get("players", {}).get("online", 0))


def read_varint_from_bytes(data, return_offset=False):
    value = 0
    shift = 0
    for i in range(5):
        if i >= len(data):
            raise RuntimeError("truncated packet VarInt")
        b = data[i]
        value |= (b & 0x7F) << shift
        if not (b & 0x80):
            return (value, i + 1) if return_offset else value
        shift += 7
    raise RuntimeError("packet VarInt too long")


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

    try:
        players = backend_player_count(target)
        return {"name": name, "state": "online", "players": players, "version": target["version"]}
    except Exception as exc:
        LOG.info("%s Machine is started but backend status is unavailable: %s", name, exc)
        return {"name": name, "state": "starting", "players": None, "version": target["version"]}


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

    def log_message(self, fmt, *args):
        LOG.debug("%s - %s", self.address_string(), fmt % args)


def main():
    if not FLY_API_TOKEN:
        raise SystemExit("FLY_API_TOKEN is required")
    logging.basicConfig(
        level=os.getenv("MM_STATUS_LOG_LEVEL", "INFO").upper(),
        format="%(asctime)s [%(levelname)s] [MonsterMazeStatus] %(message)s",
    )
    server = ThreadingHTTPServer((HOST, PORT), Handler)
    LOG.info("Status API listening on http://%s:%s/status", HOST, PORT)
    server.serve_forever()


if __name__ == "__main__":
    main()
