"""Testplex announcements and GitHub game-tracker index for the existing bot."""
import asyncio
import base64
import json
import os
import re
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
DISCORD_WRITE_LOCK = asyncio.Lock()
FEEDBACK_DIRECTORY_CACHE = None


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
    body = issue.get("body") or ""
    if not body.strip():
        body = "_No description provided._"
    return split_discord_messages(
        f"{heading}\n{issue.get('html_url', '')}\n\n{format_subissue(issue)}\n\n{body}"
    )


def format_issue_comment(comment):
    author = comment.get("user") or {}
    heading = f"💬 **{author.get('login') or 'GitHub user'} commented**"
    if comment.get("html_url"):
        heading += f" · [View on GitHub]({comment['html_url']})"
    body = comment.get("body") or ""
    if not body.strip():
        body = "_(empty comment)_"
    if comment.get("updated_at") and comment.get("created_at") and comment["updated_at"] != comment["created_at"]:
        heading += " _(edited)_"
    return split_discord_messages(f"{heading}\n\n{body}")


def format_discord_reply(message, tracker_number):
    """Format a Discord tracker-thread reply as a non-pinging GitHub comment."""
    author = getattr(message.author, "display_name", None) or getattr(message.author, "name", "Discord user")
    author = str(author).replace("`", "ˋ").replace("\r", " ").replace("\n", " ")
    content = (getattr(message, "content", "") or "").replace("@", "@\u200b").strip()
    attachments = [getattr(item, "url", "") for item in getattr(message, "attachments", [])]
    attachments = [url for url in attachments if url]
    if not content and not attachments:
        return None
    lines = [f"**Discord reply from** `{author}`"]
    guild_id = getattr(getattr(message, "guild", None), "id", None)
    channel_id = getattr(getattr(message, "channel", None), "id", None)
    message_id = getattr(message, "id", None)
    if guild_id and channel_id and message_id:
        lines.append(f"[View in Discord](https://discord.com/channels/{guild_id}/{channel_id}/{message_id})")
    lines.append("")
    if content:
        lines.append(content)
    for url in attachments:
        lines.append(f"Attachment: {url}")
    lines.extend(["", f"<!-- testplex-discord-message:{tracker_number}:{channel_id}:{message_id} -->"])
    return "\n".join(lines)


def is_discord_mirrored_comment(comment):
    body = comment.get("body") or ""
    return "<!-- testplex-discord-message:" in body


def feedback_game_from_issue(issue):
    body = issue.get("body") or ""
    match = re.search(r"^###\s+Game\s*\r?\n+([^\r\n]+)", body, re.MULTILINE)
    return match.group(1).strip() if match else None


def feedback_kind_from_issue(issue):
    body = issue.get("body") or ""
    match = re.search(r"<!--\s*testplex-feedback:type=(bug|idea)\s*-->", body)
    if match:
        return match.group(1)
    labels = {label.get("name", "") for label in issue.get("labels", [])}
    if "tester-idea" in labels or "enhancement" in labels:
        return "idea"
    return "bug"


def feedback_game_key(game_name):
    key = re.sub(r"[^a-z0-9]+", "-", str(game_name).casefold()).strip("-")
    return key[:90] or "unknown-game"


def feedback_forum_key(kind, game_name, tracker):
    game_key = tracker.get("slug") or feedback_game_key(game_name)
    return f"{kind}:{game_key}"


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


def _github_create_issue_comment_sync(issue_number, body):
    token = os.getenv("TESTPLEX_GITHUB_TOKEN", "").strip()
    if not token:
        raise RuntimeError("TESTPLEX_GITHUB_TOKEN is required to publish Discord replies")
    path = f"/issues/{int(issue_number)}/comments"
    request = urllib.request.Request(
        GITHUB_API + path,
        data=json.dumps({"body": body}).encode("utf-8"),
        headers={
            "Accept": "application/vnd.github+json",
            "Authorization": f"Bearer {token}",
            "X-GitHub-Api-Version": "2022-11-28",
            "User-Agent": "Testplex-Discord-Announcements/1",
            "Content-Type": "application/json",
        },
        method="POST",
    )
    try:
        with urllib.request.urlopen(request, timeout=20) as response:
            return json.loads(response.read().decode("utf-8"))
    except urllib.error.HTTPError as exc:
        raise RuntimeError(f"GitHub API returned HTTP {exc.code} for {path}") from exc


async def _github_create_issue_comment(issue_number, body):
    return await asyncio.to_thread(_github_create_issue_comment_sync, issue_number, body)


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
        conn.execute(
            "CREATE TABLE IF NOT EXISTS testplex_discord_github_comments ("
            "discord_message_id TEXT PRIMARY KEY, github_issue_number INTEGER NOT NULL, "
            "github_comment_id TEXT NOT NULL, github_comment_url TEXT NOT NULL)"
        )
        conn.execute(
            "CREATE TABLE IF NOT EXISTS testplex_feedback_forums ("
            "forum_key TEXT PRIMARY KEY, discord_channel_id TEXT NOT NULL)"
        )
        conn.execute(
            "CREATE TABLE IF NOT EXISTS testplex_feedback_threads ("
            "issue_number INTEGER PRIMARY KEY, tracker_issue_number INTEGER NOT NULL, "
            "forum_key TEXT NOT NULL, discord_thread_id TEXT NOT NULL)"
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


async def _load_feedback_directory():
    global FEEDBACK_DIRECTORY_CACHE
    if FEEDBACK_DIRECTORY_CACHE is not None:
        return FEEDBACK_DIRECTORY_CACHE
    source = await _github_json("/contents/docs/tester-feedback-trackers.json")
    encoded = source.get("content", "")
    if source.get("encoding") != "base64" or not encoded:
        raise RuntimeError("GitHub returned an invalid tester-feedback tracker directory")
    directory = json.loads(base64.b64decode(encoded).decode("utf-8"))
    games = directory.get("games")
    if not isinstance(games, dict) or not games:
        raise RuntimeError("tester-feedback tracker directory has no games")
    FEEDBACK_DIRECTORY_CACHE = games
    return games


async def _ensure_feedback_forums(bot, cfg, games):
    guild_id = _configured_id(cfg, "testplex_discord_guild_id", "TESTPLEX_DISCORD_GUILD_ID", DEFAULT_GUILD_ID)
    guild = bot.get_guild(guild_id) or await bot.fetch_guild(guild_id)
    categories = {}
    for kind, category_name in (("bug", "Testplex Bug Reports"), ("idea", "Testplex Ideas")):
        category = next((item for item in guild.categories if item.name.casefold() == category_name.casefold()), None)
        if category is None:
            category = await guild.create_category(category_name, reason="Set up per-game Testplex feedback forums")
        categories[kind] = category

    for game_name, tracker in games.items():
        # Multiple selectable modes may intentionally share one game tracker.
        # Use the catalog slug as identity so they also share one bug and idea
        # forum instead of creating duplicate channels from selector labels.
        for kind in ("bug", "idea"):
            forum_key = feedback_forum_key(kind, game_name, tracker)
            game_key = forum_key.split(":", 1)[1]
            channel_id = _feedback_forum_id(forum_key)
            forum = None
            if channel_id:
                try:
                    forum = bot.get_channel(channel_id) or await bot.fetch_channel(channel_id)
                except discord.HTTPException:
                    forum = None
            if forum is not None and (
                not isinstance(forum, discord.ForumChannel)
                or getattr(getattr(forum, "guild", None), "id", None) != guild_id
                or getattr(forum, "category_id", None) != categories[kind].id
                or forum.name != game_key
            ):
                forum = None
            if forum is None or not isinstance(forum, discord.ForumChannel):
                forum = next((item for item in guild.channels
                              if isinstance(item, discord.ForumChannel)
                              and item.category_id == categories[kind].id
                              and item.name == game_key), None)
            if forum is None:
                forum = await guild.create_forum(
                    name=game_key,
                    category=categories[kind],
                    topic=f"{game_name} {kind} reports. GitHub tracker: #{tracker['issue_number']}.",
                    reason="Create a per-game Testplex feedback forum",
                )
            _save_feedback_forum(forum_key, forum.id)


def _feedback_thread_name(issue):
    title = re.sub(r"\s+", " ", issue.get("title") or "Tester feedback").strip()
    return f"#{issue['number']} {title}"[:100]


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


def _tracker_for_thread(thread_id):
    conn = base.db()
    try:
        row = conn.execute(
            "SELECT tracker_issue_number FROM testplex_tracker_threads WHERE discord_thread_id=?",
            (str(thread_id),),
        ).fetchone()
        return int(row[0]) if row else None
    finally:
        conn.close()


def _feedback_thread(issue_number):
    conn = base.db()
    try:
        row = conn.execute(
            "SELECT tracker_issue_number, forum_key, discord_thread_id "
            "FROM testplex_feedback_threads WHERE issue_number=?",
            (issue_number,),
        ).fetchone()
        return (int(row[0]), row[1], int(row[2])) if row else None
    finally:
        conn.close()


def _feedback_issue_for_thread(thread_id):
    conn = base.db()
    try:
        row = conn.execute(
            "SELECT issue_number FROM testplex_feedback_threads WHERE discord_thread_id=?",
            (str(thread_id),),
        ).fetchone()
        return int(row[0]) if row else None
    finally:
        conn.close()


def _feedback_forum_id(forum_key):
    conn = base.db()
    try:
        row = conn.execute(
            "SELECT discord_channel_id FROM testplex_feedback_forums WHERE forum_key=?",
            (forum_key,),
        ).fetchone()
        return int(row[0]) if row else None
    finally:
        conn.close()


def _is_feedback_forum(channel_id):
    conn = base.db()
    try:
        return conn.execute(
            "SELECT 1 FROM testplex_feedback_forums WHERE discord_channel_id=? LIMIT 1",
            (str(channel_id),),
        ).fetchone() is not None
    finally:
        conn.close()


def _save_feedback_forum(forum_key, channel_id):
    conn = base.db()
    try:
        conn.execute(
            "INSERT INTO testplex_feedback_forums(forum_key, discord_channel_id) VALUES (?, ?) "
            "ON CONFLICT(forum_key) DO UPDATE SET discord_channel_id=excluded.discord_channel_id",
            (forum_key, str(channel_id)),
        )
        conn.commit()
    finally:
        conn.close()


def _save_feedback_thread(issue_number, tracker_number, forum_key, thread_id):
    conn = base.db()
    try:
        conn.execute(
            "INSERT INTO testplex_feedback_threads(issue_number, tracker_issue_number, forum_key, discord_thread_id) "
            "VALUES (?, ?, ?, ?) ON CONFLICT(issue_number) DO UPDATE SET "
            "tracker_issue_number=excluded.tracker_issue_number, forum_key=excluded.forum_key, "
            "discord_thread_id=excluded.discord_thread_id",
            (issue_number, tracker_number, forum_key, str(thread_id)),
        )
        conn.commit()
    finally:
        conn.close()


def _synced_github_comment(message_id):
    conn = base.db()
    try:
        return conn.execute(
            "SELECT github_comment_url FROM testplex_discord_github_comments WHERE discord_message_id=?",
            (str(message_id),),
        ).fetchone()
    finally:
        conn.close()


def _save_github_comment(message_id, github_issue_number, comment):
    conn = base.db()
    try:
        conn.execute(
            "INSERT OR IGNORE INTO testplex_discord_github_comments "
            "(discord_message_id, github_issue_number, github_comment_id, github_comment_url) "
            "VALUES (?, ?, ?, ?)",
            (str(message_id), github_issue_number, str(comment["id"]), comment.get("html_url", "")),
        )
        conn.commit()
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
    finally:
        conn.close()
    for item_key, encoded_ids in rows:
        for message_id in json.loads(encoded_ids):
            try:
                await thread.get_partial_message(int(message_id)).delete()
            except discord.HTTPException:
                pass
        conn = base.db()
        try:
            conn.execute("DELETE FROM testplex_discussion_messages WHERE item_key=?", (item_key,))
            conn.commit()
        finally:
            conn.close()


async def _sync_issue_discussion(thread, tracker_number, issue, kind=None):
    issue_number = issue["number"]
    body_key = f"issue:{issue_number}:body"
    if kind is None:
        kind = feedback_kind_from_issue(issue)
    description_kind = "Bug report" if kind == "bug" else "Game idea"
    await _sync_message_bundle(
        thread, body_key, tracker_number, issue_number,
        format_issue_description(issue, description_kind),
    )
    comments = await _github_json_pages(f"/issues/{issue_number}/comments?per_page=100")
    current_keys = set()
    for comment in comments:
        if is_discord_mirrored_comment(comment):
            continue
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


async def _ensure_feedback_thread(bot, games, issue, fallback_game=None):
    game_name = feedback_game_from_issue(issue) or fallback_game
    route = games.get(game_name) if game_name else None
    if route is None:
        raise RuntimeError(f"feedback issue #{issue['number']} has no configured Game field")
    kind = feedback_kind_from_issue(issue)
    forum_key = feedback_forum_key(kind, game_name, route)
    tracker_number = int(route["issue_number"])
    existing = _feedback_thread(issue["number"])
    if existing:
        old_tracker_number, old_forum_key, old_thread_id = existing
        try:
            thread = bot.get_channel(old_thread_id) or await bot.fetch_channel(old_thread_id)
        except discord.HTTPException:
            thread = None
        if thread is not None and old_forum_key == forum_key:
            if getattr(thread, "archived", False):
                await thread.edit(archived=False)
            if getattr(thread, "name", "") != _feedback_thread_name(issue):
                await thread.edit(name=_feedback_thread_name(issue))
            return thread, tracker_number, kind
        if thread is not None:
            try:
                await thread.send(
                    f"This report moved to a different game/type. Follow it here: {issue.get('html_url', '')}",
                    allowed_mentions=discord.AllowedMentions.none(),
                )
                await thread.edit(archived=True)
            except discord.HTTPException:
                pass

    forum_id = _feedback_forum_id(forum_key)
    if forum_id is None:
        raise RuntimeError(f"Discord forum {forum_key} has not been created")
    forum = bot.get_channel(forum_id) or await bot.fetch_channel(forum_id)
    description_kind = "Bug report" if kind == "bug" else "Game idea"
    chunks = format_issue_description(issue, description_kind)
    thread, starter = await forum.create_thread(
        name=_feedback_thread_name(issue),
        content=chunks[0],
        auto_archive_duration=10080,
        allowed_mentions=discord.AllowedMentions.none(),
    )
    _save_feedback_thread(issue["number"], tracker_number, forum_key, thread.id)
    _save_discussion_message_ids(
        f"issue:{issue['number']}:body", tracker_number, issue["number"], [str(starter.id)]
    )
    return thread, tracker_number, kind


async def _sync_feedback_issue(bot, games, issue, fallback_game=None):
    thread, tracker_number, kind = await _ensure_feedback_thread(bot, games, issue, fallback_game)
    await _sync_issue_discussion(thread, tracker_number, issue, kind)
    if issue.get("state") == "closed" and not getattr(thread, "archived", False):
        await thread.edit(archived=True)


async def _sync_feedback_event_issue(bot, cfg, issue):
    body = issue.get("body") or ""
    if not re.search(r"<!--\s*testplex-feedback:type=(bug|idea)\s*-->", body):
        return False
    games = await _load_feedback_directory()
    await _ensure_feedback_forums(bot, cfg, games)
    await _sync_feedback_issue(bot, games, issue, feedback_game_from_issue(issue))
    return True


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
        games = await _load_feedback_directory()
        await _ensure_feedback_forums(bot, cfg, games)
        trackers = {}
        for game_name, tracker in games.items():
            trackers.setdefault(int(tracker["issue_number"]), game_name)
        issues_by_number = {}
        for tracker_number, fallback_game in trackers.items():
            children = await _github_json_pages(f"/issues/{tracker_number}/sub_issues?per_page=100")
            for issue in children:
                issues_by_number.setdefault(issue["number"], (issue, fallback_game))
        synced = 0
        for issue, fallback_game in issues_by_number.values():
            try:
                await _sync_feedback_issue(bot, games, issue, fallback_game)
                synced += 1
            except Exception as exc:
                print(f"[testplex-discord] failed to sync feedback issue #{issue.get('number')}: {exc}", flush=True)
        print(
            f"[testplex-discord] synchronized {len(games)} game selections and {synced}/{len(issues_by_number)} feedback threads",
            flush=True,
        )
    except Exception as exc:
        print(f"[testplex-discord] feedback forum sync failed: {exc}", flush=True)


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
        issue = payload.get("issue") or {}
        if not await _sync_feedback_event_issue(bot, cfg, issue):
            await sync_all_trackers(bot, cfg)
    elif event == "issue_comment" and payload.get("action") in {"created", "edited", "deleted"}:
        issue = payload.get("issue") or {}
        if not await _sync_feedback_event_issue(bot, cfg, issue):
            await sync_all_trackers(bot, cfg)


async def handle_discord_message(bot, message):
    """Write human replies in mapped feedback threads back to their GitHub issue."""
    author = getattr(message, "author", None)
    if author is None or getattr(author, "bot", False) or getattr(message, "webhook_id", None):
        return False
    bot_user = getattr(bot, "user", None)
    if bot_user is not None and getattr(author, "id", None) == getattr(bot_user, "id", None):
        return False

    cfg = getattr(bot, "cfg", {})
    guild = getattr(message, "guild", None)
    channel = getattr(message, "channel", None)
    if guild is None or channel is None:
        return False
    expected_guild = _configured_id(cfg, "testplex_discord_guild_id", "TESTPLEX_DISCORD_GUILD_ID", DEFAULT_GUILD_ID)
    if guild.id != expected_guild:
        return False

    _ensure_schema()
    parent_id = getattr(channel, "parent_id", None)
    if _is_feedback_forum(parent_id):
        issue_number = _feedback_issue_for_thread(channel.id)
    else:
        expected_parent = _configured_id(cfg, "testplex_issue_index_channel", "TESTPLEX_ISSUE_INDEX_CHANNEL_ID", DEFAULT_BUG_CHANNEL_ID)
        if parent_id != expected_parent:
            return False
        issue_number = _tracker_for_thread(channel.id)
    if issue_number is None:
        return False
    if _synced_github_comment(message.id):
        return True
    body = format_discord_reply(message, issue_number)
    if body is None:
        return False

    async with DISCORD_WRITE_LOCK:
        if _synced_github_comment(message.id):
            return True
        try:
            comment = await _github_create_issue_comment(issue_number, body)
            _save_github_comment(message.id, issue_number, comment)
            url = comment.get("html_url", "")
            print(
                f"[testplex-discord] mirrored Discord message {message.id} to issue #{issue_number}",
                flush=True,
            )
            if url:
                try:
                    await message.reply(
                        f"Posted to GitHub: {url}",
                        allowed_mentions=discord.AllowedMentions.none(),
                    )
                except Exception:
                    pass
            return True
        except Exception as exc:
            print(
                f"[testplex-discord] failed to mirror Discord message {message.id} "
                f"to issue #{issue_number}: {exc}",
                flush=True,
            )
            try:
                await message.reply(
                    "I couldn't post that reply to GitHub. Your message is still here in Discord; please try again later.",
                    allowed_mentions=discord.AllowedMentions.none(),
                )
            except Exception:
                pass
            return True


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
