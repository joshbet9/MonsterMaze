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

    def test_message_split_keeps_exact_limit_chunks_and_all_characters(self):
        module = load_module()
        text = ("x" * 1900) + "\nremainder"
        messages = module.split_discord_messages(text, limit=1900)
        self.assertTrue(all(len(message) <= 1900 for message in messages))
        self.assertEqual("".join(messages), text)

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

    def test_discussion_replies_are_conversational_without_repeated_links(self):
        module = load_module()
        reply = "\n".join(module.format_issue_comment({
            "user": {"login": "tester"},
            "html_url": "https://github.com/joshbet9/testplex/issues/457#issuecomment-1",
            "body": "I can reproduce this on Blue Orchid.",
        }))
        controls = module.format_feedback_label_controls({
            "number": 457,
            "html_url": "https://github.com/joshbet9/testplex/issues/457",
            "state": "open",
            "labels": [{"name": "needs-triage"}],
        }, "Dragon Escape")
        self.assertIn("**tester**", reply)
        self.assertIn("I can reproduce this on Blue Orchid.", reply)
        self.assertNotIn("github.com", reply)
        self.assertNotIn("github.com", controls)

    def test_feedback_issue_summary_is_structured_and_removes_internal_markers(self):
        module = load_module()
        messages = module.format_issue_description({
            "number": 457,
            "title": "[Bug] Dragon Escape: Spectators can finish the race",
            "state": "open",
            "labels": [{"name": "needs-triage"}, {"name": "game:dragon-escape"}],
            "html_url": "https://github.com/joshbet9/testplex/issues/457",
            "body": (
                "<!-- testplex-feedback:type=bug -->\n"
                "<!-- testplex-discord-forum-thread:123456789 -->\n"
                "### Game\nDragon Escape\n\n"
                "### Report\nSpectators can finish the race.\n\n"
                "Submitted via the Testplex Discord feedback forum.\n\n"
                "### Parent tracker\nDragon Escape: https://github.com/joshbet9/testplex/issues/310\n\n"
                "### Discord thread\nhttps://discord.com/channels/1/123456789"
            ),
        }, "Bug report")
        message = "\n".join(messages)
        self.assertIn("🐛 **Bug report #457", message)
        self.assertIn("Submitted", message)
        self.assertIn("**Report**", message)
        self.assertIn("Spectators can finish the race.", message)
        self.assertIn("**Tracker:** [Dragon Escape]", message)
        self.assertIn("[Discord thread](https://discord.com/channels/1/123456789)", message)
        self.assertNotIn("testplex-discord-forum-thread", message)
        self.assertNotIn("Submitted via the Testplex", message)

    def test_new_forum_issue_receipt_is_scannable(self):
        module = load_module()
        receipt = module.format_feedback_receipt({
            "number": 457,
            "html_url": "https://github.com/joshbet9/testplex/issues/457",
            "labels": [{"name": "needs-triage"}],
        }, "bug", "Dragon Escape", 310)
        self.assertIn("🐛 **Bug report filed · Dragon Escape**", receipt)
        self.assertIn("`Submitted`", receipt)
        self.assertIn("[GitHub issue #457]", receipt)
        self.assertIn("[Game tracker #310]", receipt)

    def test_feedback_label_controls_are_visible_on_existing_issue_threads(self):
        import asyncio
        from types import SimpleNamespace
        from unittest.mock import AsyncMock

        module = load_module()
        issue = {
            "number": 457,
            "html_url": "https://github.com/joshbet9/testplex/issues/457",
            "state": "open",
            "labels": [{"name": "needs-triage"}],
            "body": "<!-- testplex-feedback:type=bug -->\n<!-- testplex-discord-forum-thread:9001 -->",
        }
        thread = SimpleNamespace(id=9001, archived=False)
        module._ensure_feedback_thread = AsyncMock(return_value=(thread, 310, "bug"))
        module._sync_feedback_thread_tags = AsyncMock()
        module._ensure_feedback_issue_parent = AsyncMock()
        module._sync_issue_discussion = AsyncMock()
        module._sync_message_bundle = AsyncMock()
        module._normalize_feedback_status = AsyncMock(return_value=issue)
        module.feedback_game_from_issue = lambda issue, games: "Dragon Escape"

        asyncio.run(module._sync_feedback_issue(SimpleNamespace(), {}, issue))
        module._sync_message_bundle.assert_awaited_once()
        args = module._sync_message_bundle.await_args.args
        self.assertEqual(args[0], thread)
        self.assertEqual(args[1], "issue:457:label-controls")
        self.assertIn("!implemented", args[4][0])
        self.assertIn("waiting-tester", args[4][0])

    def test_feedback_status_maps_to_discord_forum_subcategory(self):
        module = load_module()
        self.assertEqual(module._feedback_status_tag({"state": "open", "labels": [{"name": "submitted"}]}), "Submitted")
        self.assertEqual(module._feedback_status_tag({"state": "open", "labels": [{"name": "needs-triage"}]}), "Submitted")
        self.assertEqual(module._feedback_status_tag({"state": "open", "labels": [{"name": "in-progress"}]}), "In progress")
        self.assertEqual(module._feedback_status_tag({"state": "open", "labels": [{"name": "implemented"}]}), "Waiting tester")
        self.assertEqual(module._feedback_status_tag({"state": "open", "labels": [{"name": "resolved"}]}), "Resolved")
        self.assertEqual(module._feedback_status_tag({"state": "open", "labels": [{"name": "deployed"}]}), "Deployed")
        self.assertEqual(module._feedback_status_tag({"state": "closed", "labels": []}), "Resolved")

    def test_implemented_label_command_updates_github_and_discord_status(self):
        import asyncio
        from types import SimpleNamespace
        from unittest.mock import AsyncMock, Mock

        module = load_module()
        module._ensure_feedback_status_labels = AsyncMock(return_value={
            "submitted": "submitted", "in-progress": "in-progress",
            "waiting-tester": "waiting-tester", "resolved": "resolved", "deployed": "deployed",
        })
        module._github_write_sync = Mock()
        updated_issue = {
            "number": 457,
            "html_url": "https://github.com/joshbet9/testplex/issues/457",
            "state": "open",
            "labels": [{"name": "waiting-tester"}],
        }
        existing_issue = {
            "number": 457,
            "labels": [{"name": "submitted"}, {"name": "implemented"}],
        }
        module._github_json = AsyncMock(side_effect=[existing_issue, updated_issue])
        module._sync_feedback_thread_tags = AsyncMock()
        message = SimpleNamespace(
            content="!implemented",
            channel=SimpleNamespace(id=123, send=AsyncMock()),
            reply=AsyncMock(),
        )
        bot = SimpleNamespace()

        self.assertTrue(asyncio.run(module._handle_feedback_label_command(bot, message, 457)))
        self.assertEqual(module._github_write_sync.call_count, 3)
        self.assertEqual(module._github_write_sync.call_args_list[0].args[:2], (
            "POST", "/issues/457/labels",
        ))
        self.assertEqual(module._github_write_sync.call_args_list[1].args[:2], (
            "DELETE", "/issues/457/labels/submitted",
        ))
        self.assertEqual(module._github_write_sync.call_args_list[2].args[:2], (
            "DELETE", "/issues/457/labels/implemented",
        ))
        module._sync_feedback_thread_tags.assert_awaited_once_with(bot, updated_issue, message.channel)
        self.assertIn("Waiting tester", message.reply.await_args.args[0])

    def test_status_command_archives_discord_thread_only_when_deployed(self):
        import asyncio
        from types import SimpleNamespace
        from unittest.mock import AsyncMock

        module = load_module()
        updated_issue = {
            "number": 457,
            "html_url": "https://github.com/joshbet9/testplex/issues/457",
            "state": "open",
            "labels": [{"name": "deployed"}],
        }
        module._github_set_feedback_status = AsyncMock(return_value=updated_issue)
        module._sync_feedback_thread_tags = AsyncMock()
        channel = SimpleNamespace(id=123, archived=False, edit=AsyncMock())
        message = SimpleNamespace(
            content="!status deployed",
            channel=channel,
            reply=AsyncMock(),
        )

        self.assertTrue(asyncio.run(module._handle_feedback_label_command(SimpleNamespace(), message, 457)))
        module._github_set_feedback_status.assert_awaited_once_with(457, "deployed")
        channel.edit.assert_awaited_once_with(archived=True)
        self.assertIn("Deployed", message.reply.await_args.args[0])

    def test_legacy_feedback_status_migrates_to_clean_lifecycle_label(self):
        import asyncio
        from unittest.mock import AsyncMock

        module = load_module()
        updated = {"number": 457, "state": "open", "labels": [{"name": "waiting-tester"}]}
        module._github_set_feedback_status = AsyncMock(return_value=updated)
        legacy = {"number": 457, "state": "open", "labels": [{"name": "implemented"}]}
        result = asyncio.run(module._normalize_feedback_status(legacy))
        module._github_set_feedback_status.assert_awaited_once_with(457, "waiting-tester")
        self.assertEqual(result, updated)

    def test_unused_legacy_status_labels_are_deleted_but_labels_in_use_are_kept(self):
        import asyncio
        from unittest.mock import AsyncMock, Mock

        module = load_module()
        module.LEGACY_STATUS_LABEL_CLEANUP_COMPLETE = False
        module.LEGACY_FEEDBACK_STATUS_LABELS = {"fixed", "needs-triage"}
        module._github_feedback_labels = AsyncMock(return_value={
            "fixed": "fixed", "needs-triage": "needs-triage",
        })
        module._github_json_pages = AsyncMock(side_effect=[[], [{"number": 99}]])
        module._github_write_sync = Mock()

        asyncio.run(module._cleanup_unused_legacy_status_labels())
        module._github_write_sync.assert_called_once_with("DELETE", "/labels/fixed", {})
        self.assertTrue(module.LEGACY_STATUS_LABEL_CLEANUP_COMPLETE)

    def test_tester_can_apply_or_remove_an_exact_existing_github_label(self):
        import asyncio
        from unittest.mock import AsyncMock, Mock

        module = load_module()
        module._github_feedback_labels = AsyncMock(return_value={"priority: high": "Priority: High"})
        module._github_write_sync = Mock()
        module._github_json = AsyncMock(return_value={"number": 457, "labels": []})

        asyncio.run(module._github_change_feedback_label(457, "priority: high", False))
        self.assertEqual(module._github_write_sync.call_args.args[:2], (
            "DELETE", "/issues/457/labels/Priority%3A%20High",
        ))

    def test_label_commands_are_recognized_without_mirroring_them_as_comments(self):
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
            module._save_feedback_thread(457, 310, "bug:dragon-escape", 9001)
            module._handle_feedback_label_command = AsyncMock(return_value=True)
            module._github_create_issue_comment = AsyncMock()
            message = SimpleNamespace(
                id=9002,
                author=SimpleNamespace(id=5, bot=False),
                webhook_id=None,
                guild=SimpleNamespace(id=123),
                channel=SimpleNamespace(id=9001, parent_id=456),
                content="!label needs-triage",
            )
            bot = SimpleNamespace(user=SimpleNamespace(id=999), cfg={"testplex_discord_guild_id": 123})

            self.assertTrue(asyncio.run(module.handle_discord_message(bot, message)))
            module._handle_feedback_label_command.assert_awaited_once_with(bot, message, 457)
            module._github_create_issue_comment.assert_not_awaited()

    def test_feedback_status_tag_is_applied_and_repeated_sync_is_idempotent(self):
        import asyncio
        import sqlite3
        import tempfile
        from types import SimpleNamespace

        module = load_module()
        with tempfile.TemporaryDirectory() as directory:
            module.base.db = lambda: sqlite3.connect(directory + "/test.db")
            module._ensure_schema()
            module._save_feedback_forum("bug:dragon-escape", 456)

            class Forum(module.discord.ForumChannel):
                def __init__(self):
                    self.id = 456
                    self.available_tags = []
                    self.created = []

                async def create_tag(self, *, name, reason=None):
                    tag = SimpleNamespace(id=len(self.created) + 1, name=name)
                    self.available_tags.append(tag)
                    self.created.append(tag)
                    return tag

            class Thread:
                def __init__(self):
                    self.parent_id = 456
                    self.applied_tags = []
                    self.edits = 0

                async def edit(self, *, applied_tags):
                    self.applied_tags = list(applied_tags)
                    self.edits += 1

            forum, thread = Forum(), Thread()
            bot = SimpleNamespace(get_channel=lambda channel_id: forum if channel_id == 456 else None)
            issue = {"number": 457, "state": "open", "labels": [{"name": "needs-triage"}]}
            asyncio.run(module._sync_feedback_thread_tags(bot, issue, thread))
            self.assertEqual([tag.name for tag in thread.applied_tags], ["Submitted"])
            self.assertEqual(len(forum.created), 1)
            asyncio.run(module._sync_feedback_thread_tags(bot, issue, thread))
            self.assertEqual(len(forum.created), 1)
            self.assertEqual(thread.edits, 1)

    def test_feedback_status_tag_does_not_overflow_discord_tag_limit(self):
        import asyncio
        import sqlite3
        import tempfile
        from types import SimpleNamespace

        module = load_module()
        with tempfile.TemporaryDirectory() as directory:
            module.base.db = lambda: sqlite3.connect(directory + "/test.db")
            module._ensure_schema()
            module._save_feedback_forum("bug:dragon-escape", 456)

            class Forum(module.discord.ForumChannel):
                id = 456
                available_tags = []

                async def create_tag(self, *, name, reason=None):
                    return SimpleNamespace(id=99, name=name)

            class Thread:
                parent_id = 456

                def __init__(self):
                    self.applied_tags = [SimpleNamespace(id=i, name=f"Custom {i}") for i in range(5)]
                    self.edits = 0

                async def edit(self, *, applied_tags):
                    self.edits += 1

            thread = Thread()
            bot = SimpleNamespace(get_channel=lambda channel_id: Forum())
            issue = {"number": 458, "state": "open", "labels": [{"name": "needs-triage"}]}
            asyncio.run(module._sync_feedback_thread_tags(bot, issue, thread))
            self.assertEqual(thread.edits, 0)
            self.assertEqual(len(thread.applied_tags), 5)

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

    def test_discord_reply_format_is_conversational_and_does_not_ping_github_users(self):
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
        self.assertNotIn("https://discord.com/channels/111/222/123456", body)
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

    def test_feedback_routing_uses_game_label_when_display_field_is_not_a_selector(self):
        module = load_module()
        games = {
            "Monster Maze (Modern)": {"issue_number": 320, "slug": "monster-maze"},
            "Shared / Global": {"issue_number": 321, "slug": "shared-global"},
        }
        issue = {
            "number": 405,
            "body": "### Game\nShared lobby and global systems\n\n### Report\nCollision is enabled.",
            "labels": [{"name": "game:shared-global"}],
        }
        self.assertEqual(module.feedback_game_from_issue(issue, games), "Shared / Global")

    def test_feedback_routing_falls_back_to_parent_tracker_for_unmapped_optional_field(self):
        module = load_module()
        games = {
            "Monster Maze (Modern)": {"issue_number": 320, "slug": "monster-maze"},
        }
        issue = {
            "number": 389,
            "body": "### Game\nMonster Maze\n\n### Report\nMap did not load.",
            "labels": [{"name": "bug"}],
        }
        self.assertEqual(
            module.feedback_game_from_issue(issue, games, "Monster Maze (Modern)"),
            "Monster Maze (Modern)",
        )

    def test_feedback_discord_thread_marker_is_parsed(self):
        module = load_module()
        self.assertEqual(
            module.feedback_discord_thread_from_issue({
                "body": "Details\n<!-- testplex-discord-forum-thread:123456789012345678 -->"
            }),
            123456789012345678,
        )

    def test_direct_forum_starter_creates_and_maps_a_github_subissue(self):
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
            module._load_feedback_directory = AsyncMock(return_value={
                "Dragon Escape": {"issue_number": 310, "slug": "dragon-escape"},
            })
            create_issue = AsyncMock(return_value={
                "id": 991,
                "number": 455,
                "html_url": "https://github.com/joshbet9/testplex/issues/455",
            })
            module._github_create_feedback_issue = create_issue
            module._ensure_feedback_status_labels = AsyncMock(return_value={"submitted": "submitted"})
            module._ensure_feedback_issue_parent = AsyncMock()
            message = SimpleNamespace(
                id=9001,
                author=SimpleNamespace(id=5, bot=False, display_name="Tester"),
                webhook_id=None,
                guild=SimpleNamespace(id=123),
                channel=SimpleNamespace(
                    id=9001,
                    parent_id=456,
                    name="Builder blocks are unreliable",
                    send=AsyncMock(return_value=SimpleNamespace(id=9003, content="")),
                ),
                content="Builder blocks vanish after placing in water.",
                attachments=[SimpleNamespace(url="https://cdn.example/image.png")],
                reply=AsyncMock(),
            )
            bot = SimpleNamespace(user=SimpleNamespace(id=999), cfg={"testplex_discord_guild_id": 123})

            self.assertTrue(asyncio.run(module.handle_discord_message(bot, message)))
            self.assertEqual(module._feedback_issue_for_thread(9001), 455)
            self.assertEqual(create_issue.await_args.args[0], "[Bug] Dragon Escape: Builder blocks are unreliable")
            labels = create_issue.await_args.args[2]
            self.assertIn("submitted", labels)
            self.assertNotIn("needs-triage", labels)
            self.assertIn("game:dragon-escape", labels)
            self.assertIn("tester-bug", labels)
            body = create_issue.await_args.args[1]
            self.assertIn("<!-- testplex-feedback:type=bug -->", body)
            self.assertIn("<!-- testplex-discord-forum-thread:9001 -->", body)
            self.assertIn("Parent tracker", body)
            self.assertIn("https://cdn.example/image.png", body)
            self.assertIn("!implemented", message.channel.send.await_args.args[0])
            module._ensure_feedback_issue_parent.assert_awaited_once_with(
                create_issue.return_value, 310
            )
            message.reply.assert_awaited_once()

            module._github_json = AsyncMock(return_value={
                "number": 455,
                "html_url": "https://github.com/joshbet9/testplex/issues/455",
            })
            self.assertTrue(asyncio.run(module.handle_discord_message(bot, message)))
            create_issue.assert_awaited_once()
            module._github_create_issue_comment = AsyncMock()
            self.assertTrue(asyncio.run(module.handle_discord_message(bot, message)))
            module._github_create_issue_comment.assert_not_awaited()
            self.assertEqual(module._ensure_feedback_issue_parent.await_count, 1)

    def test_unmapped_forum_replies_are_not_mistaken_for_new_posts(self):
        import asyncio
        import sqlite3
        import tempfile
        from types import SimpleNamespace
        from unittest.mock import AsyncMock

        module = load_module()
        with tempfile.TemporaryDirectory() as directory:
            module.base.db = lambda: sqlite3.connect(directory + "/test.db")
            module._ensure_schema()
            module._save_feedback_forum("idea:hoops", 456)
            create_issue = AsyncMock()
            module._github_create_feedback_issue = create_issue
            message = SimpleNamespace(
                id=9010,
                author=SimpleNamespace(id=5, bot=False),
                webhook_id=None,
                guild=SimpleNamespace(id=123),
                channel=SimpleNamespace(id=9001, parent_id=456),
            )
            bot = SimpleNamespace(user=SimpleNamespace(id=999), cfg={"testplex_discord_guild_id": 123})
            self.assertFalse(asyncio.run(module.handle_discord_message(bot, message)))
            create_issue.assert_not_awaited()

    def test_reply_during_forum_issue_creation_is_queued_until_github_mapping_exists(self):
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
            module._load_feedback_directory = AsyncMock(return_value={
                "Dragon Escape": {"issue_number": 310, "slug": "dragon-escape"},
            })
            create_started = asyncio.Event()
            allow_create_to_finish = asyncio.Event()

            async def create_issue(*_args):
                create_started.set()
                await allow_create_to_finish.wait()
                return {
                    "id": 991,
                    "number": 455,
                    "html_url": "https://github.com/joshbet9/testplex/issues/455",
                }

            module._ensure_feedback_status_labels = AsyncMock(return_value={"submitted": "submitted"})
            module._github_create_feedback_issue = create_issue
            module._ensure_feedback_issue_parent = AsyncMock()
            comment = AsyncMock(return_value={"id": 88, "html_url": "https://github.com/example/comment/88"})
            module._github_create_issue_comment = comment
            thread = SimpleNamespace(
                id=9001,
                parent_id=456,
                name="Builder water issue",
                send=AsyncMock(return_value=SimpleNamespace(id=9003, content="")),
            )
            guild = SimpleNamespace(id=123)
            starter = SimpleNamespace(
                id=9001,
                author=SimpleNamespace(id=5, bot=False, display_name="Tester"),
                webhook_id=None,
                guild=guild,
                channel=thread,
                content="Builder blocks vanish in water.",
                attachments=[],
                reply=AsyncMock(),
            )
            reply = SimpleNamespace(
                id=9002,
                author=SimpleNamespace(id=6, bot=False, display_name="Second tester"),
                webhook_id=None,
                guild=guild,
                channel=thread,
                content="I can reproduce this on Through Hell.",
                attachments=[],
                reply=AsyncMock(),
            )
            bot = SimpleNamespace(user=SimpleNamespace(id=999), cfg={"testplex_discord_guild_id": 123})

            async def exercise():
                starter_task = asyncio.create_task(module.handle_discord_message(bot, starter))
                await create_started.wait()
                reply_task = asyncio.create_task(module.handle_discord_message(bot, reply))
                await asyncio.sleep(0)
                self.assertFalse(reply_task.done())
                allow_create_to_finish.set()
                self.assertTrue(await starter_task)
                self.assertTrue(await reply_task)

            asyncio.run(exercise())
            self.assertEqual(comment.await_args.args[0], 455)
            self.assertIn("Through Hell", comment.await_args.args[1])
            self.assertNotIn(9001, module.PENDING_FEEDBACK_POSTS)

    def test_feedback_sync_adopts_the_discord_origin_thread_without_duplicate_post(self):
        import asyncio
        import sqlite3
        import tempfile
        from types import SimpleNamespace

        module = load_module()
        with tempfile.TemporaryDirectory() as directory:
            module.base.db = lambda: sqlite3.connect(directory + "/test.db")
            module._ensure_schema()
            module._save_feedback_forum("bug:dragon-escape", 456)
            thread = SimpleNamespace(id=9001, parent_id=456, name="Builder blocks are unreliable")
            bot = SimpleNamespace(
                get_channel=lambda channel_id: thread if channel_id == 9001 else None,
                fetch_channel=None,
            )
            issue = {
                "number": 455,
                "body": "<!-- testplex-feedback:type=bug -->\n<!-- testplex-discord-forum-thread:9001 -->",
                "labels": [{"name": "game:dragon-escape"}],
                "title": "[Bug] Dragon Escape: Builder blocks are unreliable",
            }
            games = {"Dragon Escape": {"issue_number": 310, "slug": "dragon-escape"}}
            actual_thread, tracker_number, kind = asyncio.run(
                module._ensure_feedback_thread(bot, games, issue)
            )
            self.assertIs(actual_thread, thread)
            self.assertEqual((tracker_number, kind), (310, "bug"))
            self.assertEqual(module._feedback_issue_for_thread(9001), 455)

    def test_discord_origin_issue_body_is_not_echoed_back_into_its_thread(self):
        import asyncio
        from types import SimpleNamespace
        from unittest.mock import AsyncMock

        module = load_module()
        thread = SimpleNamespace(id=9001, archived=False)
        module._ensure_feedback_thread = AsyncMock(return_value=(thread, 310, "bug"))
        module._ensure_feedback_issue_parent = AsyncMock()
        module._sync_issue_discussion = AsyncMock()
        module._sync_message_bundle = AsyncMock()
        module.feedback_game_from_issue = lambda _issue, _games: "Dragon Escape"
        issue = {
            "number": 455,
            "body": "<!-- testplex-feedback:type=bug -->\n<!-- testplex-discord-forum-thread:9001 -->",
            "state": "open",
        }
        module._normalize_feedback_status = AsyncMock(return_value=issue)
        asyncio.run(module._sync_feedback_issue(None, {}, issue))
        module._ensure_feedback_issue_parent.assert_awaited_once_with(issue, 310)
        self.assertFalse(module._sync_issue_discussion.await_args.kwargs["include_body"])
        module._sync_message_bundle.assert_awaited_once()
        self.assertEqual(
            module._sync_message_bundle.await_args.args[1],
            "issue:455:label-controls",
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
        self.assertIn("ðŸŸ¢ Open", message)
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

