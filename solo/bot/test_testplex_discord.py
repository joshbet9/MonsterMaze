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
        message = "\\n".join(module.format_release("joshbet9/testplex", {
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
        body = ("Release change line.\\n" * 500) + ("x" * 2100)
        messages = module.format_release("joshbet9/testplex", {"body": body})
        self.assertGreater(len(messages), 1)
        self.assertTrue(all(len(message) <= 1900 for message in messages))
        self.assertIn(body, "".join(messages))

    def test_issue_descriptions_and_comments_keep_full_feedback(self):
        module = load_module()
        description = "reproduction details\\n" * 250
        messages = module.format_issue_description({
            "number": 455, "title": "Map failure", "state": "open",
            "html_url": "https://github.com/joshbet9/testplex/issues/455",
            "labels": [{"name": "game:dragon-escape"}], "body": description,
        })
        self.assertTrue(all(len(message) <= 1900 for message in messages))
        self.assertIn(description, "".join(messages))
        comment = "Additional tester discussion\\n" * 200
        messages = module.format_issue_comment({
            "user": {"login": "tester"}, "html_url": "https://github.com/joshbet9/testplex/issues/455#issuecomment-1",
            "body": comment,
        })
        self.assertTrue(all(len(message) <= 1900 for message in messages))
        self.assertIn(comment, "".join(messages))

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
