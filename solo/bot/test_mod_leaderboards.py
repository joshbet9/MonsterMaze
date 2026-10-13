"""Unit tests for the public JNP Mod leaderboard routes."""
import json
import os
import sqlite3
import tempfile
import unittest
import uuid

import mod_leaderboards


class ModLeaderboardTests(unittest.TestCase):
    def setUp(self):
        handle, self.path = tempfile.mkstemp(suffix=".sqlite")
        os.close(handle)
        c = sqlite3.connect(self.path)
        # Match the actual authoritative server-plugin leaderboard schema.
        c.execute("""CREATE TABLE runs (
            platform TEXT NOT NULL, mode TEXT NOT NULL, pattern INTEGER NOT NULL,
            kit TEXT NOT NULL, uuid TEXT NOT NULL, name TEXT, stage INTEGER NOT NULL,
            time_ms INTEGER NOT NULL, PRIMARY KEY(platform, mode, pattern, kit, uuid)
        )""")
        c.commit()
        c.close()

    def tearDown(self):
        try:
            os.unlink(self.path)
        except OSError:
            pass

    def db(self):
        return sqlite3.connect(self.path)

    def test_public_community_submit_and_leaderboard(self):
        player_uuid = str(uuid.uuid4())
        payload = {
            "submissionId": str(uuid.uuid4()), "uuid": player_uuid, "name": "MazeRunner",
            "context": "MPS", "game": "Monster Maze", "mode": "modern",
            "pattern": 1, "kit": "Jumper", "stage": 27, "timeMs": 183000,
        }
        status, response = mod_leaderboards.handle_submit(
            json.dumps(payload).encode("utf-8"), "test-ip-community", self.db
        )
        self.assertEqual(status, 200)
        self.assertTrue(response["accepted"])

        status, board = mod_leaderboards.handle_get(
            "/api/v1/mod/leaderboard",
            {"source": ["community"], "context": ["MPS"], "mode": ["modern"],
             "kind": ["overall"], "filter": ["all"]},
            self.db, {"1.8": {"original", "modern", "speed"}, "1.21": {"original", "modern", "classic"}}
        )
        self.assertEqual(status, 200)
        self.assertFalse(board["verified"])
        self.assertEqual(len(board["rows"]), 1)
        self.assertEqual(board["rows"][0]["stage"], 27)
        self.assertEqual(board["rows"][0]["context"], "MPS")

    def test_client_report_rejects_unknown_context(self):
        payload = {
            "submissionId": str(uuid.uuid4()), "uuid": str(uuid.uuid4()), "name": "MazeRunner",
            "context": "UNKNOWN", "game": "Monster Maze", "mode": "modern",
            "pattern": 1, "kit": "Jumper", "stage": 27, "timeMs": 183000,
        }
        status, response = mod_leaderboards.handle_submit(
            json.dumps(payload).encode("utf-8"), "test-ip-invalid-context", self.db
        )
        self.assertEqual(status, 400)
        self.assertFalse(response["ok"])

    def test_server_board_returns_one_pb_per_player(self):
        player_uuid = str(uuid.uuid4())
        c = self.db()
        c.executemany("INSERT INTO runs VALUES(?,?,?,?,?,?,?,?)", [
            ("1.8", "original", 0, "Jumper", player_uuid, "MazeRunner", 20, 100000),
            ("1.8", "original", 1, "Jumper", player_uuid, "MazeRunner", 25, 120000),
        ])
        c.commit()
        c.close()
        status, board = mod_leaderboards.handle_get(
            "/api/v1/mod/leaderboard",
            {"source": ["server"], "platform": ["1.8"], "mode": ["original"],
             "kind": ["overall"], "filter": ["all"]},
            self.db, {"1.8": {"original", "modern", "speed"}, "1.21": {"original", "modern", "classic"}}
        )
        self.assertEqual(status, 200)
        self.assertTrue(board["verified"])
        self.assertEqual(len(board["rows"]), 1)
        self.assertEqual(board["rows"][0]["stage"], 25)


if __name__ == "__main__":
    unittest.main()
