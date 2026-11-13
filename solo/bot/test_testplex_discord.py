"""Focused tests for Testplex Discord release and tracker formatting."""
import importlib
import sys
import types
import unittest


def load_module():
    discord_stub = types.SimpleNamespace(
        HTTPException=Exception,
        NotFound=type("NotFound", (Exception,), {}),
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
