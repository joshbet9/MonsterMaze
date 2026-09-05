import hashlib
import hmac
import os
import sqlite3
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from urllib.parse import urlparse
from urllib.request import Request, urlopen
import json

DISCORD_TOKEN = os.environ["DISCORD_TOKEN"]
GITHUB_TOKEN = os.environ["GITHUB_TOKEN"]
GITHUB_REPOSITORY = os.environ["GITHUB_REPOSITORY"]
GITHUB_WEBHOOK_SECRET = os.environ["GITHUB_WEBHOOK_SECRET"].encode()
BUG_CHANNEL_ID = int(os.environ["DISCORD_BUG_CHANNEL_ID"])
IDEA_CHANNEL_ID = int(os.environ["DISCORD_IDEA_CHANNEL_ID"])
DB_PATH = os.getenv("DATABASE_PATH", "data/bridge.sqlite3")
PORT = int(os.getenv("PORT", "8080"))

os.makedirs(os.path.dirname(DB_PATH) or ".", exist_ok=True)

def db():
    conn = sqlite3.connect(DB_PATH)
    conn.execute("""
        CREATE TABLE IF NOT EXISTS mappings (
            discord_thread_id TEXT PRIMARY KEY,
            github_issue_number INTEGER UNIQUE NOT NULL,
            discord_channel_id TEXT NOT NULL
        )
    """)
    conn.commit()
    return conn

def github_request(method, path, payload=None):
    url = f"https://api.github.com/repos/{GITHUB_REPOSITORY}{path}"
    body = json.dumps(payload).encode() if payload is not None else None
    req = Request(url, data=body, method=method, headers={
        "Accept": "application/vnd.github+json",
        "Authorization": f"Bearer {GITHUB_TOKEN}",
        "X-GitHub-Api-Version": "2022-11-28",
        "Content-Type": "application/json",
        "User-Agent": "MonsterMaze-Discord-GitHub-Bridge",
    })
    with urlopen(req, timeout=20) as response:
        return json.loads(response.read())

def discord_request(method, path, payload=None):
    url = f"https://discord.com/api/v10{path}"
    body = json.dumps(payload).encode() if payload is not None else None
    req = Request(url, data=body, method=method, headers={
        "Authorization": f"Bot {DISCORD_TOKEN}",
        "Content-Type": "application/json",
        "User-Agent": "MonsterMaze-Discord-GitHub-Bridge",
    })
    with urlopen(req, timeout=20) as response:
        raw = response.read()
        return json.loads(raw) if raw else None

def verify_signature(body, signature):
    if not signature or not signature.startswith("sha256="):
        return False
    expected = hmac.new(GITHUB_WEBHOOK_SECRET, body, hashlib.sha256).hexdigest()
    return hmac.compare_digest(signature[7:], expected)

def issue_labels(channel_id):
    return ["bug"] if channel_id == BUG_CHANNEL_ID else ["idea"]

def mapping_for_issue(issue_number):
    conn = db()
    row = conn.execute(
        "SELECT discord_thread_id FROM mappings WHERE github_issue_number = ?",
        (issue_number,),
    ).fetchone()
    conn.close()
    return row[0] if row else None

def create_issue_from_thread(thread_id, channel_id, title, message, thread_url):
    existing = db().execute(
        "SELECT github_issue_number FROM mappings WHERE discord_thread_id = ?", (thread_id,)
    ).fetchone()
    if existing:
        return existing[0]

    labels = issue_labels(channel_id)
    body = (
        "## Discord report\n\n"
        f"{message}\n\n"
        "---\n"
        f"Discord thread: {thread_url}\n"
        f"<!-- monster-maze-discord-thread:{thread_id} -->"
    )
    issue = github_request("POST", "/issues", {
        "title": title[:256],
        "body": body,
        "labels": labels,
    })
    conn = db()
    conn.execute(
        "INSERT INTO mappings(discord_thread_id, github_issue_number, discord_channel_id) VALUES (?, ?, ?)",
        (thread_id, issue["number"], str(channel_id)),
    )
    conn.commit()
    conn.close()
    return issue["number"]

def post_issue_link(thread_id, issue):
    discord_request("POST", f"/channels/{thread_id}/messages", {
        "content": f"🐛 GitHub issue created: {issue['html_url']}\nLabels: {', '.join(issue.get('labels', [])) or 'none'}"
    })

def sync_issue_to_discord(issue):
    thread_id = mapping_for_issue(issue["number"])
    if not thread_id:
        return
    state = issue["state"]
    labels = ", ".join(label["name"] for label in issue.get("labels", [])) or "none"
    prefix = "🟢 Open" if state == "open" else "✅ Closed"
    discord_request("POST", f"/channels/{thread_id}/messages", {
        "content": f"{prefix} — GitHub issue #{issue['number']} updated. Labels: {labels}\n{issue['html_url']}"
    })

def handle_github_event(event, body):
    if event == "issues":
        action = body.get("action")
        if action in {"labeled", "unlabeled", "opened", "closed", "reopened"}:
            sync_issue_to_discord(body["issue"])
    elif event == "issue_comment" and body.get("action") == "created":
        issue = body["issue"]
        thread_id = mapping_for_issue(issue["number"])
        if thread_id:
            comment = body["comment"]["body"]
            if "<!-- monster-maze-discord-bridge -->" not in comment:
                author = body["comment"]["user"]["login"]
                discord_request("POST", f"/channels/{thread_id}/messages", {
                    "content": f"💬 GitHub comment from **{author}**:\n{comment}"
                })

class Handler(BaseHTTPRequestHandler):
    def do_POST(self):
        parsed = urlparse(self.path)
        length = int(self.headers.get("Content-Length", "0"))
        body = self.rfile.read(length)
        if parsed.path != "/github/webhook":
            self.send_response(404); self.end_headers(); return
        if not verify_signature(body, self.headers.get("X-Hub-Signature-256")):
            self.send_response(401); self.end_headers(); return
        try:
            handle_github_event(self.headers.get("X-GitHub-Event", ""), json.loads(body))
            self.send_response(204); self.end_headers()
        except Exception as exc:
            print(f"webhook error: {exc}", flush=True)
            self.send_response(500); self.end_headers()

    def log_message(self, fmt, *args):
        print(fmt % args, flush=True)

if __name__ == "__main__":
    print(f"Monster Maze Discord/GitHub bridge listening on :{PORT}", flush=True)
    ThreadingHTTPServer(("0.0.0.0", PORT), Handler).serve_forever()
