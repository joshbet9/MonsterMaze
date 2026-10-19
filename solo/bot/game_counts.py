"""Persistent PlexTrends live-count watcher and Mixed Arcade alerts."""
import asyncio
import gzip
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


def build_embed(snapshot, cfg):
    mixed_count = int(snapshot["mixed_arcade_count"])
    if mixed_count > 0:
        description = f"🟢 **{mixed_count:,} player(s)** currently in Mixed Arcade"
        color = 0x2ECC71
    else:
        description = "⚪ **0 players** currently listed in Mixed Arcade"
        color = 0x95A5A6

    embed = discord.Embed(
        title="Mixed Arcade — Live Player Count",
        description=description,
        color=color,
        timestamp=snapshot["updated_at"],
        url=snapshot.get("source", PAGE_URL_DEFAULT),
    )
    embed.add_field(
        name="Personal alerts",
        value="Use `/mixedarcade alert player_count:<number>` to get a DM when the count reaches your chosen threshold.",
        inline=False,
    )
    embed.set_footer(text="Polls every 30 seconds • Source: PlexTrends")
    return embed


def _ensure_state_table(conn):
    conn.execute(
        "CREATE TABLE IF NOT EXISTS game_counts_state "
        "(state_key TEXT PRIMARY KEY, state_value TEXT NOT NULL)"
    )


def _get_state(key):
    conn = base.db()
    try:
        _ensure_state_table(conn)
        row = conn.execute(
            "SELECT state_value FROM game_counts_state WHERE state_key=?", (key,)
        ).fetchone()
        conn.commit()
        return None if row is None else row[0]
    finally:
        conn.close()


def _set_state(key, value):
    conn = base.db()
    try:
        _ensure_state_table(conn)
        conn.execute(
            "INSERT INTO game_counts_state(state_key, state_value) VALUES(?, ?) "
            "ON CONFLICT(state_key) DO UPDATE SET state_value=excluded.state_value",
            (key, str(value)),
        )
        conn.commit()
    finally:
        conn.close()


def _save_latest_count(count, updated_at):
    _set_state("mixed_arcade_count", int(count))
    _set_state("mixed_arcade_checked_at", int(updated_at.timestamp()))


def latest_count():
    count = _get_state("mixed_arcade_count")
    checked_at = _get_state("mixed_arcade_checked_at")
    return (
        int(count) if count is not None else None,
        int(checked_at) if checked_at is not None else None,
    )


def _ensure_alert_table(conn):
    conn.execute(
        "CREATE TABLE IF NOT EXISTS mixed_arcade_alerts ("
        "user_id TEXT PRIMARY KEY, threshold INTEGER NOT NULL, "
        "state TEXT NOT NULL DEFAULT 'armed', updated_at INTEGER NOT NULL, "
        "last_notified_at INTEGER)"
    )


def set_alert_subscription(user_id, threshold):
    now = int(datetime.now(timezone.utc).timestamp())
    conn = base.db()
    try:
        _ensure_alert_table(conn)
        conn.execute(
            "INSERT INTO mixed_arcade_alerts(user_id, threshold, state, updated_at, last_notified_at) "
            "VALUES(?, ?, 'armed', ?, NULL) "
            "ON CONFLICT(user_id) DO UPDATE SET threshold=excluded.threshold, "
            "state='armed', updated_at=excluded.updated_at, last_notified_at=NULL",
            (str(user_id), int(threshold), now),
        )
        conn.commit()
    finally:
        conn.close()


def remove_alert_subscription(user_id):
    conn = base.db()
    try:
        _ensure_alert_table(conn)
        cur = conn.execute("DELETE FROM mixed_arcade_alerts WHERE user_id=?", (str(user_id),))
        conn.commit()
        return cur.rowcount > 0
    finally:
        conn.close()


def get_alert_subscription(user_id):
    conn = base.db()
    try:
        _ensure_alert_table(conn)
        row = conn.execute(
            "SELECT threshold, state, last_notified_at FROM mixed_arcade_alerts WHERE user_id=?",
            (str(user_id),),
        ).fetchone()
        conn.commit()
        return None if row is None else {
            "threshold": int(row[0]), "state": row[1], "last_notified_at": row[2]
        }
    finally:
        conn.close()


def _get_alert_subscribers():
    conn = base.db()
    try:
        _ensure_alert_table(conn)
        rows = conn.execute(
            "SELECT user_id, threshold, state FROM mixed_arcade_alerts"
        ).fetchall()
        conn.commit()
        return [
            {"user_id": row[0], "threshold": int(row[1]), "state": row[2]}
            for row in rows
        ]
    finally:
        conn.close()


def _set_alert_state(user_id, state, notified_at=None):
    conn = base.db()
    try:
        _ensure_alert_table(conn)
        if notified_at is None:
            conn.execute(
                "UPDATE mixed_arcade_alerts SET state=?, updated_at=? WHERE user_id=?",
                (state, int(datetime.now(timezone.utc).timestamp()), str(user_id)),
            )
        else:
            conn.execute(
                "UPDATE mixed_arcade_alerts SET state=?, updated_at=?, last_notified_at=? WHERE user_id=?",
                (state, int(datetime.now(timezone.utc).timestamp()), notified_at, str(user_id)),
            )
        conn.commit()
    finally:
        conn.close()


def _active_alert_threshold_hint():
    conn = base.db()
    try:
        _ensure_alert_table(conn)
        rows = conn.execute(
            "SELECT threshold, COUNT(*) FROM mixed_arcade_alerts "
            "GROUP BY threshold ORDER BY threshold"
        ).fetchall()
        conn.commit()
    finally:
        conn.close()
    if not rows:
        return None
    return " • ".join(
        f"{count} subscriber(s) at {threshold}+ player(s)"
        for threshold, count in rows
    )


async def notify_alert_subscribers(bot, count):
    now = int(datetime.now(timezone.utc).timestamp())
    for subscription in _get_alert_subscribers():
        user_id = subscription["user_id"]
        threshold = subscription["threshold"]
        state = subscription["state"]

        if count < threshold:
            if state != "armed":
                _set_alert_state(user_id, "armed")
            continue
        if state != "armed":
            continue

        try:
            user = await bot.call(
                lambda user_id=user_id: bot.fetch_user(int(user_id)),
                "Mixed Arcade alert recipient",
            )
            channel = bot.channel(bot.cfg.get("game_counts_channel", CHANNEL_DEFAULT))
            channel_hint = f" View the live count in <#{channel.id}>." if channel else ""
            await bot.call(
                lambda user=user, threshold=threshold, channel_hint=channel_hint: user.send(
                    f"🔔 Mineplex Mixed Arcade has reached **{count:,} players** "
                    f"(your alert threshold: **{threshold}**)." + channel_hint
                ),
                "Mixed Arcade player-count alert",
            )
            _set_alert_state(user_id, "triggered", now)
        except discord.Forbidden:
            # Avoid retrying a permanently closed DM every 30 seconds. The user
            # can re-arm the alert with /mixedarcade alert after enabling DMs.
            _set_alert_state(user_id, "blocked")
            print(
                f"[game-counts] cannot DM user {user_id}; ask them to enable DMs "
                "and reconfigure /mixedarcade alert",
                flush=True,
            )
        except (discord.NotFound, discord.HTTPException) as exc:
            # Transient Discord errors leave the alert armed for retry next poll.
            _log_error(f"could not deliver Mixed Arcade alert to {user_id}", exc)


class MixedArcadeCommands(discord.app_commands.Group):
    def __init__(self):
        super().__init__(
            name="mixedarcade",
            description="Track Mixed Arcade player counts and configure alerts",
        )

    @discord.app_commands.command(
        name="alert", description="DM me when Mixed Arcade reaches a player count"
    )
    @discord.app_commands.describe(player_count="Alert me at this number of players (1–1000)")
    async def alert(
        self,
        interaction: discord.Interaction,
        player_count: discord.app_commands.Range[int, 1, 1000],
    ):
        set_alert_subscription(interaction.user.id, player_count)
        count, _ = latest_count()
        message = (
            f"✅ I'll DM you when Mixed Arcade reaches **{player_count} players**. "
            "After the count falls below your threshold, the alert re-arms for the next game."
        )
        if count is not None and count >= player_count:
            message += " The count is already at or above that level, so you should be alerted on the next poll."
        message += " Make sure this bot is allowed to send you DMs."
        await interaction.response.send_message(message, ephemeral=True)

    @discord.app_commands.command(
        name="remove", description="Remove my Mixed Arcade player-count alert"
    )
    async def remove(self, interaction: discord.Interaction):
        removed = remove_alert_subscription(interaction.user.id)
        message = (
            "Your Mixed Arcade alert has been removed."
            if removed else "You don't have a Mixed Arcade alert configured."
        )
        await interaction.response.send_message(message, ephemeral=True)

    @discord.app_commands.command(
        name="status", description="Show the latest Mixed Arcade count and my alert"
    )
    async def status(self, interaction: discord.Interaction):
        count, checked_at = latest_count()
        if count is None:
            status = "No successful PlexTrends reading yet."
        else:
            status = f"**Mixed Arcade:** {count:,} player(s)"
            if checked_at:
                status += f" • Last checked <t:{checked_at}:R>"
        subscription = get_alert_subscription(interaction.user.id)
        if subscription:
            threshold = subscription["threshold"]
            state = subscription["state"]
            if state == "armed":
                extra = f"Your alert is armed at **{threshold}+ players**."
            elif state == "triggered":
                extra = f"Your **{threshold}+ player** alert has fired; it re-arms once the count falls below {threshold}."
            else:
                extra = f"Your **{threshold}+ player** alert couldn't be delivered by DM. Enable DMs and run `/mixedarcade alert` again."
            status += "\\n" + extra
        else:
            status += "\\nYou have no alert configured. Use `/mixedarcade alert` to subscribe."
        await interaction.response.send_message(status, ephemeral=True)

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


async def update(bot, cfg):
    global _last_logged_error
    try:
        snapshot = await fetch_snapshot(cfg)
        mixed_count = snapshot.get("mixed_arcade_count")
        if mixed_count is None:
            raise ValueError("PlexTrends snapshot did not include a Mixed Arcade count")
        checked_at = snapshot.get("updated_at") or datetime.now(timezone.utc)
        _save_latest_count(mixed_count, checked_at)
        await notify_alert_subscribers(bot, int(mixed_count))
    except asyncio.CancelledError:
        raise
    except Exception as exc:
        # Keep the last successful count and each user's armed state on scrape failure.
        _log_error("PlexTrends poll failed", exc)
        return

    _last_logged_error = None
    channel_ref = cfg.get("game_counts_channel", CHANNEL_DEFAULT)
    channel = bot.channel(channel_ref)
    if channel is None:
        _log_error("Discord channel not found", channel_ref)
        return

    try:
        await _publish(bot, channel, build_embed(snapshot, cfg))
    except asyncio.CancelledError:
        raise
    except Exception as exc:
        _log_error("Mixed Arcade channel update failed", exc)



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
