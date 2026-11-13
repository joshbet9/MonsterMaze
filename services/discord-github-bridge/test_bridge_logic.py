import unittest

from bridge_logic import (
    format_activity,
    format_release,
    format_subissue,
    repository_allowlist,
    state_change,
)


class BridgeLogicTests(unittest.TestCase):
    def test_repository_allowlist_validates_and_normalizes(self):
        self.assertEqual(
            repository_allowlist("joshbet9/MonsterMaze, joshbet9/testplex, invalid value, /repo"),
            frozenset({"joshbet9/monstermaze", "joshbet9/testplex"}),
        )

    def test_initial_server_state_is_not_an_activity_event(self):
        self.assertFalse(state_change(None, None, "online", None))

    def test_server_state_transitions_are_announced_once(self):
        self.assertTrue(state_change("offline", None, "starting", "Josh"))
        self.assertFalse(state_change("online", None, "online", None))
        self.assertTrue(state_change("starting", "Josh", "starting", "Alex"))
        self.assertIn("started by Josh", format_activity("1.21", "starting", "Josh"))

    def test_release_message_uses_release_metadata_and_limits_notes(self):
        message = format_release("joshbet9/testplex", {
            "name": "Testplex 26.3",
            "tag_name": "v1.2.0",
            "html_url": "https://example.test/release",
            "body": "Changed gameplay",
            "author": {"login": "Josh"},
        })
        self.assertIn("v1.2.0", message)
        self.assertIn("Changed gameplay", message)
        self.assertIn("https://example.test/release", message)
        self.assertIn("Published by Josh", message)
        long_message = format_release("repo", {"body": "x" * 4000})
        self.assertLessEqual(len(long_message), 3100)

    def test_subissue_message_shows_status_title_and_labels(self):
        message = format_subissue({
            "number": 455,
            "title": "Consolidate Monster Maze",
            "state": "open",
            "html_url": "https://example.test/issues/455",
            "labels": [{"name": "game:monster-maze"}],
        })
        self.assertIn("🟢 Open", message)
        self.assertIn("#455 Consolidate Monster Maze", message)
        self.assertIn("game:monster-maze", message)


if __name__ == "__main__":
    unittest.main()
