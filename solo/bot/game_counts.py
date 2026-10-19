"""Poll Mineplex live game counts and maintain one Discord status message.

Mineplex's API key is read from the Oracle service environment as MINEPLEX_API_KEY.
This intentionally does not try to invoke Plexify's /gamecounts slash command:
Discord bots cannot execute another application's slash command as a user.
"""
import asyncio
import json
import os
import re
import time
import urllib.request
from datetime import datetime, timezone

import discord
import monster_bot_v2 as base


API_BASE_DEFAULT = "https://api.mineplex.com"
CHANNEL_DEFAULT = "game-counts"
INTERVAL_DEFAULT = 30
BOARD_KEY = "game-counts"
REQUEST_TIMEOUT = 10

_NAME_KEYS = (
    "displayName", "friendlyName", "gameName", "serverName", "gameType",
    "game", "name", "title", "identifier", "slug", "id", "key",
)
_DISPLAY_KEYS = ("displayName", "friendlyName", "gameName", "name", "label", "title")
_COUNT_KEYS = (
    "playerCount", "players", "count", "onlinePlayers", "currentPlayers",
    "player_count", "online_players", "playercount",
)
_TOTAL_KEYS = ("totalPlayers", "totalPlayerCount", "networkPlayerCount", "onlinePlayers")
_STATE_KEYS = ("gameState", "state", "status")
_METADATA_ID_KEYS = ("gameType", "identifier", "slug", "id", "key", "name", "type")
_NON_GAME_SCALAR_KEYS = {
    "page", "pages", "pagesize", "limit", "offset", "total", "totalentries",
    "timestamp", "version", "networkplayers", "totalplayers", "totalplayercount",
}


def _norm(value):
    return re.sub(r"[^a-z0-9]", "", str(value or "").lower())


def _as_int(value):
    if isinstance(value, bool):
        return None
    if isinstance(value, (int, float)):
        return int(value)
    if isinstance(value, str):
        cleaned = value.replace(",", "").strip()
        if cleaned.isdigit():
            return int(cleaned)
    return None


def _string_value(value):
    if isinstance(value, str) and value.strip():
        return value.strip()
    if isinstance(value, dict):
        for key in ("displayName", "friendlyName", "name", "label", "id", "key", "slug"):
            nested = value.get(key)
            if isinstance(nested, str) and nested.strip():
                return nested.strip()
    return None


def _first_text(record, keys):
    for key in keys:
        if key in record:
            value = _string_value(record.get(key))
            if value:
                return value
    return None


def _first_count(record, keys=_COUNT_KEYS):
    for key in keys:
        if key in record:
            value = _as_int(record.get(key))
            if value is not None:
                return max(0, value)
    return None


def _walk_records(value):
    """Yield JSON objects recursively, without requiring one exact API wrapper shape."""
    if isinstance(value, dict):
        yield value
        for child in value.values():
            if isinstance(child, (dict, list)):
                yield from _walk_records(child)
    elif isinstance(value, list):
        for child in value:
            yield from _walk_records(child)


def _metadata_names(payload):
    mapping = {}
    for record in _walk_records(payload):
        label = _first_text(record, _DISPLAY_KEYS)
        if not label:
            continue
        for key in _METADATA_ID_KEYS:
            raw = _string_value(record.get(key))
            if raw:
                mapping[_norm(raw)] = label
    return mapping


def _label(name, metadata):
    return metadata.get(_norm(name), name)


def extract_game_counts(payload, metadata=None):
    """Parse per-game counts from common paged and nested JSON response shapes."""
    metadata = metadata or {}
    totals = {}
    # Normal row objects such as {"gameType": "mixed_arcade", "playerCount": 4}.
    for record in _walk_records(payload):
        name = _first_text(record, _NAME_KEYS)
        count = _first_count(record)
        if name and count is not None:
            label = _label(name, metadata)
            key = _norm(label)
            if key:
                totals[key] = (label, totals.get(key, (label, 0))[1] + count)

    # Some API responses expose a name -> count object instead of row objects.
    excluded = {_norm(x) for x in (_TOTAL_KEYS + _COUNT_KEYS)} | _NON_GAME_SCALAR_KEYS
    if isinstance(payload, (dict, list)):
        for record in _walk_records(payload):
            for key, value in record.items():
                count = _as_int(value)
                normalized_key = _norm(key)
                if (
                    count is not None
                    and normalized_key
                    and normalized_key not in excluded
                    and normalized_key not in {_norm("data"), _norm("counts")}
                ):
                    # Only use scalar maps when the key resembles a game identifier.
                    if "_" in key or " " in key or key.isalpha():
                        label = _label(key, metadata)
                        totals.setdefault(_norm(label), (label, max(0, count)))

    rows = [{"name": name, "count": count} for name, count in totals.values()]
    rows.sort(key=lambda row: (-row["count"], row["name"].casefold()))
    return rows


def extract_mps_servers(payload, metadata=None):
    """Extract named MPS servers, player counts, and state labels from the API payload."""
    metadata = metadata or {}
    servers = []
    seen = set()
    for record in _walk_records(payload):
        name = _first_text(
            record,
            ("friendlyName", "serverName", "name", "displayName", "gameName", "gameType", "title"),
        )
        count = _first_count(record)
        if not name or count is None:
            continue
        state = _first_text(record, _STATE_KEYS)
        label = _label(name, metadata)
        identity = (_norm(label), count, _norm(state or ""))
        if identity in seen:
            continue
        seen.add(identity)
        servers.append({"name": label, "count": count, "state": state})
    servers.sort(key=lambda row: (-row["count"], row["name"].casefold()))
    return servers


def _total_players(payload, game_counts):
    for record in _walk_records(payload):
        for key in _TOTAL_KEYS:
            if key in record:
                value = _as_int(record.get(key))
                if value is not None:
                    return max(0, value)
    return sum(row["count"] for row in game_counts)


def find_mixed_arcade_count(rows, aliases=None):
    """Return the matching count, or None when no Mixed Arcade row exists."""
    aliases = aliases or ["Mixed Arcade", "mixed_arcade", "mixedarcade"]
    normalized_aliases = {_norm(alias) for alias in aliases if _norm(alias)}
    matched = [row["count"] for row in rows if _norm(row.get("name")) in normalized_aliases]
    if not matched:
        # Be tolerant of a source label with a platform suffix/prefix.
        matched = [
            row["count"] for row in rows
            if any(alias and alias in _norm(row.get("name")) for alias in normalized_aliases)
        ]
    return sum(matched) if matched else None


def _read_json(url, api_key=None):
    headers = {
        "Accept": "application/json",
        "User-Agent": "MonsterMaze-GameCounts/1.0",
    }
    if api_key:
        headers["X-API-Key"] = api_key
    request = urllib.request.Request(url, headers=headers)
    with urllib.request.urlopen(request, timeout=REQUEST_TIMEOUT) as response:
        return json.loads(response.read().decode("utf-8"))


async def _fetch_json(url, api_key=None):
    return await asyncio.to_thread(_read_json, url, api_key)


async def _get_metadata(api_base):
    global _metadata_cache, _metadata_expires_at
    now = time.monotonic()
    if now < _metadata_expires_at:
        return _metadata_cache
    try:
        payload = await _fetch_json(
            api_base.rstrip("/") + "/v1/references/games/metadata"
        )
        _metadata_cache = _metadata_names(payload)
        _metadata_expires_at = time.monotonic() + 3600
    except Exception as exc:
        # Metadata is a convenience for turning API identifiers into friendly names.
        # A failure must not block counts because those responses may include labels.
        _metadata_expires_at = time.monotonic() + 60
        _log_error("game metadata lookup failed", exc)
    return _metadata_cache


async def fetch_snapshot(cfg, api_key):
    api_base = str(cfg.get("game_counts_api_base_url", API_BASE_DEFAULT)).rstrip("/")
    counts_url = api_base + "/v1/player-counts"
    mps_url = api_base + "/v1/game-servers/mps"
    counts_payload, mps_result = await asyncio.gather(
        _fetch_json(counts_url, api_key),
        _fetch_json(mps_url, api_key),
        return_exceptions=True,
    )
    if isinstance(counts_payload, BaseException):
        raise counts_payload

    metadata = await _get_metadata(api_base)
    game_counts = extract_game_counts(counts_payload, metadata)
    if not game_counts:
        raise ValueError("Mineplex returned no parseable game-count rows")

    mps_error = None
    if isinstance(mps_result, BaseException):
        mps_servers = []
        mps_error = str(mps_result)
    else:
        mps_servers = extract_mps_servers(mps_result, metadata)

    mixed_count = find_mixed_arcade_count(
        game_counts,
        cfg.get("game_counts_mixed_arcade_names"),
    )
    return {
        "game_counts": game_counts,
        "network_players": _total_players(counts_payload, game_counts),
        "mps_servers": mps_servers,
        "mps_error": mps_error,
        "mixed_arcade_count": mixed_count,
        "updated_at": datetime.now(timezone.utc),
    }


def _line_chunks(lines, max_chars=950):
    chunks, current = [], []
    length = 0
    for line in lines:
        needed = len(line) + (1 if current else 0)
        if current and length + needed > max_chars:
            chunks.append("\n".join(current))
            current, length = [], 0
        current.append(line)
        length += len(line) + (1 if len(current) > 1 else 0)
    if current:
        chunks.append("\n".join(current))
    return chunks


def build_embed(snapshot, cfg):
    embed = discord.Embed(
        title="Mineplex Live Game Counts",
        description=f"**Network online:** {snapshot['network_players']:,} players",
        color=0x3498DB,
        timestamp=snapshot["updated_at"],
    )
    mixed_count = snapshot["mixed_arcade_count"]
    threshold = max(1, int(cfg.get("game_counts_mixed_arcade_threshold", 1)))
    if mixed_count is None:
        mixed_text = "No Mixed Arcade row is currently exposed by the API."
    elif mixed_count >= threshold:
        mixed_text = f"🟢 **ACTIVE** — {mixed_count:,} player(s)"
    else:
        mixed_text = f"⚪ Inactive — {mixed_count:,} player(s)"
    embed.add_field(name="Mixed Arcade", value=mixed_text, inline=False)

    counts = snapshot["game_counts"]
    game_lines = [f"• **{row['name']}** — {row['count']:,}" for row in counts]
    chunks = _line_chunks(game_lines)
    for index, chunk in enumerate(chunks, 1):
        title = "Players by game" if len(chunks) == 1 else f"Players by game ({index})"
        embed.add_field(name=title, value=chunk, inline=True)

    mps_servers = snapshot["mps_servers"]
    if snapshot.get("mps_error"):
        mps_text = "MPS details temporarily unavailable; public game counts are still updating."
    elif not mps_servers:
        mps_text = "No MPS server details returned."
    else:
        mps_total = sum(row["count"] for row in mps_servers)
        mps_lines = [
            f"• **{row['name']}** — {row['count']:,} player(s)" +
            (f" · {row['state']}" if row.get("state") else "")
            for row in mps_servers[:12]
        ]
        if len(mps_servers) > 12:
            mps_lines.append(f"…and {len(mps_servers) - 12} more server(s)")
        mps_text = f"**{mps_total:,} players across {len(mps_servers):,} MPS server(s)**\n" + "\n".join(mps_lines)
    embed.add_field(name="MPS — player-hosted servers", value=mps_text[:1024], inline=False)
    embed.set_footer(text="Refreshes every 30 seconds • Mineplex Public API")
    return embed


def _not_configured_embed():
    embed = discord.Embed(
        title="Mineplex Live Game Counts",
        description=(
            "⚠️ Live game-count monitoring is not configured yet. "
            "Set MINEPLEX_API_KEY in the Oracle bot service environment. "
            "The key is not stored in Git or the Discord bot config."
        ),
        color=0xE67E22,
        timestamp=datetime.now(timezone.utc),
    )
    embed.set_footer(text="Expected polling interval: 30 seconds")
    return embed


def _get_active_state():
    conn = base.db()
    try:
        conn.execute(
            "CREATE TABLE IF NOT EXISTS game_counts_state "
            "(state_key TEXT PRIMARY KEY, state_value TEXT NOT NULL)"
        )
        row = conn.execute(
            "SELECT state_value FROM game_counts_state WHERE state_key='mixed_arcade_active'"
        ).fetchone()
        conn.commit()
        if row is None:
            return None
        return row[0] == "1"
    finally:
        conn.close()


def _set_active_state(active):
    conn = base.db()
    try:
        conn.execute(
            "CREATE TABLE IF NOT EXISTS game_counts_state "
            "(state_key TEXT PRIMARY KEY, state_value TEXT NOT NULL)"
        )
        conn.execute(
            "INSERT INTO game_counts_state(state_key, state_value) VALUES('mixed_arcade_active', ?) "
            "ON CONFLICT(state_key) DO UPDATE SET state_value=excluded.state_value",
            ("1" if active else "0",),
        )
        conn.commit()
    finally:
        conn.close()


async def _publish(bot, channel, embed):
    stored = base.get_board_msg(BOARD_KEY)
    message = None
    if stored and str(stored[0]) == str(channel.id):
        try:
            message = await bot.call(
                lambda: channel.fetch_message(int(stored[1])),
                "game counts status",
            )
        except discord.NotFound:
            message = None
    try:
        if message:
            await bot.call(lambda: message.edit(embed=embed), "game counts status")
        else:
            message = await bot.call(
                lambda: channel.send(embed=embed),
                "game counts status",
            )
            base.set_board_msg(BOARD_KEY, channel.id, message.id)
    except discord.HTTPException as exc:
        _log_error("Discord status-message update failed", exc)
        raise


async def _send_mixed_arcade_alert(bot, channel, count, cfg):
    role_id = str(cfg.get("game_counts_alert_role_id", "") or "").strip()
    mention = f"<@&{role_id}>" if role_id.isdigit() else ""
    count_text = f"{count:,} player(s)" if count is not None else "players detected"
    content = f"{mention + ' ' if mention else ''}🟢 **Mixed Arcade is active!** {count_text} are currently in the game."
    await bot.call(
        lambda: channel.send(
            content=content,
            allowed_mentions=discord.AllowedMentions(everyone=False, roles=True, users=False),
        ),
        "Mixed Arcade start alert",
    )


async def update(bot, cfg):
    global _last_logged_error
    channel_ref = cfg.get("game_counts_channel", CHANNEL_DEFAULT)
    channel = bot.channel(channel_ref)
    if channel is None:
        _log_error("Discord channel not found", channel_ref)
        return

    api_key = os.environ.get("MINEPLEX_API_KEY", "").strip()
    if not api_key:
        await _publish(bot, channel, _not_configured_embed())
        return

    try:
        snapshot = await fetch_snapshot(cfg, api_key)
        await _publish(bot, channel, build_embed(snapshot, cfg))
    except asyncio.CancelledError:
        raise
    except Exception as exc:
        # Keep the last successful message visible; it includes its last-update timestamp.
        _log_error("Mineplex game-count poll failed", exc)
        return

    _last_logged_error = None
    mixed_count = snapshot["mixed_arcade_count"]
    threshold = max(1, int(cfg.get("game_counts_mixed_arcade_threshold", 1)))
    # Do not infer "inactive" from a malformed response that omitted Mixed Arcade.
    if mixed_count is None:
        return
    current_active = mixed_count >= threshold
    previous_active = _get_active_state()

    if previous_active is None:
        # Establish a baseline after deployment; do not announce a game that was
        # already active before this monitor started unless explicitly requested.
        if current_active and bool(cfg.get("game_counts_alert_initial_active", False)):
            try:
                await _send_mixed_arcade_alert(bot, channel, mixed_count, cfg)
            except Exception as exc:
                _log_error("initial Mixed Arcade alert failed", exc)
                return
        _set_active_state(current_active)
    elif previous_active != current_active:
        if current_active:
            try:
                await _send_mixed_arcade_alert(bot, channel, mixed_count, cfg)
            except asyncio.CancelledError:
                raise
            except Exception as exc:
                _log_error("Mixed Arcade start alert failed; will retry next poll", exc)
                return
        _set_active_state(current_active)


async def run(bot, cfg):
    """Long-lived polling task; errors never crash the Discord client."""
    interval = max(30, int(cfg.get("game_counts_interval", INTERVAL_DEFAULT)))
    while True:
        try:
            await update(bot, cfg)
        except asyncio.CancelledError:
            raise
        except Exception as exc:
            _log_error("game-count updater failed", exc)
        await asyncio.sleep(interval)


def _log_error(message, exc):
    global _last_logged_error
    text = f"[game-counts] {message}: {exc}"
    if text != _last_logged_error:
        print(text, flush=True)
        _last_logged_error = text
