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
        message = module.format_release("joshbet9/testplex", {
            "name": "Testplex Update",
            "tag_name": "v1.2.3",
            "body": "Fixed Dragon Escape and improved server stability.",
            "html_url": "https://github.com/joshbet9/testplex/releases/tag/v1.2.3",
            "author": {"login": "Josh"},
        })
        self.assertIn("Testplex Update", message)
        self.assertIn("v1.2.3", message)
        self.assertIn("Dragon Escape", message)
        self.assertIn("github.com/joshbet9/testplex/releases/tag/v1.2.3", message)
        self.assertIn("Published by Josh", message)

    def test_release_message_stays_below_discord_limit(self):
        module = load_module()
        message = module.format_release("joshbet9/testplex", {"body": "x" * 5000})
        self.assertLessEqual(len(message), 1900)

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
