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

    def test_unknown_route_returns_none(self):
        self.assertIsNone(hh.handle_get("/api/v1/mod/other", {}, self.db))


if __name__ == "__main__":
    unittest.main()
