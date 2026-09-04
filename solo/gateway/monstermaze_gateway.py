#!/usr/bin/env python3
"""Monster Maze Minecraft wake gateway.

The gateway is a wake/redirect endpoint, not a gameplay proxy. It parses the
initial Java Edition handshake, identifies the requested Minecraft version,
and starts the matching Fly Machine when a real login arrives. The player is
then told to reconnect directly to the Fly public service, so gameplay traffic
never traverses the Oracle VM.

Server-list status pings never start a Fly Machine. If a Machine is already
running, status traffic may be proxied to the Fly service so the server list
can show its real status.
"""

import asyncio
import json
import logging
import os
import struct
import urllib.error
import urllib.request
from dataclasses import dataclass
from typing import Optional

LOG = logging.getLogger("monstermaze-gateway")
LISTEN_HOST = os.getenv("MM_GATEWAY_HOST", "0.0.0.0")
LISTEN_PORT = int(os.getenv("MM_GATEWAY_PORT", "25565"))
FLY_API_HOST = os.getenv("FLY_API_HOST", "https://api.machines.dev").rstrip("/")
FLY_API_TOKEN = os.environ.get("FLY_API_TOKEN", "")
FLY_APP = os.getenv("FLY_APP", "monstermaze")
FLY_BACKEND_HOST = os.getenv("FLY_BACKEND_HOST", "monstermaze.fly.dev")
FLY_API_TIMEOUT = float(os.getenv("FLY_API_TIMEOUT", "10"))
START_MESSAGE = os.getenv("MM_START_MESSAGE", "Monster Maze is starting this server.\\n\\nPlease reconnect in about 60 seconds.")

@dataclass(frozen=True)
class Target:
    name: str
    protocol: int
    backend_port: int
    machine_id: str
    version_name: str

TARGETS = {
    "1.8": Target("1.8", 47, 25565, os.getenv("MM18_MACHINE_ID", "84503ef24605e8"), "1.8.9"),
    "1.21": Target("1.21", int(os.getenv("MM21_PROTOCOL", "774")), 25566, os.getenv("MM21_MACHINE_ID", "85d3e1b44dd7e8"), "1.21.11"),
}
START_LOCKS = {key: asyncio.Lock() for key in TARGETS}

class ProtocolError(Exception):
    pass

async def read_exact(reader: asyncio.StreamReader, n: int) -> bytes:
    return await reader.readexactly(n)

async def read_varint(reader: asyncio.StreamReader) -> tuple[int, bytes]:
    value = 0
    shift = 0
    raw = bytearray()
    for _ in range(5):
        b = (await read_exact(reader, 1))[0]
        raw.append(b)
        value |= (b & 0x7F) << shift
        if not (b & 0x80):
            return value, bytes(raw)
        shift += 7
    raise ProtocolError("VarInt is too long")

def encode_varint(value: int) -> bytes:
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

async def read_packet(reader: asyncio.StreamReader) -> tuple[bytes, bytes]:
    length, length_raw = await read_varint(reader)
    if length <= 0 or length > 2 * 1024 * 1024:
        raise ProtocolError(f"invalid packet length {length}")
    payload = await read_exact(reader, length)
    return length_raw + payload, payload

def read_varint_bytes(data: bytes, offset: int = 0) -> tuple[int, int]:
    value = 0
    shift = 0
    for i in range(5):
        if offset + i >= len(data):
            raise ProtocolError("truncated VarInt")
        b = data[offset + i]
        value |= (b & 0x7F) << shift
        if not (b & 0x80):
            return value, offset + i + 1
        shift += 7
    raise ProtocolError("VarInt is too long")

def read_string_bytes(data: bytes, offset: int) -> tuple[str, int]:
    length, offset = read_varint_bytes(data, offset)
    if length < 0 or offset + length > len(data):
        raise ProtocolError("invalid string length")
    try:
        return data[offset:offset + length].decode("utf-8"), offset + length
    except UnicodeDecodeError as exc:
        raise ProtocolError("invalid UTF-8 string") from exc

def parse_handshake(payload: bytes) -> tuple[int, str, int, int]:
    packet_id, offset = read_varint_bytes(payload)
    if packet_id != 0:
        raise ProtocolError(f"expected handshake packet 0x00, got 0x{packet_id:02x}")
    protocol, offset = read_varint_bytes(payload, offset)
    host, offset = read_string_bytes(payload, offset)
    if offset + 2 > len(payload):
        raise ProtocolError("truncated handshake port")
    port = struct.unpack(">H", payload[offset:offset + 2])[0]
    offset += 2
    next_state, offset = read_varint_bytes(payload, offset)
    if next_state not in (1, 2):
        raise ProtocolError(f"unsupported handshake next state {next_state}")
    return protocol, host, port, next_state

def target_for_protocol(protocol: int) -> Optional[Target]:
    if protocol == TARGETS["1.8"].protocol:
        return TARGETS["1.8"]
    if protocol == TARGETS["1.21"].protocol:
        return TARGETS["1.21"]
    return None

def json_string_packet(packet_id: int, text: str) -> bytes:
    encoded = text.encode("utf-8")
    body = encode_varint(packet_id) + encode_varint(len(encoded)) + encoded
    return encode_varint(len(body)) + body

def start_disconnect(message: str) -> bytes:
    reason = json.dumps({"text": message}, separators=(",", ":"))
    return json_string_packet(0, reason)

def static_status(target: Target) -> bytes:
    payload = {
        "version": {"name": target.version_name, "protocol": target.protocol},
        "players": {"max": 20, "online": 0, "sample": []},
        "description": {"text": "Monster Maze — login here to wake the server"},
    }
    return json_string_packet(0, json.dumps(payload, separators=(",", ":")))

def fly_request(method: str, path: str) -> tuple[int, bytes]:
    if not FLY_API_TOKEN:
        raise RuntimeError("FLY_API_TOKEN is not configured")
    request = urllib.request.Request(
        FLY_API_HOST + path,
        method=method,
        headers={"Authorization": "Bearer " + FLY_API_TOKEN, "Content-Type": "application/json", "User-Agent": "MonsterMaze-Gateway/1"},
    )
    try:
        with urllib.request.urlopen(request, timeout=FLY_API_TIMEOUT) as response:
            return response.status, response.read()
    except urllib.error.HTTPError as exc:
        return exc.code, exc.read()

async def machine_state(target: Target) -> str:
    status, body = await asyncio.to_thread(fly_request, "GET", f"/v1/apps/{FLY_APP}/machines/{target.machine_id}")
    if status != 200:
        raise RuntimeError(f"Fly machine GET returned HTTP {status}: {body[:300]!r}")
    return str(json.loads(body.decode("utf-8")).get("state", "unknown"))

async def start_machine(target: Target) -> None:
    async with START_LOCKS[target.name]:
        state = await machine_state(target)
        LOG.info("%s Machine %s is %s", target.name, target.machine_id, state)
        if state == "started":
            return
        if state not in ("stopped", "suspended", "starting", "restarting"):
            raise RuntimeError(f"cannot start Machine in state {state}")
        if state in ("starting", "restarting"):
            return
        status, body = await asyncio.to_thread(fly_request, "POST", f"/v1/apps/{FLY_APP}/machines/{target.machine_id}/start")
        if status not in (200, 202):
            raise RuntimeError(f"Fly machine start returned HTTP {status}: {body[:300]!r}")
        LOG.info("Started %s Machine %s", target.name, target.machine_id)

async def connect_backend(target: Target) -> tuple[asyncio.StreamReader, asyncio.StreamWriter]:
    return await asyncio.wait_for(asyncio.open_connection(FLY_BACKEND_HOST, target.backend_port), timeout=5)

async def pipe(reader: asyncio.StreamReader, writer: asyncio.StreamWriter) -> None:
    try:
        while True:
            data = await reader.read(65536)
            if not data:
                break
            writer.write(data)
            await writer.drain()
    except (ConnectionError, asyncio.IncompleteReadError, BrokenPipeError):
        pass

async def proxy_pair(client_reader: asyncio.StreamReader, client_writer: asyncio.StreamWriter, backend_reader: asyncio.StreamReader, backend_writer: asyncio.StreamWriter, first_packet: bytes) -> None:
    backend_writer.write(first_packet)
    await backend_writer.drain()
    a = asyncio.create_task(pipe(client_reader, backend_writer))
    b = asyncio.create_task(pipe(backend_reader, client_writer))
    _, pending = await asyncio.wait((a, b), return_when=asyncio.FIRST_COMPLETED)
    for task in pending:
        task.cancel()
    await asyncio.gather(*pending, return_exceptions=True)

async def handle_status(target: Target, client_reader: asyncio.StreamReader, client_writer: asyncio.StreamWriter, first_packet: bytes) -> None:
    # NEVER connect to the public Fly Minecraft service unless Fly already
    # reports the Machine as started. Connecting to a Flycast service can cause
    # Fly Proxy to autostart a stopped Machine, which must never happen for a
    # server-list refresh.
    try:
        state = await machine_state(target)
    except Exception as exc:
        LOG.warning("Could not query %s Machine state for status ping: %s", target.name, exc)
        client_writer.write(static_status(target))
        await client_writer.drain()
        return

    if state != "started":
        LOG.info("Status ping for %s while Machine is %s; returning static status without backend connection", target.name, state)
        client_writer.write(static_status(target))
        await client_writer.drain()
        return

    try:
        backend_reader, backend_writer = await connect_backend(target)
    except Exception:
        client_writer.write(static_status(target))
        await client_writer.drain()
        return

    backend_writer.write(first_packet)
    await backend_writer.drain()
    await proxy_pair(client_reader, client_writer, backend_reader, backend_writer, b"")
    backend_writer.close()
    client_writer.close()

async def handle_login(target: Target, client_writer: asyncio.StreamWriter) -> None:
    """Wake the backend and redirect the player to its direct Fly address."""
    state = await machine_state(target)
    if state != "started":
        await start_machine(target)
        LOG.info("%s Machine was stopped; waking it and redirecting player to direct Fly service", target.name)
        message = f"{START_MESSAGE}\\n\\nDirect server: {FLY_BACKEND_HOST}:{target.backend_port}"
    else:
        LOG.info("%s Machine is already running; redirecting player directly to Fly service", target.name)
        message = f"Monster Maze is already running.\\n\\nPlease connect directly to {FLY_BACKEND_HOST}:{target.backend_port}."
    client_writer.write(start_disconnect(message))
    await client_writer.drain()

async def handle_client(reader: asyncio.StreamReader, writer: asyncio.StreamWriter) -> None:
    peer = writer.get_extra_info("peername")
    try:
        first_packet, payload = await read_packet(reader)
        protocol, host, port, next_state = parse_handshake(payload)
        target = target_for_protocol(protocol)
        LOG.info("Connection from %s: protocol=%s host=%s port=%s state=%s target=%s", peer, protocol, host, port, next_state, target.name if target else "unknown")
        if target is None:
            writer.write(start_disconnect("Unsupported Minecraft version. Please use Minecraft 1.8.9 or 1.21.11."))
            await writer.drain()
            return
        if next_state == 1:
            await handle_status(target, reader, writer, first_packet)
        else:
            await handle_login(target, writer)
    except (asyncio.IncompleteReadError, ConnectionError, ProtocolError) as exc:
        LOG.debug("Connection %s closed/invalid: %s", peer, exc)
    except Exception:
        LOG.exception("Unhandled gateway error for %s", peer)
    finally:
        if not writer.is_closing():
            writer.close()
            try:
                await writer.wait_closed()
            except Exception:
                pass

async def main() -> None:
    if not FLY_API_TOKEN:
        raise SystemExit("FLY_API_TOKEN is required")
    server = await asyncio.start_server(handle_client, LISTEN_HOST, LISTEN_PORT)
    addresses = ", ".join(str(sock.getsockname()) for sock in (server.sockets or []))
    LOG.info("Monster Maze wake gateway listening on %s", addresses)
    LOG.info("Wake targets: 1.8 -> %s:%s; 1.21 -> %s:%s", FLY_BACKEND_HOST, TARGETS["1.8"].backend_port, FLY_BACKEND_HOST, TARGETS["1.21"].backend_port)
    async with server:
        await server.serve_forever()

if __name__ == "__main__":
    logging.basicConfig(level=os.getenv("MM_GATEWAY_LOG_LEVEL", "INFO").upper(), format="%(asctime)s [%(levelname)s] [MonsterMazeGateway] %(message)s")
    try:
        asyncio.run(main())
    except KeyboardInterrupt:
        pass
