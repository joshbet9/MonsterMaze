"""Monster Maze SOLO Discord leaderboard bot.

Keeps Minecraft 1.8 and 1.21 PBs separate, scans complete feed history,
coalesces live leaderboard updates, and retries transient Discord failures.
"""

import asyncio
import json
import os
import re
import sqlite3

import discord

HERE = os.path.dirname(os.path.abspath(__file__))
CFG = os.path.join(HERE, "config.json")
DB = os.path.join(HERE, "leaderboard.db")

PLATFORMS = ("1.8", "1.21")
PLATFORM_LABELS = {"1.8": "Minecraft 1.8.9", "1.21": "Minecraft 1.21.11"}
KITS = ["Jumper", "Slowball", "Body Builder", "Repulsor", "Maverick"]
PATTERNS = 3
MAX_HTTP_RETRIES = 5
REFRESH_DELAY = 2.0


def load_config():
    with open(CFG, "r", encoding="utf-8") as fh:
        return json.load(fh)


def db():
    conn = sqlite3.connect(DB)
    conn.execute("PRAGMA journal_mode=WAL")
    _migrate_runs(conn)
    conn.execute("CREATE TABLE IF NOT EXISTS boards (board_key TEXT PRIMARY KEY, channel_id TEXT, msg_id TEXT)")
    _migrate_board_keys(conn)
    return conn


def _migrate_runs(conn):
    columns = [row[1] for row in conn.execute("PRAGMA table_info(runs)").fetchall()]
    if not columns:
        conn.execute(
            "CREATE TABLE runs (platform TEXT NOT NULL, mode TEXT NOT NULL, pattern INTEGER NOT NULL, "
            "kit TEXT NOT NULL, uuid TEXT NOT NULL, name TEXT, stage INTEGER NOT NULL, time_ms INTEGER NOT NULL, "
            "PRIMARY KEY (platform, mode, pattern, kit, uuid))"
        )
        conn.commit()
        return
    if "platform" in columns:
        return
    conn.execute("ALTER TABLE runs RENAME TO runs_legacy")
    conn.execute(
        "CREATE TABLE runs (platform TEXT NOT NULL, mode TEXT NOT NULL, pattern INTEGER NOT NULL, "
        "kit TEXT NOT NULL, uuid TEXT NOT NULL, name TEXT, stage INTEGER NOT NULL, time_ms INTEGER NOT NULL, "
        "PRIMARY KEY (platform, mode, pattern, kit, uuid))"
    )
    conn.execute(
        "INSERT INTO runs (platform, mode, pattern, kit, uuid, name, stage, time_ms) "
        "SELECT '1.8', mode, pattern, kit, uuid, name, stage, time_ms FROM runs_legacy"
    )
    conn.execute("DROP TABLE runs_legacy")
    conn.commit()
    print("Migrated existing leaderboard runs to platform-aware schema; legacy rows treated as 1.8.")


def _migrate_board_keys(conn):
    rows = conn.execute("SELECT board_key FROM boards").fetchall()
    for (key,) in rows:
        if not key.startswith("1.8|") and not key.startswith("1.21|"):
            conn.execute("UPDATE boards SET board_key=? WHERE board_key=?", ("1.8|" + key, key))
    conn.commit()


def upsert_run(run):
    required = ("platform", "mode", "pattern", "kit", "uuid", "name", "stage")
    if any(key not in run for key in required):
        return False
    if run["platform"] not in PLATFORMS or not run["uuid"]:
        return False
    key = (run["platform"], run["mode"], run["pattern"], run["kit"], run["uuid"])
    conn = db()
    current = conn.execute(
        "SELECT stage FROM runs WHERE platform=? AND mode=? AND pattern=? AND kit=? AND uuid=?", key
    ).fetchone()
    if current and run["stage"] <= current[0]:
        conn.close()
        return False
    conn.execute(
        "INSERT INTO runs (platform, mode, pattern, kit, uuid, name, stage, time_ms) VALUES (?, ?, ?, ?, ?, ?, ?, ?) "
        "ON CONFLICT(platform, mode, pattern, kit, uuid) DO UPDATE SET name=excluded.name, stage=excluded.stage, time_ms=excluded.time_ms",
        (run["platform"], run["mode"], run["pattern"], run["kit"], run["uuid"], run["name"], run["stage"], run.get("time_ms", 0)),
    )
    conn.commit()
    conn.close()
    return True


def _rows(where, params, top_n):
    conn = db()
    rows = conn.execute(
        "WITH ranked_runs AS (SELECT name, kit, stage, time_ms, "
        "ROW_NUMBER() OVER (PARTITION BY uuid ORDER BY stage DESC, time_ms ASC, kit ASC, name ASC) rn "
        "FROM runs " + where + ") "
        "SELECT name, kit, stage FROM ranked_runs WHERE rn=1 ORDER BY stage DESC, time_ms ASC, name ASC LIMIT ?",
        tuple(params) + (top_n,),
    ).fetchall()
    conn.close()
    return rows


def overall_board(platform, mode, top_n):
    return _rows("WHERE platform=? AND mode=?", [platform, mode], top_n)


def pattern_board(platform, mode, pattern, top_n):
    return _rows("WHERE platform=? AND mode=? AND pattern=?", [platform, mode, pattern], top_n)


def kit_board(platform, mode, pattern, kit, top_n):
    return _rows("WHERE platform=? AND mode=? AND pattern=? AND kit=?", [platform, mode, pattern, kit], top_n)


def get_board_msg(board_key):
    conn = db()
    row = conn.execute("SELECT channel_id, msg_id FROM boards WHERE board_key=?", (board_key,)).fetchone()
    conn.close()
    return row


def set_board_msg(board_key, channel_id, msg_id):
    conn = db()
    conn.execute(
        "INSERT INTO boards (board_key, channel_id, msg_id) VALUES (?, ?, ?) "
        "ON CONFLICT(board_key) DO UPDATE SET channel_id=excluded.channel_id, msg_id=excluded.msg_id",
        (board_key, str(channel_id), str(msg_id)),
    )
    conn.commit()
    conn.close()


def parse_embed(embed):
    title = embed.title or ""
    match = re.search(r"new PB \(stage (\d+)\)", title, re.IGNORECASE)
    if not match:
        return None
    fields = {field.name.strip().lower(): field.value.strip() for field in embed.fields}
    mode = fields.get("mode")
    pattern_text = fields.get("pattern")
    kit = fields.get("kit")
    if not mode or not pattern_text or not kit:
        return None
    platform_text = fields.get("minecraft", "")
    if platform_text.startswith("1.8"):
        platform = "1.8"
    elif platform_text.startswith("1.21"):
        platform = "1.21"
    else:
        footer = embed.footer.text if embed.footer and embed.footer.text else ""
        platform_match = re.search(r"platform\s+(1\.8|1\.21)", footer, re.IGNORECASE)
        platform = platform_match.group(1) if platform_match else "1.8"
    pattern_match = re.search(r"Maze\s+(\d+)", pattern_text, re.IGNORECASE)
    if not pattern_match:
        return None
    pattern = int(pattern_match.group(1)) - 1
    if pattern < 0 or pattern >= PATTERNS:
        return None
    time_ms = 0
    time_match = re.search(r"(\d+)m\s+(\d+)s", fields.get("time", "0m 0s"))
    if time_match:
        time_ms = int(time_match.group(1)) * 60000 + int(time_match.group(2)) * 1000
    footer = embed.footer.text if embed.footer and embed.footer.text else ""
    uuid_match = re.search(r"uuid\s+([0-9a-f-]{8,})", footer, re.IGNORECASE)
    if not uuid_match:
        return None
    return {
        "name": title.split(" - new PB", 1)[0].strip()[:256],
        "platform": platform,
        "mode": mode.lower()[:64],
        "pattern": pattern,
        "kit": kit[:64],
        "stage": int(match.group(1)),
        "time_ms": time_ms,
        "uuid": uuid_match.group(1).lower(),
    }


class MonsterBot(discord.Client):
    def __init__(self, cfg):
        intents = discord.Intents.default()
        intents.message_content = True
        super().__init__(intents=intents)
        self.cfg = cfg
        self.top_n = max(1, min(int(cfg.get("top_n", 10)), 25))
        default_modes = list(cfg.get("modes", ["modern"]))
        self.platform_modes = {
            platform: list(cfg.get("platform_modes", {}).get(platform, default_modes))
            for platform in PLATFORMS
        }
        self.refresh_tasks = {}
        self.rebuild_lock = asyncio.Lock()
        self.ready_once = False

    def resolve_channel(self, ref):
        if ref is None or ref == "":
            return None
        try:
            return self.get_channel(int(ref))
        except (ValueError, TypeError):
            pass
        for guild in self.guilds:
            channel = discord.utils.get(guild.text_channels, name=str(ref))
            if channel:
                return channel
        return None

    def feed_channels(self):
        return [channel for ref in self.cfg.get("feed_channels", []) if (channel := self.resolve_channel(ref))]

    def platform_configs(self):
        configured = self.cfg.get("channels", {})
        if any(key in PLATFORMS for key in configured):
            return configured
        return {"1.8": configured}

    def mode_channels(self, platform, mode):
        return self.platform_configs().get(platform, {}).get(mode, {})

    def schedule_refresh(self, platform, mode):
        key = (platform, mode)
        if key not in self.refresh_tasks or self.refresh_tasks[key].done():
            self.refresh_tasks[key] = asyncio.create_task(self._delayed_refresh(platform, mode))

    async def _delayed_refresh(self, platform, mode):
        await asyncio.sleep(REFRESH_DELAY)
        try:
            await self.refresh_all_boards(platform, mode)
        except Exception as exc:
            print(f"live board refresh failed for {platform}/{mode}: {exc!r}")

    async def discord_call(self, operation, label):
        delay = 1.0
        for attempt in range(MAX_HTTP_RETRIES + 1):
            try:
                return await operation()
            except discord.HTTPException as exc:
                if attempt >= MAX_HTTP_RETRIES or exc.status not in (429, 500, 502, 503, 504):
                    raise
                retry_after = getattr(exc, "retry_after", None)
                wait = float(retry_after) if retry_after else delay
                print(f"Discord {exc.status} for {label}; retrying in {min(wait, 30.0):.1f}s ({attempt + 1}/{MAX_HTTP_RETRIES})")
                await asyncio.sleep(min(wait, 30.0))
                delay = min(delay * 2.0, 30.0)

    async def rebuild_all(self):
        async with self.rebuild_lock:
            seen = 0
            accepted = 0
            for channel in self.feed_channels():
                async for message in channel.history(limit=None, oldest_first=False):
                    for embed in message.embeds:
                        run = parse_embed(embed)
                        if not run:
                            continue
                        seen += 1
                        if upsert_run(run):
                            accepted += 1
            print(f"rescanned {seen} PB submissions ({accepted} database updates)")
            for platform in PLATFORMS:
                for mode in self.platform_modes.get(platform, []):
                    if self.mode_channels(platform, mode):
                        await self.refresh_all_boards(platform, mode)

    async def on_ready(self):
        print(f"Logged in as {self.user} (id {self.user.id})")
        if self.ready_once:
            print("Reconnected; keeping existing leaderboard state.")
            return
        self.ready_once = True
        try:
            await self.rebuild_all()
            print("Ready. Standings up to date.")
        except Exception as exc:
            print(f"initial rebuild failed: {exc!r}")

    async def on_message(self, message):
        if message.author == self.user:
            return
        if message.content.strip().lower() == "!rebuild":
            await message.channel.send("Rebuilding standings from complete feed history...")
            try:
                await self.rebuild_all()
                await message.channel.send("Done.")
            except Exception as exc:
                print(f"manual rebuild failed: {exc!r}")
                await message.channel.send(f"Rebuild failed: `{str(exc)[:1800]}`")
            return
        feed_refs = {str(ref) for ref in self.cfg.get("feed_channels", [])}
        if str(message.channel.id) not in feed_refs and message.channel.name not in feed_refs:
            return
        for embed in message.embeds:
            run = parse_embed(embed)
            if run and upsert_run(run):
                self.schedule_refresh(run["platform"], run["mode"])

    def _lines(self, rows):
        lines = []
        for i, (name, kit, stage) in enumerate(rows, 1):
            medal = {1: ":first_place:", 2: ":second_place:", 3: ":third_place:"}.get(i, f"{i}.")
            lines.append(f"{medal} **{name}** — stage {stage}" + (f" ({kit})" if kit else ""))
        return lines or ["No runs yet."]

    def overall_embed(self, platform, mode):
        embed = discord.Embed(title=f"{mode.capitalize()} — {PLATFORM_LABELS[platform]} — Overall", color=0x33AA66)
        embed.add_field(name="Top Stages (all patterns/kits)", value="\n".join(self._lines(overall_board(platform, mode, self.top_n))), inline=False)
        return embed

    def pattern_embed(self, platform, mode, pattern):
        embed = discord.Embed(title=f"{mode.capitalize()} — {PLATFORM_LABELS[platform]} — Maze {pattern + 1}", color=0x33AA66)
        embed.add_field(name="Top Stages (all kits)", value="\n".join(self._lines(pattern_board(platform, mode, pattern, self.top_n))), inline=False)
        return embed

    def kit_embed(self, platform, mode, pattern, kit):
        embed = discord.Embed(title=f"{mode.capitalize()} — {PLATFORM_LABELS[platform]} — Maze {pattern + 1} — {kit}", color=0x33AA66)
        embed.add_field(name=f"Top {kit} Stages", value="\n".join(self._lines(kit_board(platform, mode, pattern, kit, self.top_n))), inline=False)
        return embed

    async def refresh_all_boards(self, platform, mode):
        channels = self.mode_channels(platform, mode)
        if not channels:
            return
        overall_ref = channels.get("overall")
        patterns_ref = channels.get("patterns")
        kits_ref = channels.get("kits")
        if overall_ref:
            await self._post_or_edit(f"{platform}|{mode}|overall", overall_ref, self.overall_embed(platform, mode), f"{platform} overall {mode}")
        if patterns_ref:
            for pattern in range(PATTERNS):
                await self._post_or_edit(f"{platform}|{mode}|p{pattern}", patterns_ref, self.pattern_embed(platform, mode, pattern), f"{platform} {mode} pattern {pattern + 1}")
        if kits_ref:
            for pattern in range(PATTERNS):
                for kit in KITS:
                    await self._post_or_edit(f"{platform}|{mode}|p{pattern}|{kit}", kits_ref, self.kit_embed(platform, mode, pattern, kit), f"{platform} {mode} p{pattern + 1} {kit}")

    async def _post_or_edit(self, board_key, channel_ref, embed, label):
        channel = self.resolve_channel(channel_ref)
        if channel is None:
            print(f"channel not found for board {label} (ref {channel_ref})")
            return
        stored = get_board_msg(board_key)
        message = None
        if stored:
            try:
                message = await self.discord_call(lambda: channel.fetch_message(int(stored[1])), f"fetch {label}")
            except discord.NotFound:
                message = None
            except discord.HTTPException as exc:
                print(f"failed to fetch board {label}: {exc}")
                return
        if message is not None:
            try:
                await self.discord_call(lambda: message.edit(embed=embed), f"edit {label}")
                print(f"edited {label}")
                return
            except discord.NotFound:
                pass
            except discord.HTTPException as exc:
                print(f"failed to edit board {label}: {exc}")
                return
        try:
            new_message = await self.discord_call(lambda: channel.send(embed=embed), f"post {label}")
            try:
                await self.discord_call(lambda: new_message.pin(), f"pin {label}")
            except discord.HTTPException as exc:
                print(f"failed to pin {label}: {exc}")
            set_board_msg(board_key, channel.id, new_message.id)
            print(f"posted+pinned {label}")
        except discord.HTTPException as exc:
            print(f"failed to post {label}: {exc}")


def main():
    if not os.path.exists(CFG):
        print(f"Missing config.json - copy config.example.json and edit it. ({CFG})")
        return
    cfg = load_config()
    MonsterBot(cfg).run(cfg["token"])


if __name__ == "__main__":
    main()
