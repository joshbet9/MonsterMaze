"""One-time Discord migration utility for Monster Maze #solo-runs.

The bot can read historical PBs from the new channel after this script copies
messages from the old server. Run with a bot that can read the old channel and
send embeds/messages in the new channel.

Copy config.example.json to config.json and add:
  "migration": {
    "source_channel": "OLD_SOLO_RUNS_CHANNEL_ID",
    "destination_channel": "NEW_SOLO_RUNS_CHANNEL_ID"
  }

The script is resumable: source message IDs are stored in
solo_runs_migration.sqlite3 so rerunning it will not duplicate messages.
"""

import asyncio
import json
import os
import sqlite3

import discord

HERE = os.path.dirname(os.path.abspath(__file__))
CFG = os.path.join(HERE, "config.json")
MIGRATION_DB = os.path.join(HERE, "solo_runs_migration.sqlite3")
MAX_RETRIES = 5


def load_config():
    with open(CFG, "r", encoding="utf-8") as fh:
        return json.load(fh)


def init_db():
    conn = sqlite3.connect(MIGRATION_DB)
    conn.execute("CREATE TABLE IF NOT EXISTS copied (source_message_id TEXT PRIMARY KEY, destination_message_id TEXT NOT NULL)")
    conn.commit()
    return conn


def was_copied(conn, message_id):
    return conn.execute("SELECT 1 FROM copied WHERE source_message_id=?", (str(message_id),)).fetchone() is not None


def mark_copied(conn, source_id, destination_id):
    conn.execute("INSERT OR IGNORE INTO copied (source_message_id, destination_message_id) VALUES (?, ?)", (str(source_id), str(destination_id)))
    conn.commit()


async def discord_call(operation, label):
    delay = 1.0
    for attempt in range(MAX_RETRIES + 1):
        try:
            return await operation()
        except discord.HTTPException as exc:
            if attempt >= MAX_RETRIES or exc.status not in (429, 500, 502, 503, 504):
                raise
            retry_after = getattr(exc, "retry_after", None)
            wait = float(retry_after) if retry_after else delay
            print(f"Discord {exc.status} for {label}; retrying in {min(wait, 30.0):.1f}s")
            await asyncio.sleep(min(wait, 30.0))
            delay = min(delay * 2.0, 30.0)


class MigrationClient(discord.Client):
    def __init__(self, cfg):
        intents = discord.Intents.default()
        intents.message_content = True
        super().__init__(intents=intents)
        self.cfg = cfg
        self.conn = init_db()

    def resolve_channel(self, ref):
        try:
            channel = self.get_channel(int(ref))
            if channel:
                return channel
        except (TypeError, ValueError):
            pass
        for guild in self.guilds:
            channel = discord.utils.get(guild.text_channels, name=str(ref))
            if channel:
                return channel
        return None

    async def on_ready(self):
        migration = self.cfg.get("migration", {})
        source = self.resolve_channel(migration.get("source_channel"))
        destination = self.resolve_channel(migration.get("destination_channel"))
        if source is None or destination is None:
            print("Could not resolve source/destination channel.")
            print(f"source={migration.get('source_channel')!r} destination={migration.get('destination_channel')!r}")
            await self.close()
            return

        print(f"Logged in as {self.user} (id {self.user.id})")
        print(f"Migrating #{source.name} -> #{destination.name}")
        copied = 0
        skipped = 0
        failed = 0

        async for message in source.history(limit=None, oldest_first=True):
            if was_copied(self.conn, message.id):
                skipped += 1
                continue
            try:
                embeds = list(message.embeds)
                content = message.content or None
                if not content and not embeds:
                    # There should normally be no empty messages, but avoid
                    # creating meaningless placeholders if there are any.
                    mark_copied(self.conn, message.id, "0")
                    skipped += 1
                    continue
                new_message = await discord_call(
                    lambda: destination.send(content=content, embeds=embeds, allowed_mentions=discord.AllowedMentions.none()),
                    f"copy {message.id}",
                )
                mark_copied(self.conn, message.id, new_message.id)
                copied += 1
                if copied % 25 == 0:
                    print(f"Copied {copied} messages...")
            except Exception as exc:
                failed += 1
                print(f"Failed to copy {message.id}: {exc!r}")

        print(f"Migration complete: copied={copied}, already-copied={skipped}, failed={failed}")
        await self.close()


def main():
    if not os.path.exists(CFG):
        print(f"Missing config.json: {CFG}")
        return
    cfg = load_config()
    migration = cfg.get("migration", {})
    if not migration.get("source_channel") or not migration.get("destination_channel"):
        print("Add migration.source_channel and migration.destination_channel to config.json first.")
        return
    token = cfg.get("token")
    if not token or token == "YOUR_BOT_TOKEN":
        print("Set a Discord bot token in config.json first.")
        return
    MigrationClient(cfg).run(token)


if __name__ == "__main__":
    main()
