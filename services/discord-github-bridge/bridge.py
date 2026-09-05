import asyncio
import hashlib
import hmac
import json
import os
import sqlite3
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from threading import Thread
from urllib.parse import urlparse

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
        if event == "issues" and payload.get("action") in {
            "opened", "closed", "reopened", "edited", "labeled", "unlabeled"
        }:
            asyncio.run_coroutine_threadsafe(sync_issue_to_discord(payload["issue"]), bot.loop)
        elif event == "issue_comment":
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
