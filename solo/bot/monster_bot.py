#!/usr/bin/env python3
"""
Monster Maze SOLO - Discord leaderboard bot.

Watches the PB feed channel(s), parses each posted "new PB" embed, stores the
best (mode, pattern, kit, player) in SQLite, and maintains THREE tiers of pinned,
bot-edited ranked boards per game mode:

  Tier 1  Overall mode board   (channel[x].overall)  : top stages across all patterns & kits
  Tier 2  Per-pattern boards   (channel[x].patterns) : one board per pattern (all kits)
  Tier 3  Per-kit boards       (channel[x].kits)     : one board per pattern x kit

Boards are pinned embeds edited in place, so updates are reliable (no threads,
which auto-archive and break editing).

Setup (cloud host, e.g. a free-tier VPS):
    cd bot
    pip install -r requirements.txt
    cp config.example.json config.json   # fill token + channel ids
    python monster_bot.py

Prereqs on Discord:
  - A bot application (https://discord.com/developers/applications -> New Application
    -> Bot -> copy token). Enable the Message Content Intent (Privileged Gateways).
  - Invite it with: Send Messages, Embed Links, Read Messages, Read Message History,
    Manage Messages (to pin/edit the standings).

Commands (any channel the bot can see):
    !rebuild   rescans feed history and refreshes all boards.
"""

import json
import os
import re
import sqlite3

import discord

HERE = os.path.dirname(os.path.abspath(__file__))
CFG = os.path.join(HERE, "config.json")
DB = os.path.join(HERE, "leaderboard.db")

KITS = ["Jumper", "Slowball", "Body Builder", "Repulsor", "Maverick"]
PATTERNS = 3


def load_config():
    with open(CFG, "r", encoding="utf-8") as fh:
        return json.load(fh)


def db():
    conn = sqlite3.connect(DB)
    conn.execute(
        "CREATE TABLE IF NOT EXISTS runs ("
        "mode TEXT, pattern INTEGER, kit TEXT, uuid TEXT, name TEXT, "
        "stage INTEGER, time_ms INTEGER, "
        "PRIMARY KEY (mode, pattern, kit, uuid))"
    )
    conn.execute(
        "CREATE TABLE IF NOT EXISTS boards ("
        "board_key TEXT PRIMARY KEY, channel_id TEXT, msg_id TEXT)"
    )
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


# ---- query helpers -----------------------------------------------------

def _rows(where, params, top_n):
    c = db()
    rows = c.execute(
        "SELECT name, kit, MAX(stage) AS best FROM runs " + where +
        " GROUP BY uuid ORDER BY best DESC, MIN(time_ms) ASC LIMIT ?",
        tuple(params) + (top_n,),
    ).fetchall()
    c.close()
    return rows


def overall_board(mode, top_n):
    return _rows("WHERE mode=?", [mode], top_n)


def pattern_board(mode, pattern, top_n):
    return _rows("WHERE mode=? AND pattern=?", [mode, pattern], top_n)


def kit_board(mode, pattern, kit, top_n):
    return _rows("WHERE mode=? AND pattern=? AND kit=?",
                 [mode, pattern, kit], top_n)


def get_board_msg(board_key):
    c = db()
    row = c.execute(
        "SELECT channel_id, msg_id FROM boards WHERE board_key=?",
        (board_key,),
    ).fetchone()
    c.close()
    return row


def set_board_msg(board_key, channel_id, msg_id):
    c = db()
    c.execute(
        "INSERT INTO boards (board_key, channel_id, msg_id) VALUES (?,?,?) "
        "ON CONFLICT(board_key) DO UPDATE SET channel_id=excluded.channel_id, "
        "msg_id=excluded.msg_id",
        (board_key, str(channel_id), str(msg_id)),
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
    }


# ---- embed builders -----------------------------------------------------

def _lines(rows):
    lines = []
    for i, (nm, kit, best) in enumerate(rows, 1):
        medal = {1: ":first_place:", 2: ":second_place:",
                 3: ":third_place:"}.get(i, f"{i}.")
        kit_txt = f" ({kit})" if kit else ""
        lines.append(f"{medal} **{nm}** — stage {best}{kit_txt}")
    return lines if lines else ["No runs yet."]


def overall_embed(mode, top_n):
    embed = discord.Embed(
        title=f"{mode.capitalize()} — Overall", color=0x33aa66)
    embed.add_field(name="Top Stages (all patterns/kits)",
                    value="\n".join(_lines(overall_board(mode, top_n))),
                    inline=False)
    return embed


def pattern_embed(mode, pattern, top_n):
    embed = discord.Embed(
        title=f"{mode.capitalize()} — Maze {pattern + 1}", color=0x33aa66)
    embed.add_field(name="Top Stages (all kits)",
                    value="\n".join(_lines(pattern_board(mode, pattern, top_n))),
                    inline=False)
    return embed


def kit_embed(mode, pattern, kit, top_n):
    embed = discord.Embed(
        title=f"{mode.capitalize()} — Maze {pattern + 1} — {kit}",
        color=0x33aa66)
    embed.add_field(name=f"Top {kit} Stages",
                    value="\n".join(_lines(kit_board(mode, pattern, kit, top_n))),
                    inline=False)
    return embed


class MonsterBot(discord.Client):
    def __init__(self, cfg):
        intents = discord.Intents.default()
        intents.message_content = True
        super().__init__(intents=intents)
        self.cfg = cfg
        self.top_n = int(cfg.get("top_n", 10))
        self.modes = list(cfg.get("modes", ["modern"]))

    # -- helpers ----------------------------------------------------------
    async def feed_channels(self):
        out = []
        for cid in self.cfg.get("feed_channels", []):
            ch = self.get_channel(int(cid))
            if ch:
                out.append(ch)
        return out

    def is_feed(self, channel_id):
        return int(channel_id) in {int(c) for c in self.cfg.get("feed_channels", [])}

    def mode_channels(self, mode):
        return self.cfg.get("channels", {}).get(mode, {})

    def resolve_channel(self, ref):
        """Resolve a channel ref that is either a numeric ID or a channel name."""
        if ref is None or ref == "":
            return None
        try:
            return self.get_channel(int(ref))
        except (ValueError, TypeError):
            pass
        # name lookup across all guilds the bot can see
        for guild in self.guilds:
            ch = discord.utils.get(guild.text_channels, name=ref)
            if ch:
                return ch
        return None

    # -- life cycle -------------------------------------------------------
    async def on_ready(self):
        print(f"Logged in as {self.user} (id {self.user.id})")
        try:
            await self.rebuild_all(notify=False)
            print("Ready. Standings up to date.")
        except Exception as e:
            print("initial rebuild failed:", e)

    async def rebuild_all(self):
        seen = 0
        for ch in await self.feed_channels():
            async for msg in ch.history(limit=500):
                for emb in msg.embeds:
                    run = parse_embed(emb)
                    if run:
                        upsert_run(run)
                        seen += 1
        print(f"rescanned {seen} runs")
        for mode in self.modes:
            await self.refresh_all_boards(mode)

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
                    await self.refresh_all_boards(run["mode"])

    # -- boards ------------------------------------------------------------
    async def refresh_all_boards(self, mode):
        chans = self.mode_channels(mode)
        if not chans:
            return

        overall_id = chans.get("overall")
        patterns_id = chans.get("patterns")
        kits_id = chans.get("kits")

        # Tier 1: overall mode board
        if overall_id:
            await self._post_or_edit(
                f"{mode}|overall", overall_id, overall_embed(mode, self.top_n),
                f"overall {mode}")
        # Tier 2: per-pattern boards
        if patterns_id:
            for p in range(PATTERNS):
                await self._post_or_edit(
                    f"{mode}|p{p}", patterns_id, pattern_embed(mode, p, self.top_n),
                    f"{mode} pattern {p}")
        # Tier 3: per-kit boards
        if kits_id:
            for p in range(PATTERNS):
                for kit in KITS:
                    await self._post_or_edit(
                        f"{mode}|p{p}|{kit}", kits_id,
                        kit_embed(mode, p, kit, self.top_n),
                        f"{mode} p{p} {kit}")

    async def _post_or_edit(self, board_key, channel_ref, embed, label):
        channel = self.resolve_channel(channel_ref)
        if channel is None:
            print(f"channel not found for board {label} (ref {channel_ref})")
            return
        stored = get_board_msg(board_key)
        msg = None
        if stored:
            try:
                msg = await channel.fetch_message(int(stored[1]))
            except discord.NotFound:
                msg = None
        if msg is not None:
            try:
                await msg.edit(embed=embed)
                print(f"edited {label}")
                return
            except discord.NotFound:
                pass
        new_msg = await channel.send(embed=embed)
        try:
            await new_msg.pin()
        except discord.HTTPException:
            pass
        set_board_msg(board_key, str(channel.id), str(new_msg.id))
        print(f"posted+pinned {label}")


def main():
    if not os.path.exists(CFG):
        print(f"Missing config.json - copy config.example.json and edit it. ({CFG})")
        return
    cfg = load_config()
    MonsterBot(cfg).run(cfg["token"])


if __name__ == "__main__":
    main()
