"""Persistent PlexTrends live-count watcher and Mixed Arcade alerts."""
import asyncio
import gzip
import os
import urllib.error
import urllib.request
from datetime import datetime, timezone

import discord
import monster_bot_v2 as base
from plextrends_scraper import parse_live_counts


PAGE_URL_DEFAULT = "https://plextrends.com/"
CHANNEL_DEFAULT = "game-counts"
INTERVAL_DEFAULT = 30
BOARD_KEY = "game-counts"
REQUEST_TIMEOUT = 12
MAX_PAGE_BYTES = 20 * 1024 * 1024

_cached_html = None
_cached_etag = None
_cached_last_modified = None
_last_logged_error = None


def _read_page(url):
    """Fetch PlexTrends using conditional requests to avoid re-downloading unchanged pages."""
    global _cached_html, _cached_etag, _cached_last_modified
    headers = {
        "Accept": "text/html,application/xhtml+xml",
        "Accept-Encoding": "gzip",
        "Cache-Control": "no-cache",
        "Pragma": "no-cache",
        "User-Agent": "MonsterMaze-GameCounts/1.0 (+https://github.com/joshbet9/MonsterMaze)",
    }
    if _cached_etag:
        headers["If-None-Match"] = _cached_etag
    if _cached_last_modified:
        headers["If-Modified-Since"] = _cached_last_modified
    request = urllib.request.Request(url, headers=headers)
    try:
        with urllib.request.urlopen(request, timeout=REQUEST_TIMEOUT) as response:
            body = response.read(MAX_PAGE_BYTES + 1)
            if len(body) > MAX_PAGE_BYTES:
                raise ValueError("PlexTrends page exceeded the 20 MiB safety limit")
            if response.headers.get("Content-Encoding", "").lower() == "gzip":
                body = gzip.decompress(body)
            html = body.decode("utf-8", "replace")
            _cached_html = html
            _cached_etag = response.headers.get("ETag")
            _cached_last_modified = response.headers.get("Last-Modified")
            return html
    except urllib.error.HTTPError as exc:
        if exc.code == 304 and _cached_html is not None:
            return _cached_html
        raise


async def fetch_snapshot(cfg):
    url = str(cfg.get("game_counts_page_url", PAGE_URL_DEFAULT)).strip()
    if not url.lower().startswith("https://"):
        raise ValueError("game_counts_page_url must be an HTTPS URL")
    html = await asyncio.to_thread(_read_page, url)
    snapshot = parse_live_counts(html)
    snapshot["source"] = url
    snapshot["updated_at"] = datetime.now(timezone.utc)
    return snapshot


def _chunks(lines, max_chars=950):
    chunks = []
    current = []
    length = 0
    for line in lines:
        needed = len(line) + (1 if current else 0)
        if current and length + needed > max_chars:
            chunks.append("\n".join(current))
            current = []
            length = 0
        current.append(line)
        length += len(line) + (1 if len(current) > 1 else 0)
    if current:
        chunks.append("\n".join(current))
    return chunks


def build_embed(snapshot):
    embed = discord.Embed(
        title="Mineplex Live Game Counts",
        description=f"**Network online:** {snapshot['network_players']:,} players",
        color=0x3498DB,
        timestamp=snapshot["updated_at"],
        url=snapshot.get("source", PAGE_URL_DEFAULT),
    )
    mixed_count = snapshot["mixed_arcade_count"]
    if mixed_count > 0:
        mixed_text = f"🟢 **ACTIVE** — {mixed_count:,} player(s)"
    else:
        mixed_text = "⚪ Inactive — 0 players"
    embed.add_field(name="Mixed Arcade", value=mixed_text, inline=False)

    rows = snapshot["game_counts"]
    chunks = _chunks([f"• **{row['name']}** — {row['count']:,}" for row in rows])
    if not chunks:
        chunks = ["No per-game counts were listed."]
    for index, chunk in enumerate(chunks, 1):
        title = "Players by game" if len(chunks) == 1 else f"Players by game ({index})"
        embed.add_field(name=title, value=chunk, inline=True)

    embed.add_field(
        name="MPS server details",
        value=(
            "PlexTrends' public live dashboard does not expose the per-MPS-server "
            "breakdown shown by Plexify /gamecounts. Network and per-game counts "
            "are monitored here; Mixed Arcade start alerts remain enabled."
        ),
        inline=False,
    )
    embed.set_footer(text="Refreshes every 30 seconds • Source: PlexTrends")
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
        return None if row is None else row[0] == "1"
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

    if message:
        if message.embeds and message.embeds[0].to_dict() != embed.to_dict():
            await bot.call(lambda: message.edit(embed=embed), "game counts status")
    else:
        message = await bot.call(lambda: channel.send(embed=embed), "game counts status")
        base.set_board_msg(BOARD_KEY, channel.id, message.id)


async def _send_mixed_arcade_alert(bot, channel, count, cfg):
    role_id = str(cfg.get("game_counts_alert_role_id", "") or "").strip()
    mention = f"<@&{role_id}>" if role_id.isdigit() else ""
    content = (
        f"{mention + ' ' if mention else ''}🟢 **Mixed Arcade is active!** "
        f"{count:,} player(s) detected on Mineplex."
    )
    await bot.call(
        lambda: channel.send(
            content=content,
            allowed_mentions=discord.AllowedMentions(
                everyone=False, roles=True, users=False
            ),
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

    try:
        snapshot = await fetch_snapshot(cfg)
        await _publish(bot, channel, build_embed(snapshot))
    except asyncio.CancelledError:
        raise
    except Exception as exc:
        # Keep the last successful Discord message and alert state intact on scrape failure.
        _log_error("PlexTrends poll failed", exc)
        return

    _last_logged_error = None
    mixed_count = snapshot.get("mixed_arcade_count")
    if mixed_count is None:
        return
    current_active = mixed_count >= max(1, int(cfg.get("game_counts_mixed_arcade_threshold", 1)))
    previous_active = _get_active_state()

    if previous_active is None:
        # Baseline only; avoid announcing a game that started before deployment.
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
                _log_error("Mixed Arcade alert failed; will retry next poll", exc)
                return
        _set_active_state(current_active)


async def run(bot, cfg):
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
