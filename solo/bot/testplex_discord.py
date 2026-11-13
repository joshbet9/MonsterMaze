"""Testplex announcements and GitHub game-tracker index for the existing bot."""
import asyncio
import json
import os
import sqlite3
import urllib.error
import urllib.request

import discord
import monster_bot_v2 as base

GITHUB_REPOSITORY = os.getenv("TESTPLEX_GITHUB_REPOSITORY", "joshbet9/testplex")
GITHUB_API = "https://api.github.com/repos/" + GITHUB_REPOSITORY
DEFAULT_GUILD_ID = 1568187449715196035
DEFAULT_CHANGELOG_CHANNEL_ID = 1570610726903611503
DEFAULT_ACTIVITY_CHANNEL_ID = 1570610748282118234
DEFAULT_BUG_CHANNEL_ID = 1568187896890925167
DEFAULT_IDEA_CHANNEL_ID = 1568187934140538900
INDEX_INTERVAL_DEFAULT = 3600
DISCORD_MESSAGE_LIMIT = 1900


def safe_repository_allowlist(value):
    return frozenset(item.strip().casefold() for item in value.split(",") if item.strip())


def split_discord_messages(text, limit=DISCORD_MESSAGE_LIMIT):
    """Split text into Discord-safe chunks without dropping any characters."""
    if limit < 1:
        raise ValueError("message limit must be positive")
    remaining = str(text or "")
    if not remaining:
        return [""]
    chunks = []
    while len(remaining) > limit:
        split_at = remaining.rfind("\n", 0, limit + 1)
        if split_at <= 0:
            split_at = limit
        else:
            split_at += 1
        chunks.append(remaining[:split_at])
        remaining = remaining[split_at:]
    chunks.append(remaining)
    return chunks


def format_release(repository, release):
    """Return every release-note character as one or more Discord messages."""
    title = (release.get("name") or release.get("tag_name") or "New release").strip()
    lines = [f"📦 **{repository} — {title}**"]
    if release.get("tag_name"):
        lines.append(f"Release: `{release['tag_name']}`")
    body = release.get("body") or ""
    if body:
        lines.append(body)
    if release.get("html_url"):
        lines.append(release["html_url"])
    author = release.get("author", {}).get("login")
    if author:
        lines.append(f"Published by {author}")
    return split_discord_messages("\n\n".join(lines))


def format_issue_description(issue, kind="Feedback"):
    heading = f"📝 **{kind} · #{issue['number']} — {issue['title']}**"
    body = (issue.get("body") or "").strip() or "_No description provided._"
    return split_discord_messages(
        f"{heading}\n{issue.get('html_url', '')}\n\n{format_subissue(issue)}\n\n{body}"
    )


def format_issue_comment(comment):
    author = comment.get("user") or {}
    heading = f"💬 **{author.get('login') or 'GitHub user'} commented**"
    if comment.get("html_url"):
        heading += f" · [View on GitHub]({comment['html_url']})"
    body = (comment.get("body") or "").strip() or "_(empty comment)_"
    if comment.get("updated_at") and comment.get("created_at") and comment["updated_at"] != comment["created_at"]:
        heading += " _(edited)_"
    return split_discord_messages(f"{heading}\n\n{body}")


def format_subissue(issue):
    status = "🟢 Open" if issue.get("state") == "open" else "✅ Closed"
    labels = ", ".join(label.get("name", "") for label in issue.get("labels", []) if label.get("name"))
    suffix = f" · `{labels}`" if labels else ""
    return f"{status} · [#{issue['number']} {issue['title']}]({issue['html_url']}){suffix}"


def initial_state_changed(previous_state, current_state, previous_starter=None, current_starter=None):
    if previous_state is None:
        return False
    return previous_state != current_state or (
        current_state == "starting" and bool(current_starter) and previous_starter != current_starter
    )


def _github_json_sync(path):
    token = os.getenv("TESTPLEX_GITHUB_TOKEN", "").strip()
    headers = {
        "Accept": "application/vnd.github+json",
        "X-GitHub-Api-Version": "2022-11-28",
        "User-Agent": "Testplex-Discord-Announcements/1",
    }
    if token:
        headers["Authorization"] = f"Bearer {token}"
    request = urllib.request.Request(GITHUB_API + path, headers=headers)
    try:
        with urllib.request.urlopen(request, timeout=20) as response:
            return json.loads(response.read().decode("utf-8"))
    except urllib.error.HTTPError as exc:
        raise RuntimeError(f"GitHub API returned HTTP {exc.code} for {path}") from exc


async def _github_json(path):
    return await asyncio.to_thread(_github_json_sync, path)


async def _github_json_pages(path, page_size=100):
    """Fetch every page of a GitHub list endpoint."""
    results = []
    separator = "&" if "?" in path else "?"
    for page in range(1, 101):
        rows = await _github_json(f"{path}{separator}page={page}")
        if not isinstance(rows, list):
            raise RuntimeError(f"GitHub list endpoint returned a non-list for {path}")
        results.extend(rows)
        if len(rows) < page_size:
            return results
    raise RuntimeError(f"GitHub list endpoint exceeded 100 pages for {path}")


def _ensure_schema():
    conn = base.db()
    try:
        conn.execute(
            "CREATE TABLE IF NOT EXISTS testplex_tracker_threads ("
            "tracker_issue_number INTEGER PRIMARY KEY, discord_thread_id TEXT NOT NULL)"
        )
        conn.execute(
            "CREATE TABLE IF NOT EXISTS testplex_subissue_messages ("
            "issue_number INTEGER PRIMARY KEY, tracker_issue_number INTEGER NOT NULL, "
            "discord_message_id TEXT NOT NULL)"
        )
        conn.execute(
            "CREATE TABLE IF NOT EXISTS testplex_release_posts (release_id TEXT PRIMARY KEY)"
        )
        conn.execute(
            "CREATE TABLE IF NOT EXISTS testplex_discussion_messages ("
            "item_key TEXT PRIMARY KEY, tracker_issue_number INTEGER NOT NULL, "
            "issue_number INTEGER NOT NULL, discord_message_ids TEXT NOT NULL)"
        )
        conn.commit()
    finally:
        conn.close()


def _configured_id(cfg, key, env_key, default):
    return int(cfg.get(key) or os.getenv(env_key, default))


async def _channel(bot, cfg, key, env_key, default):
    channel_id = _configured_id(cfg, key, env_key, default)
    channel = bot.get_channel(channel_id) or await bot.fetch_channel(channel_id)
    guild_id = _configured_id(cfg, "testplex_discord_guild_id", "TESTPLEX_DISCORD_GUILD_ID", DEFAULT_GUILD_ID)
    guild = getattr(channel, "guild", None)
    if guild is None or guild.id != guild_id:
        raise RuntimeError(f"Discord channel {channel_id} is not in Testplex guild {guild_id}")
    return channel


def _tracker_thread_id(issue_number):
    conn = base.db()
    try:
        row = conn.execute(
            "SELECT discord_thread_id FROM testplex_tracker_threads WHERE tracker_issue_number=?",
            (issue_number,),
        ).fetchone()
        return int(row[0]) if row else None
    finally:
        conn.close()


def _save_tracker_thread(issue_number, thread_id):
    conn = base.db()
    try:
        conn.execute(
            "INSERT INTO testplex_tracker_threads(tracker_issue_number, discord_thread_id) VALUES (?, ?) "
            "ON CONFLICT(tracker_issue_number) DO UPDATE SET discord_thread_id=excluded.discord_thread_id",
            (issue_number, str(thread_id)),
        )
        conn.commit()
    finally:
        conn.close()


def _subissue_message(issue_number):
    conn = base.db()
    try:
        row = conn.execute(
            "SELECT tracker_issue_number, discord_message_id FROM testplex_subissue_messages WHERE issue_number=?",
            (issue_number,),
        ).fetchone()
        return (int(row[0]), int(row[1])) if row else None
    finally:
        conn.close()


def _save_subissue_message(issue_number, tracker_number, message_id):
    conn = base.db()
    try:
        conn.execute(
            "INSERT INTO testplex_subissue_messages(issue_number, tracker_issue_number, discord_message_id) "
            "VALUES (?, ?, ?) ON CONFLICT(issue_number) DO UPDATE SET "
            "tracker_issue_number=excluded.tracker_issue_number, discord_message_id=excluded.discord_message_id",
            (issue_number, tracker_number, str(message_id)),
        )
        conn.commit()
    finally:
        conn.close()


def _discussion_message_ids(item_key):
    conn = base.db()
    try:
        row = conn.execute(
            "SELECT discord_message_ids FROM testplex_discussion_messages WHERE item_key=?",
            (item_key,),
        ).fetchone()
        return json.loads(row[0]) if row else []
    finally:
        conn.close()


def _save_discussion_message_ids(item_key, tracker_number, issue_number, message_ids):
    conn = base.db()
    try:
        conn.execute(
            "INSERT INTO testplex_discussion_messages(item_key, tracker_issue_number, issue_number, discord_message_ids) "
            "VALUES (?, ?, ?, ?) ON CONFLICT(item_key) DO UPDATE SET "
            "tracker_issue_number=excluded.tracker_issue_number, issue_number=excluded.issue_number, "
            "discord_message_ids=excluded.discord_message_ids",
            (item_key, tracker_number, issue_number, json.dumps([str(value) for value in message_ids])),
        )
        conn.commit()
    finally:
        conn.close()


async def _sync_message_bundle(thread, item_key, tracker_number, issue_number, contents):
    old_ids = _discussion_message_ids(item_key)
    new_ids = []
    for index, content in enumerate(contents):
        message = None
        if index < len(old_ids):
            try:
                message = await thread.fetch_message(int(old_ids[index]))
            except discord.NotFound:
                message = None
        if message is None:
            message = await thread.send(content, allowed_mentions=discord.AllowedMentions.none())
        elif message.content != content:
            await message.edit(content=content, allowed_mentions=discord.AllowedMentions.none())
        new_ids.append(message.id)
    for stale_id in old_ids[len(contents):]:
        try:
            await thread.get_partial_message(int(stale_id)).delete()
        except discord.HTTPException:
            pass
    _save_discussion_message_ids(item_key, tracker_number, issue_number, new_ids)


async def _delete_issue_discussion(thread, tracker_number, issue_number):
    conn = base.db()
    try:
        rows = conn.execute(
            "SELECT item_key, discord_message_ids FROM testplex_discussion_messages "
            "WHERE tracker_issue_number=? AND issue_number=?",
            (tracker_number, issue_number),
        ).fetchall()
        for item_key, encoded_ids in rows:
            for message_id in json.loads(encoded_ids):
                try:
                    await thread.get_partial_message(int(message_id)).delete()
                except discord.HTTPException:
                    pass
            conn.execute("DELETE FROM testplex_discussion_messages WHERE item_key=?", (item_key,))
        conn.commit()
    finally:
        conn.close()


async def _sync_issue_discussion(thread, tracker_number, issue):
    issue_number = issue["number"]
    body_key = f"issue:{issue_number}:body"
    await _sync_message_bundle(
        thread, body_key, tracker_number, issue_number,
        format_issue_description(issue, "Game tracker" if issue_number == tracker_number else "Feedback"),
    )
    comments = await _github_json_pages(f"/issues/{issue_number}/comments?per_page=100")
    current_keys = set()
    for comment in comments:
        item_key = f"comment:{comment['id']}"
        current_keys.add(item_key)
        await _sync_message_bundle(
            thread, item_key, tracker_number, issue_number, format_issue_comment(comment)
        )
    conn = base.db()
    try:
        stale = conn.execute(
            "SELECT item_key, discord_message_ids FROM testplex_discussion_messages "
            "WHERE tracker_issue_number=? AND issue_number=?",
            (tracker_number, issue_number),
        ).fetchall()
        for item_key, encoded_ids in stale:
            if item_key.startswith("comment:") and item_key not in current_keys:
                for message_id in json.loads(encoded_ids):
                    try:
                        await thread.get_partial_message(int(message_id)).delete()
                    except discord.HTTPException:
                        pass
                conn.execute("DELETE FROM testplex_discussion_messages WHERE item_key=?", (item_key,))
        conn.commit()
    finally:
        conn.close()


async def _ensure_tracker_thread(bot, cfg, tracker):
    thread_id = _tracker_thread_id(tracker["number"])
    if thread_id:
        try:
            thread = bot.get_channel(thread_id) or await bot.fetch_channel(thread_id)
            if getattr(thread, "archived", False):
                await thread.edit(archived=False)
            return thread
        except discord.HTTPException:
            pass
    channel = await _channel(bot, cfg, "testplex_issue_index_channel", "TESTPLEX_ISSUE_INDEX_CHANNEL_ID", DEFAULT_BUG_CHANNEL_ID)
    thread_name = f"Game Tracker #{tracker['number']} — {tracker['title']}"[:100]
    intro = f"GitHub tracker: {tracker['html_url']}\nSub-issues are synchronized below."
    if isinstance(channel, discord.ForumChannel):
        thread, _ = await channel.create_thread(
            name=thread_name,
            content=intro,
            auto_archive_duration=10080,
            allowed_mentions=discord.AllowedMentions.none(),
        )
    else:
        thread = await channel.create_thread(
            name=thread_name,
            type=discord.ChannelType.public_thread,
            auto_archive_duration=10080,
        )
    _save_tracker_thread(tracker["number"], thread.id)
    if not isinstance(channel, discord.ForumChannel):
        await thread.send(intro, allowed_mentions=discord.AllowedMentions.none())
    return thread


async def _sync_tracker(bot, cfg, tracker):
    thread = await _ensure_tracker_thread(bot, cfg, tracker)
    await _sync_issue_discussion(thread, tracker["number"], tracker)
    children = await _github_json_pages(f"/issues/{tracker['number']}/sub_issues?per_page=100")
    children = sorted(children, key=lambda item: item["number"])
    current_numbers = {child["number"] for child in children}
    for child in children:
        previous = _subissue_message(child["number"])
        message = None
        if previous:
            old_tracker, old_message_id = previous
            if old_tracker == tracker["number"]:
                try:
                    message = await thread.fetch_message(old_message_id)
                except discord.NotFound:
                    message = None
            else:
                old_thread_id = _tracker_thread_id(old_tracker)
                if old_thread_id:
                    try:
                        old_thread = bot.get_channel(old_thread_id) or await bot.fetch_channel(old_thread_id)
                        await old_thread.get_partial_message(old_message_id).delete()
                    except discord.HTTPException:
                        pass
        content = format_subissue(child)
        if message is None:
            message = await thread.send(content, allowed_mentions=discord.AllowedMentions.none())
        elif message.content != content:
            await message.edit(content=content, allowed_mentions=discord.AllowedMentions.none())
        _save_subissue_message(child["number"], tracker["number"], message.id)
        await _sync_issue_discussion(thread, tracker["number"], child)

    conn = base.db()
    try:
        stale = conn.execute(
            "SELECT issue_number, discord_message_id FROM testplex_subissue_messages WHERE tracker_issue_number=?",
            (tracker["number"],),
        ).fetchall()
    finally:
        conn.close()
    for issue_number, message_id in stale:
        if issue_number not in current_numbers:
            try:
                await thread.get_partial_message(int(message_id)).delete()
            except discord.HTTPException:
                pass
            conn = base.db()
            try:
                conn.execute("DELETE FROM testplex_subissue_messages WHERE issue_number=?", (issue_number,))
                conn.commit()
            finally:
                conn.close()
            await _delete_issue_discussion(thread, tracker["number"], issue_number)


async def sync_all_trackers(bot, cfg):
    _ensure_schema()
    try:
        trackers = await _github_json_pages("/issues?state=all&labels=game-tracker&per_page=100")
        for tracker in trackers:
            await _sync_tracker(bot, cfg, tracker)
        print(f"[testplex-discord] synchronized {len(trackers)} game tracker threads", flush=True)
    except Exception as exc:
        print(f"[testplex-discord] issue index sync failed: {exc}", flush=True)


async def post_release(bot, cfg, release):
    if release.get("draft") or release.get("prerelease"):
        return
    _ensure_schema()
    release_id = str(release.get("id") or release.get("tag_name") or release.get("html_url", ""))
    if not release_id:
        return
    conn = base.db()
    try:
        already_posted = conn.execute(
            "SELECT 1 FROM testplex_release_posts WHERE release_id=?", (release_id,)
        ).fetchone()
    finally:
        conn.close()
    if already_posted:
        return
    channel = await _channel(bot, cfg, "testplex_changelog_channel", "TESTPLEX_CHANGELOG_CHANNEL_ID", DEFAULT_CHANGELOG_CHANNEL_ID)
    for content in format_release(GITHUB_REPOSITORY, release):
        await channel.send(content, allowed_mentions=discord.AllowedMentions.none())
    conn = base.db()
    try:
        conn.execute("INSERT OR IGNORE INTO testplex_release_posts(release_id) VALUES (?)", (release_id,))
        conn.commit()
    finally:
        conn.close()


async def handle_github_event(bot, cfg, event, payload):
    repository = payload.get("repository", {}).get("full_name", "")
    allowed = safe_repository_allowlist(os.getenv("TESTPLEX_GITHUB_REPOSITORY", "joshbet9/testplex"))
    if repository.casefold() not in allowed:
        return
    if event == "release" and payload.get("action") == "published":
        await post_release(bot, cfg, payload.get("release", {}))
    elif event == "issues" and payload.get("action") in {"opened", "closed", "reopened", "edited", "labeled", "unlabeled"}:
        # Allow the native parent/sub-issue relation update to settle before reconciling.
        await asyncio.sleep(1)
        await sync_all_trackers(bot, cfg)
    elif event == "issue_comment" and payload.get("action") in {"created", "edited", "deleted"}:
        await sync_all_trackers(bot, cfg)


async def reconciliation_loop(bot, cfg):
    _ensure_schema()
    await sync_all_trackers(bot, cfg)
    interval = max(900, int(cfg.get("testplex_issue_sync_interval", os.getenv("TESTPLEX_ISSUE_SYNC_INTERVAL", INDEX_INTERVAL_DEFAULT))))
    while True:
        await asyncio.sleep(interval)
        await sync_all_trackers(bot, cfg)


def start(bot, cfg):
    task = getattr(bot, "testplex_issue_index_task", None)
    if task is None or task.done():
        bot.testplex_issue_index_task = asyncio.create_task(reconciliation_loop(bot, cfg))
