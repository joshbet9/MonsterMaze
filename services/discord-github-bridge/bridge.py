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
        conn.execute("CREATE TABLE IF NOT EXISTS mappings (discord_thread_id TEXT PRIMARY KEY, github_issue_number INTEGER UNIQUE NOT NULL, discord_channel_id TEXT NOT NULL)")


def mapping_for_thread(thread_id):
    with sqlite3.connect(DB_PATH) as conn:
        row = conn.execute("SELECT github_issue_number FROM mappings WHERE discord_thread_id = ?", (str(thread_id),)).fetchone()
    return row[0] if row else None


def thread_for_issue(issue_number):
    with sqlite3.connect(DB_PATH) as conn:
        row = conn.execute("SELECT discord_thread_id FROM mappings WHERE github_issue_number = ?", (issue_number,)).fetchone()
    return int(row[0]) if row else None


def save_mapping(thread_id, issue_number, channel_id):
    with sqlite3.connect(DB_PATH) as conn:
        conn.execute("INSERT OR IGNORE INTO mappings(discord_thread_id, github_issue_number, discord_channel_id) VALUES (?, ?, ?)", (str(thread_id), issue_number, str(channel_id)))


async def github_request(method, path, payload=None):
    url = f"https://api.github.com/repos/{GITHUB_REPOSITORY}{path}"
    headers = {"Accept": "application/vnd.github+json", "Authorization": f"Bearer {GITHUB_TOKEN}", "X-GitHub-Api-Version": "2022-11-28", "User-Agent": "MonsterMaze-Discord-GitHub-Bridge"}
    async with aiohttp.ClientSession(headers=headers) as session:
        async with session.request(method, url, json=payload, timeout=20) as response:
            if response.status >= 300:
                raise RuntimeError(f"GitHub API {response.status}: {await response.text()}")
            return await response.json()


async def create_issue_from_thread(thread):
    if thread.parent_id not in {BUG_CHANNEL_ID, IDEA_CHANNEL_ID} or mapping_for_thread(thread.id):
        return
    try:
        starter = await thread.fetch_message(thread.id)
    except discord.HTTPException:
        return

    kind = "bug" if thread.parent_id == BUG_CHANNEL_ID else "idea"
    body = (f"## Discord {kind} report\n\n{starter.content}\n\n---\n"
            f"Discord thread: {thread.jump_url}\nReporter: {starter.author} ({starter.author.id})\n"
            f"<!-- monster-maze-discord-thread:{thread.id} -->")
    issue = await github_request("POST", "/issues", {"title": thread.name[:256], "body": body, "labels": [kind]})
    save_mapping(thread.id, issue["number"], thread.parent_id)
    await thread.send(f"🔗 **GitHub Issue #{issue['number']}**\n{issue['html_url']}\nLabels: `{kind}`")


async def sync_issue_to_discord(issue):
    thread_id = thread_for_issue(issue["number"])
    if not thread_id:
        return
    try:
        channel = bot.get_channel(thread_id) or await bot.fetch_channel(thread_id)
        labels = ", ".join(label["name"] for label in issue.get("labels", [])) or "none"
        state = "🟢 Open" if issue["state"] == "open" else "✅ Closed"
        await channel.send(f"{state} — GitHub Issue #{issue['number']} updated.\nLabels: `{labels}`\n{issue['html_url']}")
    except discord.HTTPException:
        pass


async def forward_github_comment(body):
    if body.get("action") != "created":
        return
    issue = body["issue"]
    thread_id = thread_for_issue(issue["number"])
    if not thread_id:
        return
    comment = body["comment"]["body"]
    if "<!-- monster-maze-discord-bridge -->" in comment:
        return
    try:
        channel = bot.get_channel(thread_id) or await bot.fetch_channel(thread_id)
        await channel.send(f"💬 **GitHub comment from {body['comment']['user']['login']}:**\n{comment}")
    except discord.HTTPException:
        pass


class WebhookHandler(BaseHTTPRequestHandler):
    def do_POST(self):
        if urlparse(self.path).path != "/github/webhook":
            self.send_response(404); self.end_headers(); return
        body = self.rfile.read(int(self.headers.get("Content-Length", "0")))
        signature = self.headers.get("X-Hub-Signature-256", "")
        expected = hmac.new(GITHUB_WEBHOOK_SECRET, body, hashlib.sha256).hexdigest()
        if not signature.startswith("sha256=") or not hmac.compare_digest(signature[7:], expected):
            self.send_response(401); self.end_headers(); return
        payload = json.loads(body)
        event = self.headers.get("X-GitHub-Event", "")
        if event == "issues" and payload.get("action") in {"opened", "closed", "reopened", "labeled", "unlabeled"}:
            asyncio.run_coroutine_threadsafe(sync_issue_to_discord(payload["issue"]), bot.loop)
        elif event == "issue_comment":
            asyncio.run_coroutine_threadsafe(forward_github_comment(payload), bot.loop)
        self.send_response(204); self.end_headers()

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


def start_webhook_server():
    ThreadingHTTPServer(("0.0.0.0", PORT), WebhookHandler).serve_forever()


async def main():
    init_db()
    Thread(target=start_webhook_server, daemon=True).start()
    await bot.start(DISCORD_TOKEN)


if __name__ == "__main__":
    asyncio.run(main())
