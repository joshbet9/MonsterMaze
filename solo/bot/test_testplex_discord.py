"""Focused tests for Testplex Discord release and tracker formatting."""
import importlib
import sys
import types
import unittest


def load_module():
    discord_stub = types.SimpleNamespace(
        HTTPException=Exception,
        NotFound=type("NotFound", (Exception,), {}),
        ForumChannel=type("ForumChannel", (), {}),
        AllowedMentions=types.SimpleNamespace(none=lambda: None),
    )
    base_stub = types.ModuleType("monster_bot_v2")
    sys.modules["discord"] = discord_stub
    sys.modules["monster_bot_v2"] = base_stub
    sys.modules.pop("testplex_discord", None)
    return importlib.import_module("testplex_discord")


class TestplexDiscordFormattingTests(unittest.TestCase):
    def test_release_message_includes_tag_notes_link_and_author(self):
        module = load_module()
        message = "\n".join(module.format_release("joshbet9/testplex", {
            "name": "Testplex Update",
            "tag_name": "v1.2.3",
            "body": "Fixed Dragon Escape and improved server stability.",
            "html_url": "https://github.com/joshbet9/testplex/releases/tag/v1.2.3",
            "author": {"login": "Josh"},
        }))
        self.assertIn("Testplex Update", message)
        self.assertIn("v1.2.3", message)
        self.assertIn("Dragon Escape", message)
        self.assertIn("github.com/joshbet9/testplex/releases/tag/v1.2.3", message)
        self.assertIn("Published by Josh", message)

    def test_long_release_notes_are_chunked_without_truncation(self):
        module = load_module()
        body = ("Release change line.\n" * 500) + ("x" * 2100)
        messages = module.format_release("joshbet9/testplex", {"body": body})
        self.assertGreater(len(messages), 1)
        self.assertTrue(all(len(message) <= 1900 for message in messages))
        self.assertIn(body, "".join(messages))

    def test_issue_descriptions_and_comments_keep_full_feedback(self):
        module = load_module()
        description = "reproduction details\n" * 250
        messages = module.format_issue_description({
            "number": 455, "title": "Map failure", "state": "open",
            "html_url": "https://github.com/joshbet9/testplex/issues/455",
            "labels": [{"name": "game:dragon-escape"}], "body": description,
        })
        self.assertTrue(all(len(message) <= 1900 for message in messages))
        self.assertIn(description, "".join(messages))
        comment = "Additional tester discussion\n" * 200
        messages = module.format_issue_comment({
            "user": {"login": "tester"}, "html_url": "https://github.com/joshbet9/testplex/issues/455#issuecomment-1",
            "body": comment,
        })
        self.assertTrue(all(len(message) <= 1900 for message in messages))
        self.assertIn(comment, "".join(messages))

    def test_github_lists_are_fully_paginated(self):
        import asyncio

        module = load_module()
        calls = []

        async def fake_github_json(path):
            calls.append(path)
            return [1, 2] if "page=1" in path else [3]

        module._github_json = fake_github_json
        values = asyncio.run(module._github_json_pages("/issues?per_page=2", page_size=2))
        self.assertEqual(values, [1, 2, 3])
        self.assertEqual(len(calls), 2)

    def test_discussion_messages_are_updated_and_extra_chunks_removed(self):
        import asyncio
        import sqlite3
        import tempfile

        module = load_module()
        class Message:
            def __init__(self, message_id, content):
                self.id, self.content, self.deleted = message_id, content, False
            async def edit(self, *, content, allowed_mentions=None):
                self.content = content
            async def delete(self):
                self.deleted = True

        class Thread:
            def __init__(self):
                self.messages = {}
                self.next_id = 1
            async def send(self, content, allowed_mentions=None):
                message = Message(self.next_id, content)
                self.next_id += 1
                self.messages[message.id] = message
                return message
            async def fetch_message(self, message_id):
                message = self.messages.get(message_id)
                if message is None or message.deleted:
                    raise module.discord.NotFound()
                return message
            def get_partial_message(self, message_id):
                return self.messages[message_id]

        with tempfile.TemporaryDirectory() as directory:
            db_path = directory + "/test.db"
            module.base.db = lambda: sqlite3.connect(db_path)
            module._ensure_schema()
            thread = Thread()
            asyncio.run(module._sync_message_bundle(thread, "comment:9", 455, 456, ["first", "second"]))
            first_id, second_id = [int(value) for value in module._discussion_message_ids("comment:9")]
            asyncio.run(module._sync_message_bundle(thread, "comment:9", 455, 456, ["edited"]))
            self.assertEqual(thread.messages[first_id].content, "edited")
            self.assertTrue(thread.messages[second_id].deleted)
            self.assertEqual(module._discussion_message_ids("comment:9"), [str(first_id)])

    def test_issue_comment_webhook_requests_thread_reconciliation(self):
        import asyncio
        from unittest.mock import AsyncMock

        module = load_module()
        module.sync_all_trackers = AsyncMock()
        asyncio.run(module.handle_github_event(None, {}, "issue_comment", {
            "repository": {"full_name": "joshbet9/testplex"},
            "action": "created",
            "issue": {"number": 455},
            "comment": {"id": 123},
        }))
        module.sync_all_trackers.assert_awaited_once()

    def test_feedback_webhook_syncs_only_the_changed_issue(self):
        import asyncio
        from unittest.mock import AsyncMock

        module = load_module()
        module._sync_feedback_event_issue = AsyncMock(return_value=True)
        module.sync_all_trackers = AsyncMock()
        issue = {"number": 455, "body": "<!-- testplex-feedback:type=bug -->"}
        asyncio.run(module.handle_github_event(None, {}, "issues", {
            "repository": {"full_name": "joshbet9/testplex"},
            "action": "edited",
            "issue": issue,
        }))
        module._sync_feedback_event_issue.assert_awaited_once_with(None, {}, issue)
        module.sync_all_trackers.assert_not_awaited()

    def test_discord_reply_format_is_linked_and_does_not_ping_github_users(self):
        from types import SimpleNamespace

        module = load_module()
        message = SimpleNamespace(
            id=123456,
            author=SimpleNamespace(display_name="tester"),
            guild=SimpleNamespace(id=111),
            channel=SimpleNamespace(id=222),
            content="Please check @josh's map issue.",
            attachments=[],
        )
        body = module.format_discord_reply(message, 314)
        self.assertIn("tester", body)
        self.assertIn("https://discord.com/channels/111/222/123456", body)
        self.assertIn("@\u200bjosh", body)
        self.assertIn("<!-- testplex-discord-message:314:222:123456 -->", body)
        self.assertTrue(module.is_discord_mirrored_comment({"body": body}))

    def test_feedback_form_routes_modes_to_shared_game_forum(self):
        module = load_module()
        issue = {
            "number": 455,
            "body": "<!-- testplex-feedback:type=idea -->\n### Game\n\nMonster Maze (Speed)\n",
            "labels": [],
        }
        self.assertEqual(module.feedback_game_from_issue(issue), "Monster Maze (Speed)")
        self.assertEqual(module.feedback_kind_from_issue(issue), "idea")
        games = {
            "Monster Maze (Speed)": {"issue_number": 320, "slug": "monster-maze"},
            "Monster Maze (Modern)": {"issue_number": 320, "slug": "monster-maze"},
        }
        self.assertEqual(
            module.feedback_forum_key("idea", "Monster Maze (Speed)", games["Monster Maze (Speed)"]),
            module.feedback_forum_key("idea", "Monster Maze (Modern)", games["Monster Maze (Modern)"]),
        )

    def test_forum_provisioning_covers_each_canonical_game_and_is_idempotent(self):
        import asyncio
        import sqlite3
        import tempfile
        from types import SimpleNamespace

        module = load_module()

        class Category:
            def __init__(self, channel_id, name):
                self.id, self.name = channel_id, name

        class Forum(module.discord.ForumChannel):
            def __init__(self, channel_id, name, guild, category_id):
                self.id, self.name, self.guild, self.category_id = channel_id, name, guild, category_id

        class Guild:
            id = 123
            def __init__(self):
                self.categories, self.channels, self.next_id = [], [], 1
            async def create_category(self, name, reason=None):
                category = Category(self.next_id, name)
                self.next_id += 1
                self.categories.append(category)
                return category
            async def create_forum(self, name, category, topic=None, reason=None):
                forum = Forum(self.next_id, name, self, category.id)
                self.next_id += 1
                self.channels.append(forum)
                return forum

        with tempfile.TemporaryDirectory() as directory:
            module.base.db = lambda: sqlite3.connect(directory + "/test.db")
            module._ensure_schema()
            guild = Guild()
            bot = SimpleNamespace(
                get_guild=lambda guild_id: guild if guild_id == guild.id else None,
                fetch_guild=None,
                get_channel=lambda channel_id: next((c for c in guild.channels if c.id == channel_id), None),
                fetch_channel=None,
            )
            games = {
                "Monster Maze (Speed)": {"issue_number": 320, "slug": "monster-maze"},
                "Monster Maze (Modern)": {"issue_number": 320, "slug": "monster-maze"},
            }
            asyncio.run(module._ensure_feedback_forums(bot, {"testplex_discord_guild_id": 123}, games))
            asyncio.run(module._ensure_feedback_forums(bot, {"testplex_discord_guild_id": 123}, games))
            self.assertEqual(len(guild.categories), 2)
            self.assertEqual(len(guild.channels), 2)
            self.assertEqual({forum.name for forum in guild.channels}, {"monster-maze"})

    def test_human_reply_in_mapped_game_thread_becomes_github_comment_once(self):
        import asyncio
        import sqlite3
        import tempfile
        from types import SimpleNamespace
        from unittest.mock import ANY, AsyncMock

        module = load_module()
        with tempfile.TemporaryDirectory() as directory:
            db_path = directory + "/test.db"
            module.base.db = lambda: sqlite3.connect(db_path)
            module._ensure_schema()
            module._save_tracker_thread(314, 9001)
            github_post = AsyncMock(return_value={
                "id": 77,
                "html_url": "https://github.com/joshbet9/testplex/issues/314#issuecomment-77",
            })
            module._github_create_issue_comment = github_post
            message = SimpleNamespace(
                id=12345,
                author=SimpleNamespace(id=5, bot=False, display_name="tester"),
                webhook_id=None,
                guild=SimpleNamespace(id=123),
                channel=SimpleNamespace(id=9001, parent_id=456),
                content="I can reproduce this.",
                attachments=[],
                reply=AsyncMock(),
            )
            bot = SimpleNamespace(user=SimpleNamespace(id=999), cfg={
                "testplex_discord_guild_id": 123,
                "testplex_issue_index_channel": 456,
            })

            self.assertTrue(asyncio.run(module.handle_discord_message(bot, message)))
            self.assertEqual(github_post.await_args.args, (314, ANY))
            self.assertIn("<!-- testplex-discord-message:314:9001:12345 -->", github_post.await_args.args[1])
            message.reply.assert_awaited_once()
            self.assertTrue(asyncio.run(module.handle_discord_message(bot, message)))
            github_post.assert_awaited_once()

    def test_discord_bot_and_unmapped_messages_are_not_forwarded(self):
        import asyncio
        from types import SimpleNamespace
        from unittest.mock import AsyncMock

        module = load_module()
        module._ensure_schema = lambda: None
        module._tracker_for_thread = lambda _thread_id: None
        post = AsyncMock()
        module._github_create_issue_comment = post
        bot = SimpleNamespace(user=SimpleNamespace(id=999), cfg={})
        bot_message = SimpleNamespace(
            author=SimpleNamespace(id=999, bot=True), webhook_id=None,
        )
        self.assertFalse(asyncio.run(module.handle_discord_message(bot, bot_message)))
        self.assertFalse(post.await_count)

    def test_forum_thread_reply_targets_the_specific_github_subissue(self):
        import asyncio
        import sqlite3
        import tempfile
        from types import SimpleNamespace
        from unittest.mock import AsyncMock

        module = load_module()
        with tempfile.TemporaryDirectory() as directory:
            module.base.db = lambda: sqlite3.connect(directory + "/test.db")
            module._ensure_schema()
            module._save_feedback_forum("bug:dragon-escape", 456)
            module._save_feedback_thread(455, 310, "bug:dragon-escape", 9001)
            post = AsyncMock(return_value={"id": 88, "html_url": "https://github.com/example/comment/88"})
            module._github_create_issue_comment = post
            message = SimpleNamespace(
                id=12345,
                author=SimpleNamespace(id=5, bot=False, display_name="tester"),
                webhook_id=None,
                guild=SimpleNamespace(id=123),
                channel=SimpleNamespace(id=9001, parent_id=456),
                content="The water trap still flows.",
                attachments=[],
                reply=AsyncMock(),
            )
            bot = SimpleNamespace(user=SimpleNamespace(id=999), cfg={"testplex_discord_guild_id": 123})
            self.assertTrue(asyncio.run(module.handle_discord_message(bot, message)))
            self.assertEqual(post.await_args.args[0], 455)


    def test_subissue_message_tracks_state_and_game_label(self):
        module = load_module()
        message = module.format_subissue({
            "number": 455,
            "title": "Consolidate Monster Maze",
            "state": "open",
            "html_url": "https://github.com/joshbet9/testplex/issues/455",
            "labels": [{"name": "game:monster-maze"}],
        })
        self.assertIn("🟢 Open", message)
        self.assertIn("#455 Consolidate Monster Maze", message)
        self.assertIn("game:monster-maze", message)

    def test_repository_allowlist_is_case_insensitive(self):
        module = load_module()
        self.assertEqual(
            module.safe_repository_allowlist("joshbet9/Testplex, joshbet9/MonsterMaze"),
            frozenset({"joshbet9/testplex", "joshbet9/monstermaze"}),
        )

    def test_first_status_poll_is_not_a_transition(self):
        module = load_module()
        self.assertFalse(module.initial_state_changed(None, "online"))
        self.assertTrue(module.initial_state_changed("offline", "starting"))
        self.assertTrue(module.initial_state_changed("starting", "starting", "Josh", "Alex"))
        self.assertFalse(module.initial_state_changed("online", "online"))


if __name__ == "__main__":
    unittest.main()
