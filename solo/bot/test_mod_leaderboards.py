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
            "startedAt": 1760000000123,
            "startedAt": 1760000000123,
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
        self.assertEqual(board["rows"][0]["startedAt"], 1760000000123)
        self.assertEqual(board["rows"][0]["startedAt"], 1760000000123)

    def test_legacy_community_schema_is_migrated_with_unknown_start_time(self):
        c = self.db()
        c.execute("""CREATE TABLE community_mod_runs (
            submission_id TEXT PRIMARY KEY, uuid TEXT NOT NULL, name TEXT NOT NULL,
            context TEXT NOT NULL, game TEXT NOT NULL, mode TEXT NOT NULL DEFAULT 'unknown',
            pattern INTEGER NOT NULL DEFAULT -1, kit TEXT NOT NULL DEFAULT 'Unknown',
            stage INTEGER NOT NULL, time_ms INTEGER NOT NULL DEFAULT 0, submitted_at INTEGER NOT NULL
        )""")
        c.commit()
        mod_leaderboards._ensure_schema(c)
        columns = {row[1] for row in c.execute("PRAGMA table_info(community_mod_runs)")}
        self.assertIn("started_at", columns)
        c.close()

    def test_same_player_uploads_near_same_game_start_are_deduplicated(self):
        player_uuid = str(uuid.uuid4())
        first = {
            "submissionId": str(uuid.uuid4()), "uuid": player_uuid, "name": "MazeRunner",
            "context": "MPS", "game": "Monster Maze", "mode": "classic",
            "pattern": -1, "kit": "Unknown", "stage": 24, "timeMs": 105000,
            "startedAt": 1760000000000,
        }
        second = dict(first)
        second["submissionId"] = str(uuid.uuid4())
        second["startedAt"] = 1760000004500
        second["kit"] = "Repulsor"
        second["pattern"] = 1
        first_status, first_response = mod_leaderboards.handle_submit(
            json.dumps(first).encode("utf-8"), "dedup-first-ip", self.db
        )
        second_status, second_response = mod_leaderboards.handle_submit(
            json.dumps(second).encode("utf-8"), "dedup-second-ip", self.db
        )
        self.assertEqual(first_status, 200)
        self.assertTrue(first_response["accepted"])
        self.assertEqual(second_status, 200)
        self.assertTrue(second_response["accepted"])
        self.assertTrue(second_response["deduplicated"])
        c = self.db()
        rows = c.execute(
            "SELECT COUNT(*),kit,pattern,started_at FROM community_mod_runs WHERE lower(uuid)=?",
            (player_uuid.lower(),),
        ).fetchone()
        c.close()
        self.assertEqual(rows, (1, "Repulsor", 1, 1760000000000))

    def test_late_unknown_duplicate_never_downgrades_known_kit(self):
        player_uuid = str(uuid.uuid4())
        first = {
            "submissionId": str(uuid.uuid4()), "uuid": player_uuid, "name": "MazeRunner",
            "context": "MIN", "game": "Monster Maze", "mode": "classic",
            "pattern": 1, "kit": "Body Builder", "stage": 22, "timeMs": 417327,
            "startedAt": 1760000000000,
        }
        # The winner's own upload can arrive after another observer already supplied the
        # same player's row, but its kit is Unknown. It must not create another score or
        # erase the known Body Builder observation.
        late_duplicate = dict(first)
        late_duplicate["submissionId"] = str(uuid.uuid4())
        late_duplicate["startedAt"] = 1760000006500
        late_duplicate["kit"] = "Unknown"
        # Deliberately worse timing/stage metadata from the duplicate observer must not
        # overwrite the already accepted known-kit winner record.
        late_duplicate["stage"] = 19
        late_duplicate["timeMs"] = 429921
        late_duplicate["pattern"] = -1

        first_status, first_response = mod_leaderboards.handle_submit(
            json.dumps(first).encode("utf-8"), "known-kit-first-ip", self.db
        )
        duplicate_status, duplicate_response = mod_leaderboards.handle_submit(
            json.dumps(late_duplicate).encode("utf-8"), "late-unknown-ip", self.db
        )
        self.assertEqual(first_status, 200)
        self.assertTrue(first_response["accepted"])
        self.assertEqual(duplicate_status, 200)
        self.assertTrue(duplicate_response["accepted"])
        self.assertTrue(duplicate_response["deduplicated"])

        c = self.db()
        rows = c.execute(
            "SELECT COUNT(*),kit,pattern,stage,time_ms FROM community_mod_runs WHERE lower(uuid)=?",
            (player_uuid.lower(),),
        ).fetchone()
        c.close()
        self.assertEqual(rows, (1, "Body Builder", 1, 22, 417327))

    def test_game_info_returns_all_participant_rows_near_start_timestamp(self):
        anchor_uuid = str(uuid.uuid4())
        other_uuid = str(uuid.uuid4())
        common = {
            "context": "MPS", "game": "Monster Maze", "mode": "classic",
            "pattern": 0, "kit": "Jumper", "stage": 25, "timeMs": 100000,
            "startedAt": 1760000000000,
        }
        for player_uuid, name, offset in (
            (anchor_uuid, "AnchorRunner", 0),
            (other_uuid, "OtherRunner", 5000),
        ):
            payload = dict(common)
            payload["submissionId"] = str(uuid.uuid4())
            payload["uuid"] = player_uuid
            payload["name"] = name
            payload["startedAt"] += offset
            status, result = mod_leaderboards.handle_submit(
                json.dumps(payload).encode("utf-8"), "game-info-" + name, self.db
            )
            self.assertEqual(status, 200)
            self.assertTrue(result["accepted"])
        status, game = mod_leaderboards.handle_get(
            "/api/v1/mod/game",
            {"uuid": [anchor_uuid], "startedAt": ["1760000000000"], "context": ["MPS"]},
            self.db, {"1.8": {"original", "modern", "speed"}, "1.21": {"original", "modern", "classic"}}
        )
        self.assertEqual(status, 200)
        self.assertTrue(game["ok"])
        self.assertEqual({row["name"] for row in game["rows"]}, {"AnchorRunner", "OtherRunner"})
        self.assertEqual(game["windowMs"], 30000)

    def test_game_history_is_chronological_grouped_and_paginated(self):
        round_one = 1760000000000
        round_two = round_one + 90_000
        uploads = [
            ("HistoryAnchor", str(uuid.uuid4()), "MPS", round_one, 24, True),
            ("HistoryPartner", str(uuid.uuid4()), "MPS", round_one + 5_000, 30, True),
            ("HistoryNextRound", str(uuid.uuid4()), "MIN", round_two, 19, True),
            # Older client builds have no start time. Preserve the row as an
            # explicitly unanchored history entry instead of inventing a round.
            ("HistoryLegacy", str(uuid.uuid4()), "MPS", None, 12, False),
        ]
        for index, (name, player_uuid, context, started_at, stage, has_start) in enumerate(uploads):
            payload = {
                "submissionId": str(uuid.uuid4()), "uuid": player_uuid, "name": name,
                "context": context, "game": "Monster Maze", "mode": "original",
                "pattern": 0, "kit": "Maverick", "stage": stage, "timeMs": 90_000,
            }
            if has_start:
                payload["startedAt"] = started_at
            status, response = mod_leaderboards.handle_submit(
                json.dumps(payload).encode("utf-8"), "history-" + str(index), self.db
            )
            self.assertEqual(status, 200)
            self.assertTrue(response["accepted"])

        status, page_one = mod_leaderboards.handle_get(
            "/api/v1/mod/history",
            {"context": ["ALL"], "limit": ["1"], "offset": ["0"]},
            self.db, {"1.8": {"original", "modern", "speed"}, "1.21": {"original", "modern", "classic"}}
        )
        self.assertEqual(status, 200)
        self.assertFalse(page_one["verified"])
        self.assertEqual(page_one["chronological"], "oldest-first")
        self.assertEqual(page_one["total"], 3)
        self.assertTrue(page_one["hasMore"])
        first_game = page_one["rows"][0]
        self.assertEqual(first_game["startedAt"], round_one)
        self.assertEqual(first_game["participantCount"], 2)
        self.assertEqual(first_game["maxStage"], 30)
        self.assertTrue(first_game["gameInfoAvailable"])

        status, page_two = mod_leaderboards.handle_get(
            "/api/v1/mod/history",
            {"context": ["ALL"], "limit": ["1"], "offset": ["1"]},
            self.db, {"1.8": {"original", "modern", "speed"}, "1.21": {"original", "modern", "classic"}}
        )
        self.assertEqual(status, 200)
        self.assertEqual(page_two["rows"][0]["startedAt"], round_two)
        self.assertEqual(page_two["rows"][0]["context"], "MIN")

        status, legacy_page = mod_leaderboards.handle_get(
            "/api/v1/mod/history",
            {"context": ["MPS"], "limit": ["10"], "offset": ["0"]},
            self.db, {"1.8": {"original", "modern", "speed"}, "1.21": {"original", "modern", "classic"}}
        )
        self.assertEqual(status, 200)
        legacy_rows = [row for row in legacy_page["rows"] if row["legacyUnknownStart"]]
        self.assertEqual(len(legacy_rows), 1)
        self.assertFalse(legacy_rows[0]["gameInfoAvailable"])
        self.assertEqual(legacy_rows[0]["startedAt"], 0)

    def test_game_history_rejects_invalid_pagination(self):
        status, response = mod_leaderboards.handle_get(
            "/api/v1/mod/history",
            {"limit": ["101"], "offset": ["0"]},
            self.db, {"1.8": {"original", "modern", "speed"}, "1.21": {"original", "modern", "classic"}}
        )
        self.assertEqual(status, 400)
        self.assertIn("limit", response["error"])

    def test_duplicate_submission_is_acknowledged_before_rate_limit(self):
        player_uuid = str(uuid.uuid4())
        submission_id = str(uuid.uuid4())
        payload = {
            "submissionId": submission_id, "uuid": player_uuid, "name": "MazeRunner",
            "context": "MIN", "game": "Monster Maze", "mode": "original",
            "pattern": 0, "kit": "Maverick", "stage": 16, "timeMs": 120000,
        }
        body = json.dumps(payload).encode("utf-8")
        first_status, first_response = mod_leaderboards.handle_submit(
            body, "test-ip-idempotency", self.db
        )
        self.assertEqual(first_status, 200)
        self.assertTrue(first_response["accepted"])

        # The same player/IP immediately retries after assuming its first response
        # was lost. This should ACK the existing row rather than return HTTP 429.
        retry_status, retry_response = mod_leaderboards.handle_submit(
            body, "test-ip-idempotency", self.db
        )
        self.assertEqual(retry_status, 200)
        self.assertTrue(retry_response["accepted"])
        self.assertTrue(retry_response["duplicate"])

        c = self.db()
        rows = c.execute(
            "SELECT COUNT(*) FROM community_mod_runs WHERE submission_id=?",
            (submission_id.lower(),),
        ).fetchone()[0]
        c.close()
        self.assertEqual(rows, 1)

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

    def test_community_player_profile_includes_global_category_placement(self):
        player_uuid = str(uuid.uuid4())
        candidates = [
            (player_uuid, "MazeRunner", 30, 95000),
            (str(uuid.uuid4()), "Stage31Runner", 31, 120000),
            (str(uuid.uuid4()), "FastRunnerA", 30, 70000),
            (str(uuid.uuid4()), "FastRunnerB", 30, 80000),
            (str(uuid.uuid4()), "FastRunnerC", 30, 90000),
        ]
        for index, (uuid_value, name, stage, time_ms) in enumerate(candidates):
            payload = {
                "submissionId": str(uuid.uuid4()), "uuid": uuid_value, "name": name,
                "context": "MIN", "game": "Monster Maze", "mode": "speed",
                "pattern": 2, "kit": "Jumper", "stage": stage, "timeMs": time_ms,
            }
            status, response = mod_leaderboards.handle_submit(
                json.dumps(payload).encode("utf-8"), "placement-community-ip-%d" % index, self.db
            )
            self.assertEqual(status, 200, response)
            self.assertTrue(response["accepted"])

        # A worse duplicate attempt must not push another player's category rank down;
        # profiles and leaderboards rank each participant's best result, not every attempt.
        duplicate_payload = {
            "submissionId": str(uuid.uuid4()), "uuid": candidates[2][0], "name": "FastRunnerA",
            "context": "MIN", "game": "Monster Maze", "mode": "speed",
            "pattern": 2, "kit": "Jumper", "stage": 30, "timeMs": 75000,
        }
        # Insert this duplicate directly because the upload endpoint deliberately
        # rate-limits rapid requests from a single player. The placement query must
        # still match the real leaderboard's ordering when multiple rows exist.
        c = self.db()
        mod_leaderboards._ensure_schema(c)
        c.execute(
            """INSERT INTO community_mod_runs
               (submission_id, uuid, name, context, game, mode, pattern, kit,
                stage, time_ms, submitted_at, started_at)
               VALUES(?,?,?,?,?,?,?,?,?,?,?,?)""",
            (duplicate_payload["submissionId"], duplicate_payload["uuid"], duplicate_payload["name"],
             duplicate_payload["context"], duplicate_payload["game"], duplicate_payload["mode"],
             duplicate_payload["pattern"], duplicate_payload["kit"], duplicate_payload["stage"],
             duplicate_payload["timeMs"], 1760000009000, 1760000000000)
        )
        c.commit()
        c.close()

        status, profile = mod_leaderboards.handle_get(
            "/api/v1/mod/player",
            {"source": ["community"], "uuid": [player_uuid], "context": ["MIN"],
             "mode": ["speed"], "pattern": ["2"], "kit": ["Jumper"]},
            self.db, {"1.8": {"original", "modern", "speed"}, "1.21": {"original", "modern", "classic"}}
        )
        self.assertEqual(status, 200)
        self.assertEqual(len(profile["rows"]), 1)
        row = profile["rows"][0]
        self.assertEqual((row["context"], row["mode"], row["pattern"], row["kit"], row["stage"]),
                         ("MIN", "speed", 2, "Jumper", 30))
        # The public category board lists individual run rows, including valid repeat attempts.
        # FastRunnerA's additional 75-second Stage 30 run appears before this 95-second row.
        self.assertEqual(row["placement"], 6)

    def test_server_player_profile_includes_global_category_placement(self):
        player_uuid = str(uuid.uuid4())
        c = self.db()
        candidates = [
            ("1.8", "speed", 2, "Jumper", player_uuid, "MazeRunner", 30, 95000),
            ("1.8", "speed", 2, "Jumper", str(uuid.uuid4()), "Stage31Runner", 31, 120000),
            ("1.8", "speed", 2, "Jumper", str(uuid.uuid4()), "FastRunnerA", 30, 70000),
            ("1.8", "speed", 2, "Jumper", str(uuid.uuid4()), "FastRunnerB", 30, 80000),
            ("1.8", "speed", 2, "Jumper", str(uuid.uuid4()), "FastRunnerC", 30, 90000),
        ]
        c.executemany("INSERT INTO runs VALUES(?,?,?,?,?,?,?,?)", candidates)
        c.commit()
        c.close()

        status, profile = mod_leaderboards.handle_get(
            "/api/v1/mod/player",
            {"source": ["server"], "uuid": [player_uuid], "platform": ["1.8"],
             "mode": ["speed"], "pattern": ["2"], "kit": ["Jumper"]},
            self.db, {"1.8": {"original", "modern", "speed"}, "1.21": {"original", "modern", "classic"}}
        )
        self.assertEqual(status, 200)
        self.assertTrue(profile["verified"])
        self.assertEqual(len(profile["rows"]), 1)
        self.assertEqual(profile["rows"][0]["placement"], 5)

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
