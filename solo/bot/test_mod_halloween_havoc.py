"""Tests for public Halloween Havoc/HHC community uploads and boards."""
import json
import os
import sqlite3
import tempfile
import unittest
import uuid

import mod_halloween_havoc as hh


class HalloweenHavocApiTests(unittest.TestCase):
    def setUp(self):
        handle, self.path = tempfile.mkstemp(suffix=".sqlite")
        os.close(handle)
        self.player_uuid = str(uuid.uuid4())
        self.member2_uuid = str(uuid.uuid4())
        self.member3_uuid = str(uuid.uuid4())
        self.member4_uuid = str(uuid.uuid4())
        self.team_signature = "test-quartet-signature"
        self.team_members = [
            {"uuid": self.player_uuid, "name": "RunnerA"},
            {"uuid": self.member2_uuid, "name": "RunnerB"},
            {"uuid": self.member3_uuid, "name": "RunnerC"},
            {"uuid": self.member4_uuid, "name": "RunnerD"},
        ]
        hh._PLAYER_LAST.clear()
        hh._IP_LAST.clear()

    def tearDown(self):
        try:
            os.unlink(self.path)
        except OSError:
            pass
        hh._PLAYER_LAST.clear()
        hh._IP_LAST.clear()

    def db(self):
        return sqlite3.connect(self.path)

    def payload(self, **overrides):
        base = {
            "submissionId": str(uuid.uuid4()),
            "runId": str(uuid.uuid4()),
            "uuid": self.player_uuid,
            "name": "RunnerA",
            "context": "HH",
            "serverType": "STANDARD",
            "serverNode": "HH-1",
            "lobbyPlayerCount": -1,
            "maxLobbyPlayerCount": -1,
            "startedAt": 1760000000000,
            "endedAt": 1760000030000,
            "timeMs": 30000,
            "reportedTimeMs": 0,
            "splitCount": 2,
            "teamMembers": self.team_members,
            "teamSignature": self.team_signature,
            "sharedRunKey": "shared-test-key",
            "finishReason": "Objective completed",
            "successful": True,
            "runStatus": "COMPLETED",
            "splits": [
                {"index": 1, "objective": "Pumpkins", "splitMs": 10000, "elapsedMs": 10000, "status": "PASSED", "completedAt": 1760000010000},
                {"index": 2, "objective": "Graveyard", "splitMs": 20000, "elapsedMs": 30000, "status": "PASSED", "completedAt": 1760000030000},
            ],
        }
        base.update(overrides)
        return base

    def submit(self, payload, ip="hh-test-ip"):
        return hh.handle_submit(json.dumps(payload).encode("utf-8"), ip, self.db)

    def get(self, query):
        return hh.handle_get("/api/v1/mod/halloween-havoc/leaderboard", query, self.db)

    def test_submit_creates_run_and_each_split(self):
        payload = self.payload()
        status, response = self.submit(payload)
        self.assertEqual(status, 200)
        self.assertTrue(response["ok"])
        self.assertTrue(response["accepted"])
        self.assertEqual(response["storedSplits"], 2)
        c = self.db()
        self.assertEqual(c.execute("SELECT COUNT(*) FROM community_hh_runs").fetchone()[0], 1)
        self.assertEqual(c.execute("SELECT COUNT(*) FROM community_hh_splits").fetchone()[0], 2)
        c.close()

    def test_duplicate_retry_is_acknowledged_without_duplicate_rows(self):
        payload = self.payload()
        self.assertEqual(self.submit(payload)[0], 200)
        status, response = self.submit(payload)
        self.assertEqual(status, 200)
        self.assertTrue(response["accepted"])
        self.assertTrue(response["duplicate"])
        c = self.db()
        self.assertEqual(c.execute("SELECT COUNT(*) FROM community_hh_runs").fetchone()[0], 1)
        self.assertEqual(c.execute("SELECT COUNT(*) FROM community_hh_splits").fetchone()[0], 2)
        c.close()

    def test_submission_id_cannot_be_reused_by_another_player(self):
        payload = self.payload()
        self.assertEqual(self.submit(payload)[0], 200)
        other = self.payload(uuid=self.member2_uuid, name="RunnerB", submissionId=payload["submissionId"])
        status, response = self.submit(other)
        self.assertEqual(status, 409)
        self.assertEqual(response["error"], "submission_id_conflict")

    def test_full_run_board_only_includes_successful_runs(self):
        successful = self.payload(timeMs=30000, successful=True, runStatus="COMPLETED")
        failed = self.payload(
            uuid=self.member2_uuid, name="RunnerB", timeMs=10000, successful=False, runStatus="FAILED",
            finishReason="Failed at final objective",
            splits=[{"index": 1, "objective": "Pumpkins", "splitMs": 4000, "elapsedMs": 4000, "status": "PASSED", "completedAt": 1760000004000}],
            splitCount=1,
        )
        self.assertEqual(self.submit(successful, "ip-a")[0], 200)
        self.assertEqual(self.submit(failed, "ip-b")[0], 200)
        status, board = self.get({"context": ["HH"], "serverType": ["STANDARD"], "kind": ["runs"]})
        self.assertEqual(status, 200)
        self.assertFalse(board["verified"])
        self.assertEqual(len(board["rows"]), 1)
        self.assertEqual(board["rows"][0]["timeMs"], 30000)
        self.assertTrue(board["rows"][0]["successful"])

    def test_bks_composite_uses_best_split_per_objective_and_marks_complete(self):
        first = self.payload()
        second = self.payload(
            uuid=self.member2_uuid,
            name="RunnerB",
            submissionId=str(uuid.uuid4()),
            runId=str(uuid.uuid4()),
            teamSignature="second-quartet-signature",
            serverNode="HH-2",
            teamMembers=[
                {"uuid": self.member2_uuid, "name": "RunnerB"},
                {"uuid": str(uuid.uuid4()), "name": "RunnerE"},
                {"uuid": str(uuid.uuid4()), "name": "RunnerF"},
                {"uuid": str(uuid.uuid4()), "name": "RunnerG"},
            ],
            splits=[
                {"index": 1, "objective": "Pumpkins", "splitMs": 5000, "elapsedMs": 5000,
                 "status": "PASSED", "completedAt": 1760000010000},
                {"index": 2, "objective": "Graveyard", "splitMs": 22000, "elapsedMs": 27000,
                 "status": "PASSED", "completedAt": 1760000030000},
            ],
        )
        self.assertEqual(self.submit(first, "bks-ip-a")[0], 200)
        self.assertEqual(self.submit(second, "bks-ip-b")[0], 200)
        status, response = self.get({"kind": ["bks"], "context": ["HH"], "serverType": ["STANDARD"]})
        self.assertEqual(status, 200, response)
        self.assertTrue(response["ok"])
        self.assertEqual(response["kind"], "bks")
        self.assertEqual(len(response["benchmarks"]), 1)
        benchmark = response["benchmarks"][0]
        self.assertTrue(benchmark["complete"])
        self.assertEqual(benchmark["requiredObjectiveCount"], 2)
        self.assertEqual(benchmark["totalTimeMs"], 25000)
        self.assertEqual([row["objective"] for row in benchmark["rows"]], ["Pumpkins", "Graveyard"])
        self.assertEqual(benchmark["rows"][0]["splitMs"], 5000)
        self.assertEqual(benchmark["rows"][0]["name"], "RunnerB")
        self.assertEqual(benchmark["rows"][0]["compositeElapsedMs"], 5000)
        self.assertEqual(benchmark["rows"][1]["compositeElapsedMs"], 25000)
        self.assertFalse(benchmark["verified"])
        self.assertEqual(benchmark["worldRecordTimeMs"], 30000)
        # BKS response keeps WR timing separate without duplicating the entire WR run
        # and its splits; the composite's own objective rows retain provenance.
        self.assertNotIn("worldRecord", benchmark)
        self.assertNotIn("rows", response)

    def test_repeated_objective_names_remain_separate_chronological_splits(self):
        route = [
            ("Lobby", 1000), ("Regroup", 2000), ("Push the Minecart", 3000),
            ("Regroup", 4000), ("Pumpkin King", 5000),
        ]
        splits = []
        elapsed = 0
        for index, (objective, duration) in enumerate(route, 1):
            elapsed += duration
            splits.append({
                "index": index, "objective": objective, "splitMs": duration,
                "elapsedMs": elapsed, "status": "PASSED", "completedAt": 1760000000000 + elapsed,
            })
        payload = self.payload(timeMs=elapsed, splitCount=len(splits), splits=splits)
        self.assertEqual(self.submit(payload, "repeat-objective-ip")[0], 200)

        status, bks = self.get({"kind": ["bks"], "context": ["HH"], "serverType": ["STANDARD"]})
        self.assertEqual(status, 200, bks)
        benchmark = bks["benchmarks"][0]
        self.assertTrue(benchmark["complete"])
        self.assertEqual(benchmark["requiredObjectiveCount"], 5)
        self.assertEqual([row["objective"] for row in benchmark["rows"]],
                         ["Lobby", "Regroup", "Push the Minecart", "Regroup", "Pumpkin King"])
        self.assertEqual([row["index"] for row in benchmark["rows"]], [1, 2, 3, 4, 5])

        status, repeated = self.get({
            "kind": ["splits"], "context": ["HH"], "serverType": ["STANDARD"], "objective": ["Regroup"]
        })
        self.assertEqual(status, 200, repeated)
        self.assertEqual([row["index"] for row in repeated["rows"]], [2, 4])

        status, one_stage = self.get({
            "kind": ["splits"], "context": ["HH"], "serverType": ["STANDARD"],
            "objective": ["Regroup"], "splitIndex": ["4"]
        })
        self.assertEqual(status, 200, one_stage)
        self.assertEqual([row["index"] for row in one_stage["rows"]], [4])

        status, group = self.get({
            "kind": ["group"], "context": ["HH"], "serverType": ["STANDARD"],
            "teamSignature": [self.team_signature]
        })
        self.assertEqual(status, 200, group)
        self.assertEqual([row["index"] for row in group["splits"]], [1, 2, 3, 4, 5])

    def test_bks_is_partial_when_no_completed_run_establishes_required_route(self):
        failed = self.payload(
            successful=False,
            runStatus="FAILED",
            finishReason="Failed before finish",
            splits=[{"index": 1, "objective": "Pumpkins", "splitMs": 4000, "elapsedMs": 4000,
                     "status": "PASSED", "completedAt": 1760000004000}],
            splitCount=1,
        )
        self.assertEqual(self.submit(failed, "bks-partial-ip")[0], 200)
        status, response = self.get({"kind": ["bks"], "context": ["HH"], "serverType": ["STANDARD"]})
        self.assertEqual(status, 200, response)
        benchmark = response["benchmarks"][0]
        self.assertFalse(benchmark["complete"])
        self.assertIsNone(benchmark["totalTimeMs"])
        self.assertEqual(benchmark["knownCompositeTimeMs"], 4000)
        self.assertEqual(benchmark["requiredObjectiveCount"], 0)

    def test_failed_run_splits_still_contribute_to_split_board(self):
        successful = self.payload(timeMs=30000, successful=True, runStatus="COMPLETED")
        failed = self.payload(
            uuid=self.member2_uuid, name="RunnerB", timeMs=10000, successful=False, runStatus="FAILED",
            finishReason="Failed at next objective",
            splits=[{"index": 1, "objective": "Pumpkins", "splitMs": 4000, "elapsedMs": 4000, "status": "PASSED", "completedAt": 1760000004000}],
            splitCount=1,
        )
        self.assertEqual(self.submit(successful, "ip-a")[0], 200)
        self.assertEqual(self.submit(failed, "ip-b")[0], 200)
        status, board = self.get({
            "context": ["HH"], "serverType": ["STANDARD"], "kind": ["splits"], "objective": ["Pumpkins"]
        })
        self.assertEqual(status, 200)
        self.assertEqual(len(board["rows"]), 1)
        self.assertEqual(board["rows"][0]["splitMs"], 4000)
        self.assertFalse(board["rows"][0]["runSuccessful"])

    def test_group_profile_includes_best_completed_run_and_failed_attempt_splits(self):
        successful = self.payload(timeMs=30000, successful=True, runStatus="COMPLETED")
        failed = self.payload(
            uuid=self.member2_uuid, name="RunnerB", timeMs=10000, successful=False, runStatus="PARTIAL",
            splits=[{"index": 1, "objective": "Pumpkins", "splitMs": 3000, "elapsedMs": 3000, "status": "PASSED", "completedAt": 1760000003000}],
            splitCount=1,
        )
        self.assertEqual(self.submit(successful, "ip-a")[0], 200)
        self.assertEqual(self.submit(failed, "ip-b")[0], 200)
        status, group = self.get({
            "context": ["HH"], "serverType": ["STANDARD"], "kind": ["group"],
            "teamSignature": [self.team_signature],
        })
        self.assertEqual(status, 200)
        self.assertEqual(group["attemptCount"], 2)
        self.assertEqual(group["run"]["timeMs"], 30000)
        self.assertEqual(len(group["splits"]), 2)
        best_pumpkin = next(row for row in group["splits"] if row["objective"] == "Pumpkins")
        self.assertEqual(best_pumpkin["splitMs"], 3000)
        self.assertFalse(best_pumpkin["runSuccessful"])
        self.assertTrue(any(row["objective"] == "Pumpkins" and int(row["splitMs"]) == 3000
                            for row in group["globalBestSplits"]))
        self.assertTrue(any(row["objective"] == "Pumpkins" and int(row["splitMs"]) == 3000
                            and row["uuid"] == self.player_uuid for row in group["memberBestSplits"]))


    def test_personal_history_returns_all_runs_and_splits_newest_first(self):
        first = self.payload(
            runId=str(uuid.uuid4()), submissionId=str(uuid.uuid4()),
            startedAt=1760000000000, endedAt=1760000030000, timeMs=30000,
        )
        self.assertEqual(self.submit(first, "history-ip-1")[0], 200)

        # Simulate elapsed time between separate runs; bypass the API's intentional
        # per-player submission throttle so this test focuses on history retrieval.
        hh._PLAYER_LAST.clear()
        hh._IP_LAST.clear()
        second = self.payload(
            runId=str(uuid.uuid4()), submissionId=str(uuid.uuid4()),
            startedAt=1760000100000, endedAt=1760000145000, timeMs=45000,
            splits=[
                {"index": 1, "objective": "Pumpkins", "splitMs": 15000, "elapsedMs": 15000, "status": "PASSED", "completedAt": 1760000115000},
                {"index": 2, "objective": "Graveyard", "splitMs": 30000, "elapsedMs": 45000, "status": "PASSED", "completedAt": 1760000145000},
            ],
            splitCount=2,
        )
        self.assertEqual(self.submit(second, "history-ip-2")[0], 200)

        status, result = self.get({"kind": ["history"], "uuid": [self.player_uuid]})
        self.assertEqual(status, 200)
        self.assertTrue(result["ok"])
        self.assertEqual(len(result["rows"]), 2)
        self.assertEqual(result["rows"][0]["runId"], second["runId"])
        self.assertEqual(result["rows"][1]["runId"], first["runId"])
        self.assertEqual(result["rows"][0]["uuid"], self.player_uuid)
        self.assertEqual(len(result["rows"][0]["splits"]), 2)
        self.assertEqual(result["rows"][0]["splits"][0]["objective"], "Pumpkins")

    def test_canonical_hh_route_has_ten_stable_checkpoints(self):
        expected = [
            "GIANT", "GENDO", "CLEAR_PATH", "CHALLENGES_COMPLETE", "MINECART_LAVA",
            "MAZE_EXIT", "BOARD_GREEN_SHIP", "BOTTOM_DECK_PORTAL",
            "PUMPKIN_KING_MINIONS", "PUMPKIN_KING",
        ]
        self.assertEqual(list(hh._CANONICAL_IDS), expected)
        self.assertEqual(len(hh._CANONICAL_CHECKPOINTS), 10)

    def test_canonicalization_keeps_known_skips_as_merged_non_comparable_interval(self):
        raw = [
            {"index": 1, "objective": "Defeat the giant", "splitMs": 30000, "elapsedMs": 30000, "status": "PASSED"},
            {"index": 2, "objective": "Defeat Gendo", "splitMs": 40000, "elapsedMs": 70000, "status": "PASSED"},
            # Known unreliable "Secure the mine entrance" / "Defeat the Molten Snake"
            # objectives are deliberately not canonical boundaries.
            {"index": 3, "objective": "Find a way to clear the path", "splitMs": 80000, "elapsedMs": 150000, "status": "PASSED"},
            {"index": 4, "objective": "Complete the 4 challenges", "splitMs": 60000, "elapsedMs": 210000, "status": "PASSED"},
            {"index": 5, "objective": "Guide the minecart across the lava", "splitMs": 25000, "elapsedMs": 235000, "status": "PASSED"},
            {"index": 6, "objective": "Find an exit to the maze", "splitMs": 50000, "elapsedMs": 285000, "status": "PASSED"},
            {"index": 7, "objective": "Board the Green Ship", "splitMs": 40000, "elapsedMs": 325000, "status": "PASSED"},
            {"index": 8, "objective": "Enter the portal on the bottom deck", "splitMs": 30000, "elapsedMs": 355000, "status": "PASSED"},
            {"index": 9, "objective": "Defeat the Pumpkin King's minions", "splitMs": 60000, "elapsedMs": 415000, "status": "PASSED"},
        ]
        result = hh._canonicalize_one_run(raw, total_elapsed_ms=450000, completed=True)
        self.assertEqual([item["checkpointId"] for item in result], list(hh._CANONICAL_IDS))
        self.assertEqual(result[-1]["boundarySource"], "CONFIRMED_RUN_END")
        self.assertEqual(result[-1]["elapsedMs"], 450000)
        self.assertEqual(sum(item["splitMs"] for item in result), 450000)
        # If an intermediate checkpoint really is absent, the interval is retained,
        # but BKS excludes it rather than claiming an exact segment time.
        skipped = hh._canonicalize_one_run([
            raw[0], raw[1], raw[3], raw[4], raw[5], raw[6], raw[7], raw[8]
        ], total_elapsed_ms=450000, completed=True)
        merged = next(item for item in skipped if item["checkpointId"] == "CHALLENGES_COMPLETE")
        self.assertEqual(merged["missingCheckpointIds"], ["CLEAR_PATH"])
        self.assertTrue(hh._split_has_missing_boundary(merged))
        self.assertNotEqual(merged["checkpointId"], "CLEAR_PATH")

    def test_hhc_is_separate_from_hh(self):
        hh_run = self.payload()
        hhc_run = self.payload(context="HHC", uuid=self.member2_uuid, name="RunnerB")
        self.assertEqual(self.submit(hh_run, "ip-a")[0], 200)
        self.assertEqual(self.submit(hhc_run, "ip-b")[0], 200)
        status, board = self.get({"context": ["HHC"], "kind": ["runs"]})
        self.assertEqual(status, 200)
        self.assertEqual(len(board["rows"]), 1)
        self.assertEqual(board["rows"][0]["context"], "HHC")

    def test_rejects_invalid_context_and_inconsistent_splits(self):
        bad_context = self.payload(context="ALL")
        self.assertEqual(self.submit(bad_context)[0], 400)
        bad_splits = self.payload(splits=[
            {"index": 1, "objective": "Pumpkins", "splitMs": 10000, "elapsedMs": 9000, "status": "PASSED"}
        ], splitCount=1)
        self.assertEqual(self.submit(bad_splits, "other-ip")[0], 400)

    def test_mps_run_requires_verified_population_within_four(self):
        payload = self.payload(serverType="MPS", lobbyPlayerCount=5, maxLobbyPlayerCount=5)
        status, response = self.submit(payload)
        self.assertEqual(status, 400)
        self.assertIn("MPS lobby", response["error"])

    def test_uploading_player_must_belong_to_team(self):
        payload = self.payload(teamMembers=[{"uuid": self.member2_uuid, "name": "RunnerB"}])
        status, response = self.submit(payload)
        self.assertEqual(status, 400)
        self.assertIn("uploading player", response["error"])

    def test_schema_migrates_older_split_table_to_include_run_status(self):
        old = sqlite3.connect(self.path)
        old.execute("""CREATE TABLE community_hh_splits (
            submission_id TEXT NOT NULL, split_index INTEGER NOT NULL, run_id TEXT NOT NULL,
            uuid TEXT NOT NULL, name TEXT NOT NULL, context TEXT NOT NULL, server_type TEXT NOT NULL,
            server_node TEXT NOT NULL DEFAULT '', team_members_json TEXT NOT NULL,
            team_signature TEXT NOT NULL, objective TEXT NOT NULL, split_ms INTEGER NOT NULL,
            elapsed_ms INTEGER NOT NULL, status TEXT NOT NULL DEFAULT 'PASSED',
            run_successful INTEGER NOT NULL DEFAULT 0, submitted_at INTEGER NOT NULL,
            PRIMARY KEY (submission_id, split_index)
        )""")
        hh._ensure_schema(old)
        columns = {row[1] for row in old.execute("PRAGMA table_info(community_hh_splits)").fetchall()}
        old.close()
        self.assertIn("run_status", columns)
        self.assertIn("checkpoint_id", columns)
        self.assertIn("from_checkpoint_id", columns)
        self.assertIn("missing_checkpoint_ids_json", columns)
        self.assertIn("boundary_source", columns)
        self.assertIn("schema_version", columns)

    def test_canonical_partial_route_preserves_total_time_and_stable_indexes(self):
        route = [
            ("Defeat the Giant", 8000),
            ("Defeat Gendo", 11000),
            ("Complete the 4 challenges", 15000),
            ("Find an exit to the maze", 9000),
            ("Board the Green Ship", 13000),
            ("Enter the portal on the bottom deck", 10000),
            ("Defeat the Pumpkin King's Minions", 7000),
            ("Pumpkin King", 9000),
        ]
        elapsed = 0
        raw_splits = []
        for index, (objective, duration) in enumerate(route, 1):
            elapsed += duration
            raw_splits.append({
                "index": index, "splitIndex": index, "objective": objective,
                "splitMs": duration, "elapsedMs": elapsed, "status": "PASSED",
                "submittedAt": 1760000000000 + elapsed,
            })

        canonical = hh._canonicalize_one_run(raw_splits, elapsed, True)
        self.assertEqual([row["checkpointId"] for row in canonical], [
            "GIANT", "GENDO", "CHALLENGES_COMPLETE", "MAZE_EXIT",
            "BOARD_GREEN_SHIP", "BOTTOM_DECK_PORTAL",
            "PUMPKIN_KING_MINIONS", "PUMPKIN_KING",
        ])
        # Stable indexes retain the ten-checkpoint route positions instead of being
        # renumbered compactly when a boundary is missing.
        self.assertEqual([row["splitIndex"] for row in canonical], [1, 2, 4, 6, 7, 8, 9, 10])
        self.assertEqual(sum(row["splitMs"] for row in canonical), elapsed)
        self.assertEqual(canonical[-1]["elapsedMs"], elapsed)
        self.assertEqual(canonical[2]["missingCheckpointIds"], ["CLEAR_PATH"])
        self.assertEqual(canonical[3]["missingCheckpointIds"], ["MINECART_LAVA"])
        self.assertTrue(hh._split_has_missing_boundary(canonical[2]))
        self.assertTrue(hh._split_has_missing_boundary(canonical[3]))
        self.assertTrue(all(not row["missingCheckpointIds"] for row in canonical[4:]))

    def test_missing_canonical_checkpoint_is_flagged_and_not_comparable(self):
        route = [
            ("Defeat the Giant", 8000),
            ("Defeat Gendo", 11000),
            ("Find a way to clear the path", 12000),
            # The 4-challenges checkpoint is absent, but the next two milestones exist.
            ("Guide the minecart across the lava", 24000),
            ("Find an exit to the maze", 13000),
            ("Board the Green Ship", 13000),
            ("Enter the portal on the bottom deck", 10000),
            ("Defeat the Pumpkin King's Minions", 7000),
            ("Pumpkin King", 9000),
        ]
        elapsed = 0
        raw_splits = []
        for index, (objective, duration) in enumerate(route, 1):
            elapsed += duration
            raw_splits.append({
                "index": index, "splitIndex": index, "objective": objective,
                "splitMs": duration, "elapsedMs": elapsed, "status": "PASSED",
                "submittedAt": 1760000000000 + elapsed,
            })

        canonical = hh._canonicalize_one_run(raw_splits, elapsed, True)
        lava = next(row for row in canonical if row["checkpointId"] == "MINECART_LAVA")
        self.assertEqual(lava["missingCheckpointIds"], ["CHALLENGES_COMPLETE"])
        self.assertTrue(hh._split_has_missing_boundary(lava))
        self.assertEqual(sum(row["splitMs"] for row in canonical), elapsed)
        self.assertEqual(canonical[-1]["elapsedMs"], elapsed)

    def test_canonical_route_is_fixed_for_bks_when_multiple_milestones_are_known(self):
        rows = [
            {"objective": "Defeat the Giant", "elapsedMs": 8000, "splitMs": 8000, "index": 1},
            {"objective": "Defeat Gendo", "elapsedMs": 19000, "splitMs": 11000, "index": 2},
            {"objective": "Find an exit to the maze", "elapsedMs": 43000, "splitMs": 24000, "index": 3},
            {"objective": "Board the Green Ship", "elapsedMs": 56000, "splitMs": 13000, "index": 4},
        ]
        canonical = hh._canonicalize_one_run(rows, 56000, False)
        self.assertEqual([row["objective"] for row in canonical], [
            "Giant", "Gendo", "Maze Exit", "Board Green Ship"
        ])
        self.assertEqual(canonical[2]["missingCheckpointIds"], [
            "CLEAR_PATH", "CHALLENGES_COMPLETE", "MINECART_LAVA"
        ])
        # Once the Maze Exit boundary is present, the following interval is clean.
        self.assertEqual(canonical[3]["missingCheckpointIds"], [])
        self.assertFalse(hh._split_has_missing_boundary(canonical[1]))
        self.assertTrue(hh._split_has_missing_boundary(canonical[2]))
        self.assertFalse(hh._split_has_missing_boundary(canonical[3]))

    def test_unknown_route_returns_none(self):
        self.assertIsNone(hh.handle_get("/api/v1/mod/other", {}, self.db))


if __name__ == "__main__":
    unittest.main()
