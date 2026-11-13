import asyncio
import hashlib
import hmac
import json
import os
import sqlite3
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from threading import Thread
from urllib.parse import urlparse

from bridge_logic import format_activity, format_release, format_subissue, repository_allowlist, state_change

import aiohttp
import discord
from discord.ext import commands

DISCORD_TOKEN = os.environ["DISCORD_TOKEN"]
DISCORD_GUILD_ID = int(os.environ["DISCORD_GUILD_ID"])
GITHUB_TOKEN = os.environ["GITHUB_TOKEN"]
GITHUB_REPOSITORY = os.environ["GITHUB_REPOSITORY"]
GITHUB_WEBHOOK_SECRET = os.environ["GITHUB_WEBHOOK_SECRET"].encode()
BUG_CHANNEL_ID = int(os.environ["DISCORD_BUG_CHANNEL_ID"])
IDEA_CHANNEL_ID = int(os.environ["DISCORD_IDEA_CHANNEL_ID"])
CHANGELOG_REPOSITORY = os.getenv("CHANGELOG_REPOSITORY", "joshbet9/testplex")
TRACKER_REPOSITORY = os.getenv("TRACKER_REPOSITORY", "joshbet9/testplex")
WEBHOOK_REPOSITORIES = repository_allowlist(os.getenv(
    "WEBHOOK_REPOSITORIES", f"{GITHUB_REPOSITORY},{CHANGELOG_REPOSITORY},{TRACKER_REPOSITORY}"
))
CHANGELOG_CHANNEL_ID = int(os.getenv("DISCORD_CHANGELOG_CHANNEL_ID", "0"))
ACTIVITY_CHANNEL_ID = int(os.getenv("DISCORD_ACTIVITY_CHANNEL_ID", "0"))
ISSUE_INDEX_CHANNEL_ID = int(os.getenv("DISCORD_ISSUE_INDEX_CHANNEL_ID", "0"))
SERVER_STATUS_URL = os.getenv("SERVER_STATUS_URL", "")
SERVER_STATUS_INTERVAL = max(10, int(os.getenv("SERVER_STATUS_INTERVAL", "15")))
SERVER_ACTIVITY_SERVERS = tuple(
    item.strip() for item in os.getenv("SERVER_ACTIVITY_SERVERS", "1.21").split(",") if item.strip()
)
DB_PATH = os.getenv("DATABASE_PATH", "data/bridge.sqlite3")
PORT = int(os.getenv("PORT", "8080"))

os.makedirs(os.path.dirname(DB_PATH) or ".", exist_ok=True)


def init_db():
    with sqlite3.connect(DB_PATH) as conn:
        conn.execute(
            "CREATE TABLE IF NOT EXISTS mappings ("
            "discord_thread_id TEXT PRIMARY KEY, "
            "github_issue_number INTEGER UNIQUE NOT NULL, "
            "discord_channel_id TEXT NOT NULL)"
        )
        conn.execute(
            "CREATE TABLE IF NOT EXISTS tracker_threads ("
            "repository TEXT NOT NULL, tracker_issue_number INTEGER NOT NULL, "
            "discord_thread_id TEXT NOT NULL, PRIMARY KEY(repository, tracker_issue_number))"
        )
        conn.execute(
            "CREATE TABLE IF NOT EXISTS subissue_messages ("
            "repository TEXT NOT NULL, issue_number INTEGER NOT NULL, tracker_issue_number INTEGER NOT NULL, "
            "discord_message_id TEXT NOT NULL, PRIMARY KEY(repository, issue_number))"
        )
        conn.execute(
            "CREATE TABLE IF NOT EXISTS server_activity_state ("
            "server TEXT PRIMARY KEY, state TEXT NOT NULL, started_by TEXT)"
        )
        conn.execute(
            "CREATE TABLE IF NOT EXISTS release_posts (release_id TEXT PRIMARY KEY, posted_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP)"
        )


def mapping_for_thread(thread_id):
    with sqlite3.connect(DB_PATH) as conn:
        row = conn.execute(
            "SELECT github_issue_number FROM mappings WHERE discord_thread_id = ?",
            (str(thread_id),),
        ).fetchone()
    return row[0] if row else None


def thread_for_issue(issue_number):
    if issue_number is None:
        return None
    with sqlite3.connect(DB_PATH) as conn:
        row = conn.execute(
            "SELECT discord_thread_id FROM mappings WHERE github_issue_number = ?",
            (issue_number,),
        ).fetchone()
    return int(row[0]) if row else None


def save_mapping(thread_id, issue_number, channel_id):
    with sqlite3.connect(DB_PATH) as conn:
        conn.execute(
            "INSERT OR IGNORE INTO mappings(discord_thread_id, github_issue_number, discord_channel_id) "
            "VALUES (?, ?, ?)",
            (str(thread_id), issue_number, str(channel_id)),
        )


async def github_request(method, path, payload=None):
    url = f"https://api.github.com/repos/{GITHUB_REPOSITORY}{path}"
    headers = {
        "Accept": "application/vnd.github+json",
        "Authorization": f"Bearer {GITHUB_TOKEN}",
        "X-GitHub-Api-Version": "2022-11-28",
        "User-Agent": "MonsterMaze-Discord-GitHub-Bridge",
    }
    async with aiohttp.ClientSession(headers=headers) as session:
        async with session.request(method, url, json=payload, timeout=20) as response:
            if response.status >= 300:
                raise RuntimeError(f"GitHub API {response.status}: {await response.text()}")
            return await response.json()


async def github_repository_request(repository, method, path):
    url = f"https://api.github.com/repos/{repository}{path}"
    headers = {
        "Accept": "application/vnd.github+json",
        "Authorization": f"Bearer {GITHUB_TOKEN}",
        "X-GitHub-Api-Version": "2022-11-28",
        "User-Agent": "MonsterMaze-Discord-GitHub-Bridge",
    }
    async with aiohttp.ClientSession(headers=headers) as session:
        async with session.request(method, url, timeout=20) as response:
            if response.status >= 300:
                raise RuntimeError(f"GitHub API {response.status}: {await response.text()}")
            return await response.json()


async def discord_channel(channel_id):
    if not channel_id:
        return None
    return bot.get_channel(channel_id) or await bot.fetch_channel(channel_id)


async def post_release(release, repository):
    if not CHANGELOG_CHANNEL_ID or repository.casefold() != CHANGELOG_REPOSITORY.casefold():
        return
    if release.get("draft") or release.get("prerelease"):
        return
    release_id = str(release.get("id") or release.get("tag_name") or release.get("html_url", ""))
    if not release_id:
        return
    with sqlite3.connect(DB_PATH) as conn:
        if conn.execute("SELECT 1 FROM release_posts WHERE release_id=?", (release_id,)).fetchone():
            return
    channel = await discord_channel(CHANGELOG_CHANNEL_ID)
    if channel is not None:
        await channel.send(format_release(repository, release),
                           allowed_mentions=discord.AllowedMentions.none())
        with sqlite3.connect(DB_PATH) as conn:
            conn.execute("INSERT OR IGNORE INTO release_posts(release_id) VALUES (?)", (release_id,))


async def poll_server_activity():
    if not SERVER_STATUS_URL or not ACTIVITY_CHANNEL_ID:
        return
    try:
        async with aiohttp.ClientSession() as session:
            async with session.get(SERVER_STATUS_URL, timeout=10) as response:
                if response.status >= 300:
                    raise RuntimeError(f"status API returned HTTP {response.status}")
                data = await response.json()
        channel = await discord_channel(ACTIVITY_CHANNEL_ID)
        if channel is None:
            return
        for server in SERVER_ACTIVITY_SERVERS:
            server_data = data.get("servers", {}).get(server, {})
            state = server_data.get("state")
            if not state:
                continue
            started_by = server_data.get("started_by")
            with sqlite3.connect(DB_PATH) as conn:
                row = conn.execute(
                    "SELECT state, started_by FROM server_activity_state WHERE server = ?", (server,)
                ).fetchone()
                previous_state, previous_starter = row if row else (None, None)
                announce = state_change(previous_state, previous_starter, state, started_by)
            if announce:
                await channel.send(format_activity(server, state, started_by),
                                   allowed_mentions=discord.AllowedMentions.none())
            with sqlite3.connect(DB_PATH) as conn:
                conn.execute(
                    "INSERT INTO server_activity_state(server, state, started_by) VALUES (?, ?, ?) "
                    "ON CONFLICT(server) DO UPDATE SET state=excluded.state, started_by=excluded.started_by",
                    (server, state, started_by if state == "starting" else None),
                )
    except Exception as exc:
        print(f"[activity] status polling failed: {exc}", flush=True)


async def server_activity_loop():
    while True:
        await poll_server_activity()
        await asyncio.sleep(SERVER_STATUS_INTERVAL)


def tracker_thread_id(repository, tracker_issue_number):
    with sqlite3.connect(DB_PATH) as conn:
        row = conn.execute(
            "SELECT discord_thread_id FROM tracker_threads WHERE repository=? AND tracker_issue_number=?",
            (repository, tracker_issue_number),
        ).fetchone()
    return int(row[0]) if row else None


def save_tracker_thread(repository, tracker_issue_number, thread_id):
    with sqlite3.connect(DB_PATH) as conn:
        conn.execute(
            "INSERT INTO tracker_threads(repository, tracker_issue_number, discord_thread_id) VALUES (?, ?, ?) "
            "ON CONFLICT(repository, tracker_issue_number) DO UPDATE SET discord_thread_id=excluded.discord_thread_id",
            (repository, tracker_issue_number, str(thread_id)),
        )


def subissue_message_id(repository, issue_number):
    with sqlite3.connect(DB_PATH) as conn:
        row = conn.execute(
            "SELECT discord_message_id FROM subissue_messages WHERE repository=? AND issue_number=?",
            (repository, issue_number),
        ).fetchone()
    return int(row[0]) if row else None


def save_subissue_message(repository, issue_number, tracker_issue_number, message_id):
    with sqlite3.connect(DB_PATH) as conn:
        conn.execute(
            "INSERT INTO subissue_messages(repository, issue_number, tracker_issue_number, discord_message_id) "
            "VALUES (?, ?, ?, ?) ON CONFLICT(repository, issue_number) DO UPDATE SET "
            "tracker_issue_number=excluded.tracker_issue_number, discord_message_id=excluded.discord_message_id",
            (repository, issue_number, tracker_issue_number, str(message_id)),
        )


async def ensure_tracker_thread(tracker):
    thread_id = tracker_thread_id(TRACKER_REPOSITORY, tracker["number"])
    if thread_id:
        try:
            thread = await discord_channel(thread_id)
            if thread and thread.archived:
                await thread.edit(archived=False)
            return thread
        except discord.HTTPException:
            pass
    channel = await discord_channel(ISSUE_INDEX_CHANNEL_ID)
    if channel is None:
        return None
    thread = await channel.create_thread(
        name=tracker["title"][:100],
        type=discord.ChannelType.public_thread,
        auto_archive_duration=10080,
    )
    save_tracker_thread(TRACKER_REPOSITORY, tracker["number"], thread.id)
    await thread.send(f"GitHub tracker: {tracker['html_url']}\nSub-issues are synchronized below.")
    return thread


async def sync_tracker(tracker):
    thread = await ensure_tracker_thread(tracker)
    if thread is None:
        return
    children = await github_repository_request(
        TRACKER_REPOSITORY, "GET", f"/issues/{tracker['number']}/sub_issues?per_page=100"
    )
    children = sorted(children, key=lambda item: item["number"])
    current_numbers = {child["number"] for child in children}
    for child in children:
        content = format_subissue(child)
        message_id = subissue_message_id(TRACKER_REPOSITORY, child["number"])
        message = None
        if message_id:
            try:
                message = await thread.fetch_message(message_id)
                if message.content != content:
                    await message.edit(content=content, allowed_mentions=discord.AllowedMentions.none())
            except discord.NotFound:
                message = None
        if message is None:
            message = await thread.send(content, allowed_mentions=discord.AllowedMentions.none())
        save_subissue_message(TRACKER_REPOSITORY, child["number"], tracker["number"], message.id)
    with sqlite3.connect(DB_PATH) as conn:
        stale = conn.execute(
            "SELECT issue_number, discord_message_id FROM subissue_messages WHERE repository=? AND tracker_issue_number=?",
            (TRACKER_REPOSITORY, tracker["number"]),
        ).fetchall()
    for issue_number, message_id in stale:
        if issue_number not in current_numbers:
            try:
                await thread.get_partial_message(int(message_id)).delete()
            except discord.HTTPException:
                pass
            with sqlite3.connect(DB_PATH) as conn:
                conn.execute("DELETE FROM subissue_messages WHERE repository=? AND issue_number=?",
                             (TRACKER_REPOSITORY, issue_number))


async def sync_all_trackers():
    if not ISSUE_INDEX_CHANNEL_ID:
        return
    try:
        trackers = await github_repository_request(
            TRACKER_REPOSITORY, "GET", "/issues?state=all&labels=game-tracker&per_page=100"
        )
        for tracker in trackers:
            await sync_tracker(tracker)
    except Exception as exc:
        print(f"[issue-index] synchronization failed: {exc}", flush=True)


async def issue_index_loop():
    while True:
        await sync_all_trackers()
        await asyncio.sleep(600)


def issue_body(kind, starter, thread):
    return (
        f"## Discord {kind} report\n\n{starter.content}\n\n---\n"
        f"Discord thread: {thread.jump_url}\nReporter: {starter.author} ({starter.author.id})\n"
        f"<!-- monster-maze-discord-thread:{thread.id} -->"
    )


async def create_issue_from_thread(thread):
    if thread.guild is None or thread.guild.id != DISCORD_GUILD_ID:
        return
    if thread.parent_id not in {BUG_CHANNEL_ID, IDEA_CHANNEL_ID} or mapping_for_thread(thread.id):
        return
    try:
        starter = await thread.fetch_message(thread.id)
        kind = "bug" if thread.parent_id == BUG_CHANNEL_ID else "idea"
        body = issue_body(kind, starter, thread)
        issue = await github_request(
            "POST",
            "/issues",
            {"title": thread.name[:256], "body": body, "labels": [kind]},
        )
        save_mapping(thread.id, issue["number"], thread.parent_id)
        await thread.send(
            f"🔗 **GitHub Issue #{issue['number']}**\n{issue['html_url']}\nLabels: `{kind}`"
        )
    except (discord.HTTPException, RuntimeError) as exc:
        print(f"Failed to create GitHub issue for Discord thread {thread.id}: {exc}", flush=True)


async def update_issue_from_thread(thread, issue_number):
    try:
        starter = await thread.fetch_message(thread.id)
        kind = "bug" if thread.parent_id == BUG_CHANNEL_ID else "idea"
        await github_request(
            "PATCH",
            f"/issues/{issue_number}",
            {"title": thread.name[:256], "body": issue_body(kind, starter, thread)},
        )
        print(f"Updated GitHub Issue #{issue_number} from Discord thread {thread.id}", flush=True)
    except (discord.HTTPException, RuntimeError) as exc:
        print(f"Failed to update GitHub Issue #{issue_number} from Discord thread {thread.id}: {exc}", flush=True)


async def sync_thread_to_issue(thread):
    if thread.guild is None or thread.guild.id != DISCORD_GUILD_ID:
        return
    if thread.parent_id not in {BUG_CHANNEL_ID, IDEA_CHANNEL_ID}:
        return
    issue_number = mapping_for_thread(thread.id)
    if issue_number:
        await update_issue_from_thread(thread, issue_number)
    else:
        await create_issue_from_thread(thread)


async def handle_message_edit(message_id, channel_id):
    if message_id != channel_id:
        return
    try:
        thread = bot.get_channel(channel_id) or await bot.fetch_channel(channel_id)
    except discord.HTTPException:
        return
    if not isinstance(thread, discord.Thread):
        return
    await sync_thread_to_issue(thread)


async def sync_issue_to_discord(issue):
    thread_id = thread_for_issue(issue.get("number"))
    if not thread_id:
        return
    try:
        channel = bot.get_channel(thread_id) or await bot.fetch_channel(thread_id)
        labels = ", ".join(label["name"] for label in issue.get("labels", [])) or "none"
        state = "🟢 Open" if issue.get("state") == "open" else "✅ Closed"
        await channel.send(
            f"{state} — GitHub Issue #{issue['number']} updated.\n"
            f"Labels: `{labels}`\n{issue['html_url']}"
        )
    except discord.HTTPException:
        pass


async def forward_github_comment(body):
    if body.get("action") != "created":
        return
    issue = body.get("issue", {})
    thread_id = thread_for_issue(issue.get("number"))
    if not thread_id:
        return
    comment = body.get("comment", {}).get("body", "")
    if "<!-- monster-maze-discord-bridge -->" in comment:
        return
    try:
        channel = bot.get_channel(thread_id) or await bot.fetch_channel(thread_id)
        user = body.get("comment", {}).get("user", {}).get("login", "GitHub user")
        await channel.send(f"💬 **GitHub comment from {user}:**\n{comment}")
    except discord.HTTPException:
        pass


class WebhookHandler(BaseHTTPRequestHandler):
    def do_GET(self):
        if urlparse(self.path).path != "/health":
            self.send_response(404)
            self.end_headers()
            return
        body = b'{"ok":true}'
        self.send_response(200)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(body)))
        self.send_header("Cache-Control", "no-store")
        self.end_headers()
        self.wfile.write(body)

    def do_POST(self):
        if urlparse(self.path).path != "/github/webhook":
            self.send_response(404)
            self.end_headers()
            return
        try:
            content_length = int(self.headers.get("Content-Length", "0"))
        except ValueError:
            self.send_response(400)
            self.end_headers()
            return
        body = self.rfile.read(content_length)
        signature = self.headers.get("X-Hub-Signature-256", "")
        expected = hmac.new(GITHUB_WEBHOOK_SECRET, body, hashlib.sha256).hexdigest()
        if not signature.startswith("sha256=") or not hmac.compare_digest(signature[7:], expected):
            self.send_response(401)
            self.end_headers()
            return
        try:
            payload = json.loads(body)
        except json.JSONDecodeError:
            self.send_response(400)
            self.end_headers()
            return
        event = self.headers.get("X-GitHub-Event", "")
        repository = payload.get("repository", {}).get("full_name", "")
        if repository.casefold() not in WEBHOOK_REPOSITORIES:
            self.send_response(202)
            self.end_headers()
            return
        if event == "issues" and payload.get("action") in {
            "opened", "closed", "reopened", "edited", "labeled", "unlabeled"
        }:
            issue = payload["issue"]
            if repository.casefold() == GITHUB_REPOSITORY.casefold():
                asyncio.run_coroutine_threadsafe(sync_issue_to_discord(issue), bot.loop)
            if repository.casefold() == TRACKER_REPOSITORY.casefold() and ISSUE_INDEX_CHANNEL_ID:
                asyncio.run_coroutine_threadsafe(sync_all_trackers(), bot.loop)
        elif event == "release" and payload.get("action") == "published":
            asyncio.run_coroutine_threadsafe(post_release(payload.get("release", {}), repository), bot.loop)
        elif event == "issue_comment":
            if repository.casefold() == GITHUB_REPOSITORY.casefold():
                asyncio.run_coroutine_threadsafe(forward_github_comment(payload), bot.loop)
        self.send_response(204)
        self.end_headers()

    def log_message(self, fmt, *args):
        print(fmt % args, flush=True)


intents = discord.Intents.default()
bot = commands.Bot(command_prefix="!", intents=intents)


@bot.event
async def on_ready():
    print(f"Logged in as {bot.user}", flush=True)
    if ACTIVITY_CHANNEL_ID and SERVER_STATUS_URL:
        if not getattr(bot, "server_activity_task", None) or bot.server_activity_task.done():
            bot.server_activity_task = asyncio.create_task(server_activity_loop())
    if ISSUE_INDEX_CHANNEL_ID:
        if not getattr(bot, "issue_index_task", None) or bot.issue_index_task.done():
            bot.issue_index_task = asyncio.create_task(issue_index_loop())


@bot.event
async def on_thread_create(thread):
    await create_issue_from_thread(thread)


@bot.event
async def on_thread_update(before, after):
    if before.name == after.name:
        return
    await sync_thread_to_issue(after)


@bot.event
async def on_raw_message_edit(payload):
    await handle_message_edit(payload.message_id, payload.channel_id)


def start_webhook_server():
    ThreadingHTTPServer(("0.0.0.0", PORT), WebhookHandler).serve_forever()


async def main():
    init_db()
    Thread(target=start_webhook_server, daemon=True).start()
    await bot.start(DISCORD_TOKEN)


if __name__ == "__main__":
    asyncio.run(main())
