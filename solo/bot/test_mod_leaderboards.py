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


    def test_server_combination_filter_supports_maverick_pattern_and_mode(self):
        c = self.db()
        players = [
            (str(uuid.uuid4()), "MaverickAce", "1.8", "speed", 2, "Maverick", 29, 90000),
            (str(uuid.uuid4()), "OtherKit", "1.8", "speed", 2, "Jumper", 30, 85000),
            (str(uuid.uuid4()), "OtherMode", "1.8", "original", 2, "Maverick", 31, 80000),
        ]
        c.executemany("INSERT INTO runs VALUES(?,?,?,?,?,?,?,?)", [
            (platform, mode, pattern, kit, player_uuid, name, stage, time_ms)
            for player_uuid, name, platform, mode, pattern, kit, stage, time_ms in players
        ])
        c.commit()
        c.close()
        status, board = mod_leaderboards.handle_get(
            "/api/v1/mod/leaderboard",
            {"source": ["server"], "platform": ["1.8"], "mode": ["speed"],
             "kind": ["combination"], "pattern": ["2"], "kit": ["Maverick"], "filter": ["Maverick"]},
            self.db, {"1.8": {"original", "modern", "speed"}, "1.21": {"original", "modern", "classic"}}
        )
        self.assertEqual(status, 200)
        self.assertTrue(board["verified"])
        self.assertEqual(len(board["rows"]), 1)
        self.assertEqual(board["rows"][0]["name"], "MaverickAce")
        self.assertEqual(board["rows"][0]["mode"], "speed")
        self.assertEqual(board["rows"][0]["kit"], "Maverick")

    def test_server_player_profile_honours_mode_pattern_and_kit_filters(self):
        player_uuid = str(uuid.uuid4())
        c = self.db()
        c.executemany("INSERT INTO runs VALUES(?,?,?,?,?,?,?,?)", [
            ("1.8", "speed", 2, "Maverick", player_uuid, "MazeRunner", 29, 90000),
            ("1.8", "original", 2, "Maverick", player_uuid, "MazeRunner", 31, 80000),
            ("1.8", "speed", 1, "Jumper", player_uuid, "MazeRunner", 33, 70000),
        ])
        c.commit()
        c.close()
        status, profile = mod_leaderboards.handle_get(
            "/api/v1/mod/player",
            {"source": ["server"], "uuid": [player_uuid], "platform": ["1.8"],
             "mode": ["speed"], "pattern": ["2"], "kit": ["Maverick"]},
            self.db, {"1.8": {"original", "modern", "speed"}, "1.21": {"original", "modern", "classic"}}
        )
        self.assertEqual(status, 200)
        self.assertEqual(len(profile["rows"]), 1)
        self.assertEqual(profile["rows"][0]["mode"], "speed")
        self.assertEqual(profile["rows"][0]["pattern"], 2)
        self.assertEqual(profile["rows"][0]["kit"], "Maverick")

    def test_arcade_alias_maps_to_min_for_upload_and_public_filter(self):
        payload = {
            "submissionId": str(uuid.uuid4()), "uuid": str(uuid.uuid4()), "name": "ArcadeRunner",
            "context": "ARCADE", "game": "Monster Maze", "mode": "original",
            "pattern": 0, "kit": "Maverick", "stage": 25, "timeMs": 123000,
        }
        status, response = mod_leaderboards.handle_submit(
            json.dumps(payload).encode("utf-8"), "test-ip-arcade-alias", self.db
        )
        self.assertEqual(status, 200)
        self.assertTrue(response["accepted"])
        status, board = mod_leaderboards.handle_get(
            "/api/v1/mod/leaderboard",
            {"source": ["community"], "context": ["ARCADE"], "mode": ["original"],
             "kind": ["kit"], "filter": ["Maverick"]},
            self.db, {"1.8": {"original", "modern", "speed"}, "1.21": {"original", "modern", "classic"}}
        )
        self.assertEqual(status, 200)
        self.assertEqual(len(board["rows"]), 1)
        self.assertEqual(board["rows"][0]["context"], "MIN")


if __name__ == "__main__":
    unittest.main()
