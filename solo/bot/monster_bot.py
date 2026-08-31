Monster Maze SOLO - Discord leaderboard bot.

Watches the PB feed channel(s), parses each posted "new PB" embed, stores the
best (mode, pattern, kit, player) in SQLite, and maintains THREE tiers of
ranked boards per game mode:

  Tier 1  Overall mode board
          Top stages across all patterns & kits for each player.

  Tier 2  Per-pattern boards
          Top stages on each pattern for each player.

  Tier 3  Per-kit boards
          Top stages for each specific pattern + kit for each player.

Boards are pinned embeds edited in place.
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


# ---------------------------------------------------------------------------
# Configuration
# ---------------------------------------------------------------------------

def load_config():
    with open(CFG, "r", encoding="utf-8") as fh:
        return json.load(fh)


# ---------------------------------------------------------------------------
# Database
# ---------------------------------------------------------------------------

def db():
    conn = sqlite3.connect(DB)

    conn.execute(
        "CREATE TABLE IF NOT EXISTS runs ("
        "mode TEXT, "
        "pattern INTEGER, "
        "kit TEXT, "
        "uuid TEXT, "
        "name TEXT, "
        "stage INTEGER, "
        "time_ms INTEGER, "
        "PRIMARY KEY (mode, pattern, kit, uuid)"
        ")"
    )

    conn.execute(
        "CREATE TABLE IF NOT EXISTS boards ("
        "board_key TEXT PRIMARY KEY, "
        "channel_id TEXT, "
        "msg_id TEXT"
        ")"
    )

    return conn


def upsert_run(run):
    """
    Record a run.

    A run is uniquely identified by:
        mode + pattern + kit + uuid

    A new record only replaces the existing record when it reaches a higher
    stage. This matches the game's PB semantics: stage is the primary score.
    """

    key = (
        run["mode"],
        run["pattern"],
        run["kit"],
        run["uuid"],
    )

    c = db()

    cur = c.execute(
        """
        SELECT stage
        FROM runs
        WHERE mode=?
          AND pattern=?
          AND kit=?
          AND uuid=?
        """,
        key,
    ).fetchone()

    if cur and run["stage"] <= cur[0]:
        c.close()
        return False

    c.execute(
        """
        INSERT INTO runs (
            mode,
            pattern,
            kit,
            uuid,
            name,
            stage,
            time_ms
        )
        VALUES (?, ?, ?, ?, ?, ?, ?)
        ON CONFLICT(mode, pattern, kit, uuid)
        DO UPDATE SET
            name=excluded.name,
            stage=excluded.stage,
            time_ms=excluded.time_ms
        """,
        (
            run["mode"],
            run["pattern"],
            run["kit"],
            run["uuid"],
            run["name"],
            run["stage"],
            run.get("time_ms", 0),
        ),
    )

    c.commit()
    c.close()

    return True


# ---------------------------------------------------------------------------
# Leaderboard queries
# ---------------------------------------------------------------------------

def _rows(where, params, top_n):
    """
    Return the best run for each player within the supplied leaderboard scope.

    The important part here is ROW_NUMBER().

    We deliberately select the COMPLETE row where rn=1 rather than using
    GROUP BY uuid together with MAX(stage). The latter can cause SQLite to
    return MAX(stage) from one run while taking the kit/name from another run
    belonging to the same player.

    Ranking:
      1. Highest stage wins.
      2. For equal-stage runs, time is used only as a deterministic tie-break.
      3. Kit/name/other fields all come from that SAME selected row.
    """

    c = db()

    rows = c.execute(
        """
        WITH ranked_runs AS (
            SELECT
                name,
                kit,
                stage,
                time_ms,
                ROW_NUMBER() OVER (
                    PARTITION BY uuid
                    ORDER BY
                        stage DESC,
                        time_ms ASC,
                        kit ASC,
                        name ASC
                ) AS rn
            FROM runs
        """
        + " "
        + where
        + """
        )
        SELECT
            name,
            kit,
            stage
        FROM ranked_runs
        WHERE rn = 1
        ORDER BY
            stage DESC,
            time_ms ASC,
            name ASC
        LIMIT ?
        """,
        tuple(params) + (top_n,),
    ).fetchall()

    c.close()

    return rows


def overall_board(mode, top_n):
    return _rows(
        "WHERE mode=?",
        [mode],
        top_n,
    )


def pattern_board(mode, pattern, top_n):
    return _rows(
        "WHERE mode=? AND pattern=?",
        [mode, pattern],
        top_n,
    )


def kit_board(mode, pattern, kit, top_n):
    return _rows(
        "WHERE mode=? AND pattern=? AND kit=?",
        [mode, pattern, kit],
        top_n,
    )


# ---------------------------------------------------------------------------
# Board message persistence
# ---------------------------------------------------------------------------

def get_board_msg(board_key):
    c = db()

    row = c.execute(
        """
        SELECT channel_id, msg_id
        FROM boards
        WHERE board_key=?
        """,
        (board_key,),
    ).fetchone()

    c.close()

    return row


def set_board_msg(board_key, channel_id, msg_id):
    c = db()

    c.execute(
        """
        INSERT INTO boards (
            board_key,
            channel_id,
            msg_id
        )
        VALUES (?, ?, ?)
        ON CONFLICT(board_key)
        DO UPDATE SET
            channel_id=excluded.channel_id,
            msg_id=excluded.msg_id
        """,
        (
            board_key,
            str(channel_id),
            str(msg_id),
        ),
    )

    c.commit()
    c.close()


# ---------------------------------------------------------------------------
# Submission parsing
# ---------------------------------------------------------------------------

def parse_embed(embed):
    """
    Extract a run dict from a webhook PB embed.

    Returns None when the embed is not a recognised PB submission.
    """

    title = embed.title or ""

    match = re.search(
        r"new PB \(stage (\d+)\)",
        title,
    )

    if not match:
        return None

    stage = int(match.group(1))
    name = title.split(" - new PB")[0].strip()

    fields = {
        field.name.lower(): field.value.strip()
        for field in embed.fields
    }

    mode = fields.get("mode")
    pattern_text = fields.get("pattern")
    kit = fields.get("kit")

    if not mode or not pattern_text or not kit:
        return None

    pattern_match = re.search(
        r"Maze (\d+)",
        pattern_text,
    )

    pattern = (
        int(pattern_match.group(1)) - 1
        if pattern_match
        else 0
    )

    time_ms = 0

    time_match = re.search(
        r"(\d+)m (\d+)s",
        fields.get("time", "0m 0s"),
    )

    if time_match:
        time_ms = (
            int(time_match.group(1)) * 60000
            + int(time_match.group(2)) * 1000
        )

    uuid = ""

    if embed.footer and embed.footer.text:
        uuid_match = re.search(
            r"uuid ([0-9a-f-]+)",
            embed.footer.text,
        )

        if uuid_match:
            uuid = uuid_match.group(1)

    return {
        "name": name,
        "mode": mode.lower(),
        "pattern": pattern,
        "kit": kit,
        "stage": stage,
        "time_ms": time_ms,
        "uuid": uuid,
    }


# ---------------------------------------------------------------------------
# Embed builders
# ---------------------------------------------------------------------------

def _lines(rows):
    """
    Convert leaderboard rows into Discord display lines.

    _rows() intentionally returns exactly:
        name, kit, stage
    """

    lines = []

    for i, (name, kit, stage) in enumerate(rows, 1):
        medal = {
            1: ":first_place:",
            2: ":second_place:",
            3: ":third_place:",
        }.get(i, f"{i}.")

        kit_text = f" ({kit})" if kit else ""

        lines.append(
            f"{medal} **{name}** — stage {stage}{kit_text}"
        )

    return lines if lines else ["No runs yet."]


def overall_embed(mode, top_n):
    embed = discord.Embed(
        title=f"{mode.capitalize()} — Overall",
        color=0x33AA66,
    )

    embed.add_field(
        name="Top Stages (all patterns/kits)",
        value="\n".join(
            _lines(
                overall_board(
                    mode,
                    top_n,
                )
            )
        ),
        inline=False,
    )

    return embed


def pattern_embed(mode, pattern, top_n):
    embed = discord.Embed(
        title=f"{mode.capitalize()} — Maze {pattern + 1}",
        color=0x33AA66,
    )

    embed.add_field(
        name="Top Stages (all kits)",
        value="\n".join(
            _lines(
                pattern_board(
                    mode,
                    pattern,
                    top_n,
                )
            )
        ),
        inline=False,
    )

    return embed


def kit_embed(mode, pattern, kit, top_n):
    embed = discord.Embed(
        title=f"{mode.capitalize()} — Maze {pattern + 1} — {kit}",
        color=0x33AA66,
    )

    embed.add_field(
        name=f"Top {kit} Stages",
        value="\n".join(
            _lines(
                kit_board(
                    mode,
                    pattern,
                    kit,
                    top_n,
                )
            )
        ),
        inline=False,
    )

    return embed


# ---------------------------------------------------------------------------
# Discord bot
# ---------------------------------------------------------------------------

class MonsterBot(discord.Client):

    def __init__(self, cfg):
        intents = discord.Intents.default()
        intents.message_content = True

        super().__init__(
            intents=intents
        )

        self.cfg = cfg
        self.top_n = int(
            cfg.get("top_n", 10)
        )
        self.modes = list(
            cfg.get(
                "modes",
                ["modern"],
            )
        )

    # -----------------------------------------------------------------------
    # Helpers
    # -----------------------------------------------------------------------

    async def feed_channels(self):
        out = []

        for channel_id in self.cfg.get(
            "feed_channels",
            [],
        ):
            channel = self.get_channel(
                int(channel_id)
            )

            if channel:
                out.append(channel)

        return out

    def is_feed(self, channel_id):
        return int(channel_id) in {
            int(channel_id)
            for channel_id in self.cfg.get(
                "feed_channels",
                [],
            )
        }

    def mode_channels(self, mode):
        return self.cfg.get(
            "channels",
            {}
        ).get(
            mode,
            {}
        )

    def resolve_channel(self, ref):
        """
        Resolve a channel reference that is either:
          - a numeric Discord channel ID
          - a channel name
        """

        if ref is None or ref == "":
            return None

        try:
            return self.get_channel(
                int(ref)
            )
        except (ValueError, TypeError):
            pass

        for guild in self.guilds:
            channel = discord.utils.get(
                guild.text_channels,
                name=ref,
            )

            if channel:
                return channel

        return None

    # -----------------------------------------------------------------------
    # Rebuild
    # -----------------------------------------------------------------------

    async def rebuild_all(self):
        """
        Rescan PB feed history and rebuild every configured leaderboard.

        Existing run records are not deleted. upsert_run() applies the same
        PB rules as normal live submissions.

        Existing leaderboard messages are then edited in place where possible.
        """

        seen = 0
        accepted = 0

        for channel in await self.feed_channels():

            async for message in channel.history(limit=500):

                for embed in message.embeds:

                    run = parse_embed(embed)

                    if not run:
                        continue

                    seen += 1

                    if upsert_run(run):
                        accepted += 1

        print(
            f"rescanned {seen} runs "
            f"({accepted} database updates)"
        )

        for mode in self.modes:
            await self.refresh_all_boards(mode)

    # -----------------------------------------------------------------------
    # Discord lifecycle
    # -----------------------------------------------------------------------

    async def on_ready(self):
        print(
            f"Logged in as {self.user} "
            f"(id {self.user.id})"
        )

        try:
            await self.rebuild_all()

            print(
                "Ready. Standings up to date."
            )

        except Exception as e:
            print(
                "initial rebuild failed:",
                repr(e)
            )

    # -----------------------------------------------------------------------
    # Discord events
    # -----------------------------------------------------------------------

    async def on_message(self, message):

        if message.author == self.user:
            return

        if message.content.strip().lower() == "!rebuild":

            await message.channel.send(
                "Rebuilding standings from feed history..."
            )

            try:
                await self.rebuild_all()

                await message.channel.send(
                    "Done."
                )

            except Exception as e:

                print(
                    "manual rebuild failed:",
                    repr(e)
                )

                await message.channel.send(
                    f"Rebuild failed: `{e}`"
                )

            return

        if not self.is_feed(message.channel.id):
            return

        for embed in message.embeds:

            run = parse_embed(embed)

            if not run:
                continue

            if upsert_run(run):
                await self.refresh_all_boards(
                    run["mode"]
                )

    # -----------------------------------------------------------------------
    # Boards
    # -----------------------------------------------------------------------

    async def refresh_all_boards(self, mode):

        channels = self.mode_channels(mode)

        if not channels:
            return

        overall_id = channels.get("overall")
        patterns_id = channels.get("patterns")
        kits_id = channels.get("kits")

        # Tier 1: overall mode board
        if overall_id:

            await self._post_or_edit(
                f"{mode}|overall",
                overall_id,
                overall_embed(
                    mode,
                    self.top_n,
                ),
                f"overall {mode}",
            )

        # Tier 2: per-pattern boards
        if patterns_id:

            for pattern in range(PATTERNS):

                await self._post_or_edit(
                    f"{mode}|p{pattern}",
                    patterns_id,
                    pattern_embed(
                        mode,
                        pattern,
                        self.top_n,
                    ),
                    f"{mode} pattern {pattern}",
                )

        # Tier 3: per-kit boards
        if kits_id:

            for pattern in range(PATTERNS):

                for kit in KITS:

                    await self._post_or_edit(
                        f"{mode}|p{pattern}|{kit}",
                        kits_id,
                        kit_embed(
                            mode,
                            pattern,
                            kit,
                            self.top_n,
                        ),
                        f"{mode} p{pattern} {kit}",
                    )

    async def _post_or_edit(
        self,
        board_key,
        channel_ref,
        embed,
        label,
    ):

        channel = self.resolve_channel(
            channel_ref
        )

        if channel is None:

            print(
                f"channel not found for board "
                f"{label} (ref {channel_ref})"
            )

            return

        stored = get_board_msg(
            board_key
        )

        message = None

        if stored:

            try:

                message = await channel.fetch_message(
                    int(stored[1])
                )

            except discord.NotFound:
                message = None

            except discord.HTTPException as e:

                print(
                    f"failed to fetch board "
                    f"{label}: {e}"
                )

                return

        if message is not None:

            try:

                await message.edit(
                    embed=embed
                )

                print(
                    f"edited {label}"
                )

                return

            except discord.NotFound:
                pass

            except discord.HTTPException as e:

                print(
                    f"failed to edit board "
                    f"{label}: {e}"
                )

                return

        new_message = await channel.send(
            embed=embed
        )

        try:
            await new_message.pin()

        except discord.HTTPException:
            pass

        set_board_msg(
            board_key,
            str(channel.id),
            str(new_message.id),
        )

        print(
            f"posted+pinned {label}"
        )


# ---------------------------------------------------------------------------
# Main
# ---------------------------------------------------------------------------

def main():

    if not os.path.exists(CFG):

        print(
            f"Missing config.json - copy "
            f"config.example.json and edit it. "
            f"({CFG})"
        )

        return

    cfg = load_config()

    MonsterBot(
        cfg
    ).run(
        cfg["token"]
    )


if __name__ == "__main__":
    main()
