#!/usr/bin/env python3
"""
Monster Maze SOLO - Discord leaderboard bot.

Watches the PB feed channel(s), parses each posted "new PB" embed, stores the
best (mode, pattern, kit, player) in SQLite, and maintains a ranked standings
message per (mode, pattern) in each mode's leaderboard channel.

Deployment (cloud host, e.g. a free-tier VPS):
    cd bot
    pip install -r requirements.txt
    cp config.example.json config.json   # fill in token + channel ids
    python monster_bot.py                # run under a process manager (systemd/tmux)

Prereqs on Discord:
  * A bot application (https://discord.com/developers/applications -> New Application
    -> Bot -> copy token). Enable the **Message Content Intent** (Privileged Gateways).
  * Invite the bot to your server with Send Messages, Embed Links, Read Messages,
    Read Message History, and Manage Messages (to pin/edit the standings).

Commands (in any channel the bot can see):
    !rebuild   rescans feed history and refreshes all standings.

Files:
    config.example.json  -> copy to config.json
    monster_bot.py       -> the bot
    requirements.txt     -> pip install -r requirements.txt
    leaderboard.db       -> SQLite store (auto-created)
"""

import json
import os
import re
import sqlite3

import discord

HERE = os.path.dirname(os.path.abspath(__file__))
CFG = os.path.join(HERE, "config.json")
DB = os.path.join(HERE, "leaderboard.db")

MODES = ["modern", "speed", "lagless", "original"]
PATTERNS = 3


def load_config():
    with open(CFG, "r", encoding="utf-8") as fh:
        return json.load(fh)


def db():
    conn = sqlite3.connect(DB)
    conn.execute(
        "CREATE TABLE IF NOT EXISTS runs ("
        "mode TEXT, pattern INTEGER, kit TEXT, uuid TEXT, name TEXT, "
        "stage INTEGER, time_ms INTEGER, pattern_name TEXT, kit_name TEXT, "
        "PRIMARY KEY (mode, pattern, kit, uuid))"
    )
    conn.execute(
        "CREATE TABLE IF NOT EXISTS standings ("
        "mode TEXT, pattern INTEGER, kit TEXT, channel_id TEXT, msg_id TEXT, "
        "pattern_name TEXT, kit_name TEXT, "
        "PRIMARY KEY (mode, pattern, kit))"
    )
    # Add new columns for hierarchy support
    try:
        conn.execute("ALTER TABLE runs ADD COLUMN pattern_name TEXT")
        conn.execute("ALTER TABLE runs ADD COLUMN kit_name TEXT")
    except sqlite3.OperationalError:
        # Columns already exist
        pass
    try:
        conn.execute("ALTER TABLE standings ADD COLUMN pattern_name TEXT")
        conn.execute("ALTER TABLE standings ADD COLUMN kit_name TEXT")
    except sqlite3.OperationalError:
        # Columns already exist
        pass
    return conn


def upsert_run(run):
    """Record a run; returns True if it set a new PB (changed)."""
    key = (run["mode"], run["pattern"], run["kit"], run["uuid"])
    c = db()
    cur = c.execute(
        "SELECT stage FROM runs WHERE mode=? AND pattern=? AND kit=? AND uuid=?",
        key,
    ).fetchone()
    if cur and run["stage"] <= cur[0]:
        c.close()
        return False
    c.execute(
        "INSERT INTO runs (mode, pattern, kit, uuid, name, stage, time_ms) "
        "VALUES (?,?,?,?,?,?,?) "
        "ON CONFLICT(mode, pattern, kit, uuid) DO UPDATE SET "
        "name=excluded.name, stage=excluded.stage, time_ms=excluded.time_ms",
        (run["mode"], run["pattern"], run["kit"], run["uuid"],
         run["name"], run["stage"], run.get("time_ms", 0)),
    )
    c.commit()
    c.close()
    return True


def board(mode, pattern, kit, top_n):
    c = db()
    rows = c.execute(
        "SELECT name, MAX(stage) AS best FROM runs "
        "WHERE mode=? AND pattern=? AND kit=? "
        "GROUP BY uuid ORDER BY best DESC, MIN(time_ms) ASC LIMIT ?",
        (mode, pattern, kit, top_n),
    ).fetchall()
    c.close()
    return rows


def kits_for(mode, pattern):
    c = db()
    rows = [r[0] for r in c.execute(
        "SELECT DISTINCT kit FROM runs WHERE mode=? AND pattern=? ORDER BY kit",
        (mode, pattern),
    )]
    c.close()
    return rows


def get_standings_msg(mode, pattern):
    c = db()
    row = c.execute(
        "SELECT channel_id, msg_id FROM standings WHERE mode=? AND pattern=?",
        (mode, pattern),
    ).fetchone()
    c.close()
    return row


def set_standings_msg(mode, pattern, channel_id, msg_id):
    c = db()
    c.execute(
        "INSERT INTO standings (mode, pattern, channel_id, msg_id) VALUES (?,?,?,?) "
        "ON CONFLICT(mode, pattern) DO UPDATE SET channel_id=excluded.channel_id, "
        "msg_id=excluded.msg_id",
        (mode, pattern, channel_id, msg_id),
    )
    c.commit()
    c.close()


def parse_embed(embed):
    """Extract a run dict from a webhook PB embed, or None if not a PB embed."""
    title = embed.title or ""
    m = re.search(r"new PB \(stage (\d+)\)", title)
    if not m:
        return None
    stage = int(m.group(1))
    name = title.split(" - new PB")[0].strip()

    fields = {f.name.lower(): f.value.strip() for f in embed.fields}
    mode = fields.get("mode")
    pat_text = fields.get("pattern")
    kit = fields.get("kit")
    if not mode or not pat_text or not kit:
        return None
    pm = re.search(r"Maze (\d+)", pat_text)
    pattern = int(pm.group(1)) - 1 if pm else 0

    # Extract string pattern name (e.g. "Maze 1")
    pattern_name = pat_text.strip() if pat_text else ""
    
    # Extract kit name 
    kit_name = kit.strip() if kit else ""

    time_ms = 0
    tm = re.search(r"(\d+)m (\d+)s", fields.get("time", "0m 0s"))
    if tm:
        time_ms = int(tm.group(1)) * 60000 + int(tm.group(2)) * 1000

    uuid = ""
    if embed.footer and embed.footer.text:
        um = re.search(r"uuid ([0-9a-f-]+)", embed.footer.text)
        if um:
            uuid = um.group(1)

    return {
        "name": name, "mode": mode.lower(), "pattern": pattern, "kit": kit,
        "stage": stage, "time_ms": time_ms, "uuid": uuid,
        "pattern_name": pattern_name, "kit_name": kit_name
    }


def standings_embed(mode, pattern, top_n):
    """Build the Discord embed for one (mode, pattern), ranked per kit."""
    embed = discord.Embed(
        title=f"{mode.capitalize()} — Maze {pattern + 1}", color=0x33aa66)
    added = False
    for kit in kits_for(mode, pattern):
        rows = board(mode, pattern, kit, top_n)
        if not rows:
            continue
        lines = []
        for i, (nm, best) in enumerate(rows, 1):
            medal = {1: ":first_place:", 2: ":second_place:",
                     3: ":third_place:"}.get(i, f"{i}.")
            lines.append(f"{medal} **{nm}** — stage {best}")
        embed.add_field(name=kit, value="\n".join(lines), inline=False)
        added = True
    return embed if added else None


class MonsterBot(discord.Client):
    def __init__(self, cfg):
        intents = discord.Intents.default()
        intents.message_content = True
        super().__init__(intents=intents)
        self.cfg = cfg
        self.top_n = int(cfg.get("top_n", 10))

    # -- helpers ----------------------------------------------------------
    async def feed_channels(self):
        out = []
        for cid in self.cfg.get("feed_channels", []):
            ch = self.get_channel(int(cid))
            if ch:
                out.append(ch)
        return out

    def mode_channel_id(self, mode):
        return int(self.cfg.get("mode_channels", {}).get(mode, 0) or 0)

    def is_feed(self, channel_id):
        return int(channel_id) in {int(c) for c in self.cfg.get("feed_channels", [])}

    # -- life cycle -------------------------------------------------------
    async def on_ready(self):
        print(f"Logged in as {self.user} (id {self.user.id})")
        try:
            await self.rebuild_all(notify=False)
            print("Ready. Standings up to date.")
        except Exception as e:
            print("initial rebuild failed:", e)

    async def rebuild_all(self, notify=True):
        """Scan feed history and refresh every standings message."""
        seen = 0
        for ch in await self.feed_channels():
            async for msg in ch.history(limit=500):
                for emb in msg.embeds:
                    run = parse_embed(emb)
                    if run:
                        upsert_run(run)
                        seen += 1
        print(f"rescanned {seen} runs")
        for mode in MODES:
            for pattern in range(PATTERNS):
                await self.refresh_standings(mode, pattern)

    # -- events ------------------------------------------------------------
    async def on_message(self, message):
        if message.author == self.user:
            return
        if message.content.strip().lower() == "!rebuild":
            await message.channel.send("Rebuilding standings from feed history...")
            await self.rebuild_all()
            await message.channel.send("Done.")
            return
        if self.is_feed(message.channel.id):
            for emb in message.embeds:
                run = parse_embed(emb)
                if run and upsert_run(run):
                    await self.refresh_standings(run["mode"], run["pattern"])

    # -- standings ---------------------------------------------------------
    async def refresh_standings(self, mode, pattern):
        target = self.mode_channel_id(mode)
        if not target:
            return
        channel = self.get_channel(target)
        if channel is None:
            print(f"mode channel not found: {mode} (id {target})")
            return
        embed = standings_embed(mode, pattern, self.top_n)
        if embed is None:
            return

        stored = get_standings_msg(mode, pattern)
        msg = None
        if stored:
            ch = self.get_channel(int(stored[0]))
            if ch:
                try:
                    msg = await ch.fetch_message(int(stored[1]))
                except discord.NotFound:
                    msg = None
        if msg is not None:
            try:
                await msg.edit(embed=embed)
                print(f"edited standings: {mode} pattern {pattern}")
            except discord.NotFound:
                msg = None
        if msg is None:
            new_msg = await channel.send(embed=embed)
            try:
                await new_msg.pin()
            except discord.HTTPException:
                pass
            set_standings_msg(mode, pattern, str(channel.id), str(new_msg.id))
            print(f"posted+pinned standings: {mode} pattern {pattern}")


def main():
    if not os.path.exists(CFG):
        print(f"Missing config.json - copy config.example.json and edit it. ({CFG})")
        return
    cfg = load_config()
    MonsterBot(cfg).run(cfg["token"])


if __name__ == "__main__":
    main()
