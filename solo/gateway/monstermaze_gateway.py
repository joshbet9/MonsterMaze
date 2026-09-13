#!/usr/bin/env python3
"""Monster Maze Minecraft wake gateway."""
import asyncio
import json
import logging
import os
import struct
import urllib.error
import urllib.parse
import urllib.request
from dataclasses import dataclass
from uuid import UUID

from starter_state import record_started_by

LOG = logging.getLogger("monstermaze-gateway")
LISTEN_HOST = os.getenv("MM_GATEWAY_HOST", "0.0.0.0")
LISTEN_PORT = int(os.getenv("MM_GATEWAY_PORT", "25565"))
FLY_API_HOST = os.getenv("FLY_API_HOST", "https://api.machines.dev").rstrip("/")
FLY_API_TOKEN = os.environ.get("FLY_API_TOKEN", "")
FLY_APP = os.getenv("FLY_APP", "monstermaze")
FLY_BACKEND_HOST = os.getenv("FLY_BACKEND_HOST", "monstermaze.fly.dev")
FLY_API_TIMEOUT = float(os.getenv("FLY_API_TIMEOUT", "10"))
LOGIN_PROBE_TIMEOUT = float(os.getenv("MM_LOGIN_PROBE_TIMEOUT", "3"))
MINECRAFT_PROFILE_LOOKUP_URL = os.getenv("MM_MINECRAFT_PROFILE_LOOKUP_URL", "https://api.minecraftservices.com/minecraft/profile/lookup/name")
MINECRAFT_PROFILE_TIMEOUT = float(os.getenv("MM_MINECRAFT_PROFILE_TIMEOUT", "3"))
START_MESSAGE = os.getenv("MM_START_MESSAGE", "Monster Maze is starting this server.\n\nPlease reconnect in about 60 seconds.")
IGN_BLACKLIST_FILE = os.getenv("MM_IGN_BLACKLIST_FILE", "/home/ubuntu/monstermaze/data/ign_blacklist.json")


@dataclass(frozen=True)
class Target:
    name: str
    protocols: tuple[int, ...]
    backend_port: int
    machine_id: str
    version_name: str


def parse_protocols(value: str) -> tuple[int, ...]:
    return tuple(int(item.strip()) for item in value.split(",") if item.strip())


TARGETS = {
    "1.8": Target("1.8", (47,), 25565, os.getenv("MM18_MACHINE_ID", "84503ef24605e8"), "1.8.9"),
    "1.21": Target("1.21", parse_protocols(os.getenv("MM21_PROTOCOLS", "774,775,776")), 25566, os.getenv("MM21_MACHINE_ID", "85d3e1b44dd7e8"), "1.21.11"),
}
START_LOCKS = {key: asyncio.Lock() for key in TARGETS}


class ProtocolError(Exception):
    pass


def load_ign_blacklist():
    try:
        with open(IGN_BLACKLIST_FILE, "r", encoding="utf-8") as handle:
            data = json.load(handle)
        if isinstance(data, list):
            return {str(item).strip().casefold() for item in data if str(item).strip()}
    except (FileNotFoundError, OSError, json.JSONDecodeError):
        pass
    return {item.strip().casefold() for item in os.getenv("MM_IGN_BLACKLIST", "").split(",") if item.strip()}


async def read_exact(reader, n):
    return await reader.readexactly(n)


async def read_varint(reader):
    value = 0
    shift = 0
    raw = bytearray()
    for _ in range(5):
        b = (await read_exact(reader, 1))[0]
        raw.append(b)
        value |= (b & 0x7F) << shift
        if not b & 0x80:
            return value, bytes(raw)
        shift += 7
    raise ProtocolError("VarInt is too long")


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


async def read_packet(reader):
    length, length_raw = await read_varint(reader)
    if length <= 0 or length > 2 * 1024 * 1024:
        raise ProtocolError(f"invalid packet length {length}")
    payload = await read_exact(reader, length)
    return length_raw + payload, payload


def read_varint_bytes(data, offset=0):
    value = 0
    shift = 0
    for i in range(5):
        if offset + i >= len(data):
            raise ProtocolError("truncated VarInt")
        b = data[offset + i]
        value |= (b & 0x7F) << shift
        if not b & 0x80:
            return value, offset + i + 1
        shift += 7
    raise ProtocolError("VarInt is too long")


def read_string_bytes(data, offset):
    length, offset = read_varint_bytes(data, offset)
    if length < 0 or offset + length > len(data):
        raise ProtocolError("invalid string length")
    try:
        return data[offset:offset + length].decode("utf-8"), offset + length
    except UnicodeDecodeError as exc:
        raise ProtocolError("invalid UTF-8 string") from exc


def parse_handshake(payload):
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


def parse_login_start(payload):
    packet_id, offset = read_varint_bytes(payload)
    if packet_id != 0:
        raise ProtocolError(f"expected login-start packet 0x00, got 0x{packet_id:02x}")
    username, offset = read_string_bytes(payload, offset)
    remaining = payload[offset:]
    uuid_candidate = str(UUID(bytes=remaining)) if len(remaining) == 16 else None
    return username, uuid_candidate, remaining


def target_for_protocol(protocol):
    return next((target for target in TARGETS.values() if protocol in target.protocols), None)


def json_string_packet(packet_id, text):
    encoded = text.encode("utf-8")
    body = encode_varint(packet_id) + encode_varint(len(encoded)) + encoded
    return encode_varint(len(body)) + body


def start_disconnect(message):
    return json_string_packet(0, json.dumps({"text": message}, separators=(",", ":")))


def static_status(target):
    payload = {
        "version": {"name": target.version_name, "protocol": target.protocols[0]},
        "players": {"max": 20, "online": 0, "sample": []},
        "description": {"text": "Monster Maze — login here to wake the server"},
    }
    return json_string_packet(0, json.dumps(payload, separators=(",", ":")))


def fly_request(method, path):
    if not FLY_API_TOKEN:
        raise RuntimeError("FLY_API_TOKEN is not configured")
    request = urllib.request.Request(
        FLY_API_HOST + path,
        method=method,
        headers={
            "Authorization": "Bearer " + FLY_API_TOKEN,
            "Content-Type": "application/json",
            "User-Agent": "MonsterMaze-Gateway/1",
        },
    )
    try:
        with urllib.request.urlopen(request, timeout=FLY_API_TIMEOUT) as response:
            return response.status, response.read()
    except urllib.error.HTTPError as exc:
        return exc.code, exc.read()


def lookup_username_uuid(username):
    url = f"{MINECRAFT_PROFILE_LOOKUP_URL.rstrip('/')}/{urllib.parse.quote(username, safe='')}"
    request = urllib.request.Request(
        url,
        method="GET",
        headers={"Accept": "application/json", "User-Agent": "MonsterMaze-Gateway/1"},
    )
    try:
        with urllib.request.urlopen(request, timeout=MINECRAFT_PROFILE_TIMEOUT) as response:
            body = response.read()
    except urllib.error.HTTPError as exc:
        if exc.code == 404:
            return None
        raise RuntimeError(f"Minecraft profile lookup returned HTTP {exc.code}") from exc
    except urllib.error.URLError as exc:
        raise RuntimeError(f"Minecraft profile lookup failed: {exc.reason}") from exc
    try:
        profile = json.loads(body.decode("utf-8"))
    except (UnicodeDecodeError, json.JSONDecodeError) as exc:
        raise RuntimeError("Minecraft profile lookup returned invalid JSON") from exc
    profile_id = profile.get("id")
    profile_name = profile.get("name")
    if not isinstance(profile_id, str) or len(profile_id) != 32 or not isinstance(profile_name, str):
        raise RuntimeError("Minecraft profile lookup returned an invalid profile")
    return str(UUID(profile_id))


async def machine_state(target):
    status, body = await asyncio.to_thread(
        fly_request, "GET", f"/v1/apps/{FLY_APP}/machines/{target.machine_id}"
    )
    if status != 200:
        raise RuntimeError(f"Fly machine GET returned HTTP {status}: {body[:300]!r}")
    return str(json.loads(body.decode("utf-8")).get("state", "unknown"))


async def start_machine(target):
    async with START_LOCKS[target.name]:
        state = await machine_state(target)
        LOG.info("%s Machine %s is %s", target.name, target.machine_id, state)
        if state == "started":
            return False
        if state not in ("stopped", "suspended", "starting", "restarting"):
            raise RuntimeError(f"cannot start Machine in state {state}")
        if state in ("starting", "restarting"):
            return False
        status, body = await asyncio.to_thread(
            fly_request, "POST", f"/v1/apps/{FLY_APP}/machines/{target.machine_id}/start"
        )
        if status not in (200, 202):
            raise RuntimeError(f"Fly machine start returned HTTP {status}: {body[:300]!r}")
        LOG.info("Started %s Machine %s", target.name, target.machine_id)
        return True


async def connect_backend(target):
    return await asyncio.wait_for(asyncio.open_connection(FLY_BACKEND_HOST, target.backend_port), timeout=5)


async def pipe(reader, writer):
    try:
        while True:
            data = await reader.read(65536)
            if not data:
                break
            writer.write(data)
            await writer.drain()
    except (ConnectionError, asyncio.IncompleteReadError, BrokenPipeError):
        pass


async def proxy_pair(client_reader, client_writer, backend_reader, backend_writer, first_packet):
    backend_writer.write(first_packet)
    await backend_writer.drain()
    a = asyncio.create_task(pipe(client_reader, backend_writer))
    b = asyncio.create_task(pipe(backend_reader, client_writer))
    _, pending = await asyncio.wait((a, b), return_when=asyncio.FIRST_COMPLETED)
    for task in pending:
        task.cancel()
    await asyncio.gather(*pending, return_exceptions=True)


async def handle_status(target, client_reader, client_writer, first_packet):
    try:
        state = await machine_state(target)
    except Exception as exc:
        LOG.warning("Could not query %s Machine state for status ping: %s", target.name, exc)
        client_writer.write(static_status(target))
        await client_writer.drain()
        return
    if state != "started":
        LOG.info(
            "Status ping for %s while Machine is %s; returning static status without backend connection",
            target.name,
            state,
        )
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


async def handle_login(target, client_reader, client_writer, peer):
    try:
        _, login_payload = await asyncio.wait_for(read_packet(client_reader), timeout=LOGIN_PROBE_TIMEOUT)
        username, uuid_candidate, remaining = parse_login_start(login_payload)
        if username.casefold() in load_ign_blacklist():
            LOG.warning(
                "BLOCKED LOGIN from %s target=%s username=%r: IGN is blacklisted; not waking Machine",
                peer,
                target.name,
                username,
            )
            return
        if uuid_candidate:
            LOG.info(
                "LOGIN PROBE from %s target=%s username=%r uuid_candidate=%s remaining_bytes=0",
                peer,
                target.name,
                username,
                uuid_candidate,
            )
            try:
                authoritative_uuid = await asyncio.to_thread(lookup_username_uuid, username)
            except Exception as exc:
                LOG.warning(
                    "BLOCKED LOGIN from %s target=%s username=%r uuid_candidate=%s: UUID lookup failed: %s; not waking Machine",
                    peer,
                    target.name,
                    username,
                    uuid_candidate,
                    exc,
                )
                return
            if authoritative_uuid is None:
                LOG.warning(
                    "BLOCKED LOGIN from %s target=%s username=%r uuid_candidate=%s: username not found by Minecraft profile lookup; not waking Machine",
                    peer,
                    target.name,
                    username,
                    uuid_candidate,
                )
                return
            if UUID(uuid_candidate).hex != UUID(authoritative_uuid).hex:
                LOG.warning(
                    "BLOCKED LOGIN from %s target=%s username=%r uuid_candidate=%s authoritative_uuid=%s: UUID mismatch; not waking Machine",
                    peer,
                    target.name,
                    username,
                    uuid_candidate,
                    authoritative_uuid,
                )
                return
            LOG.info(
                "UUID MATCH from %s target=%s username=%r uuid=%s",
                peer,
                target.name,
                username,
                authoritative_uuid,
            )
        else:
            LOG.info(
                "LOGIN PROBE from %s target=%s username=%r uuid_candidate=none remaining_bytes=%d remaining_hex=%s",
                peer,
                target.name,
                username,
                len(remaining),
                remaining.hex(),
            )
            LOG.warning(
                "BLOCKED LOGIN from %s target=%s username=%r: no UUID supplied; not waking Machine",
                peer,
                target.name,
                username,
            )
            return
    except asyncio.TimeoutError:
        LOG.warning(
            "LOGIN PROBE from %s target=%s: no login-start packet within %.1fs; not waking Machine",
            peer,
            target.name,
            LOGIN_PROBE_TIMEOUT,
        )
        return
    except (asyncio.IncompleteReadError, ConnectionError, ProtocolError) as exc:
        LOG.warning(
            "LOGIN PROBE from %s target=%s failed before identity could be read: %s; not waking Machine",
            peer,
            target.name,
            exc,
        )
        return
    state = await machine_state(target)
    if state != "started":
        started = await start_machine(target)
        if started:
            try:
                record_started_by(target.name, username)
            except Exception as exc:
                LOG.error("Could not record starter for %s: %s", target.name, exc)
        LOG.info("%s Machine was stopped; waking it and redirecting player to direct Fly service", target.name)
        message = f"{START_MESSAGE}\n\nDirect server: {FLY_BACKEND_HOST}:{target.backend_port}"
    else:
        LOG.info("%s Machine is already running; redirecting player directly to Fly service", target.name)
        message = f"Monster Maze is already running.\n\nPlease connect directly to {FLY_BACKEND_HOST}:{target.backend_port}."
    client_writer.write(start_disconnect(message))
    await client_writer.drain()


async def handle_client(reader, writer):
    peer = writer.get_extra_info("peername")
    try:
        first_packet, payload = await read_packet(reader)
        protocol, host, port, next_state = parse_handshake(payload)
        target = target_for_protocol(protocol)
        LOG.info(
            "Connection from %s: protocol=%s host=%s port=%s state=%s target=%s",
            peer,
            protocol,
            host,
            port,
            next_state,
            target.name if target else "unknown",
        )
        if target is None:
            writer.write(start_disconnect("Unsupported Minecraft version. Please use Minecraft 1.8.9, 1.21.11, 26.1.x, or 26.2."))
            await writer.drain()
            return
        if next_state == 1:
            await handle_status(target, reader, writer, first_packet)
        else:
            await handle_login(target, reader, writer, peer)
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


async def main():
    if not FLY_API_TOKEN:
        raise SystemExit("FLY_API_TOKEN is required")
    server = await asyncio.start_server(handle_client, LISTEN_HOST, LISTEN_PORT)
    addresses = ", ".join(str(sock.getsockname()) for sock in (server.sockets or []))
    LOG.info("Monster Maze wake gateway listening on %s", addresses)
    LOG.info(
        "Wake targets: 1.8 protocols=%s -> %s:%s; 1.21 protocols=%s -> %s:%s",
        TARGETS["1.8"].protocols,
        FLY_BACKEND_HOST,
        TARGETS["1.8"].backend_port,
        TARGETS["1.21"].protocols,
        FLY_BACKEND_HOST,
        TARGETS["1.21"].backend_port,
    )
    if load_ign_blacklist():
        LOG.info("IGN blacklist configured with %d entries", len(load_ign_blacklist()))
    async with server:
        await server.serve_forever()


if __name__ == "__main__":
    logging.basicConfig(
        level=os.getenv("MM_GATEWAY_LOG_LEVEL", "INFO").upper(),
        format="%(asctime)s [%(levelname)s] [MonsterMazeGateway] %(message)s",
    )
    try:
        asyncio.run(main())
    except KeyboardInterrupt:
        pass
