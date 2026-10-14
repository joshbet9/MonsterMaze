"""Tests for the safe JNP Mod community-run administration tool."""
import json
import sqlite3
import tempfile
import unittest
import uuid
from pathlib import Path

import admin_mod_runs as admin


class AdminModRunsTests(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.root = Path(self.tmp.name)
        self.db_path = self.root / "leaderboard.db"
        self.backup_dir = self.root / "backups"
        self.ids = [str(uuid.uuid4()), str(uuid.uuid4())]
        self.players = [str(uuid.uuid4()), str(uuid.uuid4())]
        self.hh_id = str(uuid.uuid4())
        self.hh_run_id = str(uuid.uuid4())
        self.hh_player = str(uuid.uuid4())
        self.hh_members = [{"uuid": self.hh_player, "name": "HavocRunner"}]
        conn = sqlite3.connect(self.db_path)
        conn.execute("""CREATE TABLE community_hh_runs (
            submission_id TEXT PRIMARY KEY, run_id TEXT NOT NULL, uuid TEXT NOT NULL,
            name TEXT NOT NULL, context TEXT NOT NULL, server_type TEXT NOT NULL,
            server_node TEXT NOT NULL DEFAULT '', started_at INTEGER NOT NULL, ended_at INTEGER NOT NULL,
            time_ms INTEGER NOT NULL, reported_time_ms INTEGER NOT NULL DEFAULT 0, split_count INTEGER NOT NULL DEFAULT 0,
            team_members_json TEXT NOT NULL, team_signature TEXT NOT NULL, shared_run_key TEXT NOT NULL DEFAULT '',
            finish_reason TEXT NOT NULL DEFAULT '', successful INTEGER NOT NULL, run_status TEXT NOT NULL,
            submitted_at INTEGER NOT NULL
        )""")
        conn.execute("""CREATE TABLE community_hh_splits (
            submission_id TEXT NOT NULL, split_index INTEGER NOT NULL, run_id TEXT NOT NULL,
            uuid TEXT NOT NULL, name TEXT NOT NULL, context TEXT NOT NULL, server_type TEXT NOT NULL,
            server_node TEXT NOT NULL DEFAULT '', team_members_json TEXT NOT NULL, team_signature TEXT NOT NULL,
            objective TEXT NOT NULL, split_ms INTEGER NOT NULL, elapsed_ms INTEGER NOT NULL,
            status TEXT NOT NULL DEFAULT 'PASSED', run_successful INTEGER NOT NULL DEFAULT 0,
            run_status TEXT NOT NULL DEFAULT 'PARTIAL', submitted_at INTEGER NOT NULL,
            PRIMARY KEY (submission_id, split_index)
        )""")
        conn.execute("""CREATE TABLE community_mod_runs (
            submission_id TEXT PRIMARY KEY, uuid TEXT NOT NULL, name TEXT NOT NULL,
            context TEXT NOT NULL, game TEXT NOT NULL, mode TEXT NOT NULL DEFAULT 'unknown',
            pattern INTEGER NOT NULL DEFAULT -1, kit TEXT NOT NULL DEFAULT 'Unknown',
            stage INTEGER NOT NULL, time_ms INTEGER NOT NULL DEFAULT 0, submitted_at INTEGER NOT NULL
        )""")
        conn.execute("""CREATE TABLE runs (
            platform TEXT NOT NULL, mode TEXT NOT NULL, pattern INTEGER NOT NULL, kit TEXT NOT NULL,
            uuid TEXT NOT NULL, name TEXT, stage INTEGER NOT NULL, time_ms INTEGER NOT NULL,
            PRIMARY KEY(platform, mode, pattern, kit, uuid)
        )""")
        conn.execute("INSERT INTO runs VALUES('1.8','original',0,'Jumper',?,'OfficialPB',44,1000)",
                     (str(uuid.uuid4()),))
        members_json = json.dumps(self.hh_members, separators=(",", ":"))
        conn.execute("""INSERT INTO community_hh_runs VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)""", (
            self.hh_id, self.hh_run_id, self.hh_player, "HavocRunner", "HH", "STANDARD", "HH-1",
            1760000000000, 1760000030000, 30000, 0, 1, members_json, self.hh_player,
            "shared-hh-test", "Mineplex completion", 1, "COMPLETED", 3000
        ))
        conn.execute("""INSERT INTO community_hh_splits VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)""", (
            self.hh_id, 1, self.hh_run_id, self.hh_player, "HavocRunner", "HH", "STANDARD",
            "HH-1", members_json, self.hh_player, "Pumpkins", 30000, 30000,
            "PASSED", 1, "COMPLETED", 3000
        ))
        conn.executemany("INSERT INTO community_mod_runs VALUES(?,?,?,?,?,?,?,?,?,?,?)", [
            (self.ids[0], self.players[0], "MazeRunner", "MPS", "Monster Maze", "modern", 1, "Jumper", 22, 120000, 1000),
            (self.ids[1], self.players[1], "OtherRunner", "MIN", "Monster Maze", "speed", 2, "Maverick", 31, 130000, 2000),
        ])
        conn.commit()
        conn.close()

    def tearDown(self):
        self.tmp.cleanup()

    def test_list_filters_only_community_table(self):
        rows = admin.list_entries(self.db_path, {"context": "mps", "name": "mazer"})
        self.assertEqual(len(rows), 1)
        self.assertEqual(rows[0]["submission_id"], self.ids[0])

    def test_edit_creates_backup_and_audit_record(self):
        before, after, backup = admin.edit_entry(
            self.db_path, self.backup_dir, self.ids[0], {"stage": 29, "kit": "Repulsor"},
            confirmed=True, actor="test-admin",
        )
        self.assertEqual(before["stage"], 22)
        self.assertEqual(after["stage"], 29)
        self.assertEqual(after["kit"], "Repulsor")
        self.assertTrue(backup.is_file())
        backup_db = sqlite3.connect(backup)
        self.assertEqual(
            backup_db.execute("SELECT stage FROM community_mod_runs WHERE submission_id=?",
                              (self.ids[0],)).fetchone()[0], 22
        )
        backup_db.close()
        conn = sqlite3.connect(self.db_path)
        audit = conn.execute(
            "SELECT action,submission_id,actor FROM community_mod_run_admin_audit"
        ).fetchone()
        self.assertEqual(audit, ("edit", self.ids[0], "test-admin"))
        official = conn.execute("SELECT stage FROM runs").fetchone()[0]
        conn.close()
        self.assertEqual(official, 44)

    def test_delete_requires_confirmation_and_creates_backup(self):
        with self.assertRaises(admin.AdminError):
            admin.delete_entry(self.db_path, self.backup_dir, self.ids[0])
        deleted, backup = admin.delete_entry(
            self.db_path, self.backup_dir, self.ids[0], confirmed=True, actor="test-admin"
        )
        self.assertEqual(deleted["name"], "MazeRunner")
        self.assertTrue(backup.is_file())
        self.assertIsNone(admin.find_entry(self.db_path, self.ids[0]))
        conn = sqlite3.connect(self.db_path)
        audit = conn.execute(
            "SELECT action,after_json FROM community_mod_run_admin_audit"
        ).fetchone()
        official_count = conn.execute("SELECT COUNT(*) FROM runs").fetchone()[0]
        conn.close()
        self.assertEqual(audit, ("delete", None))
        self.assertEqual(official_count, 1)

    def test_invalid_edit_does_not_change_entry(self):
        with self.assertRaises(admin.AdminError):
            admin.edit_entry(self.db_path, self.backup_dir, self.ids[0], {"stage": 501}, confirmed=True)
        self.assertEqual(admin.find_entry(self.db_path, self.ids[0])["stage"], 22)
        self.assertFalse(self.backup_dir.exists())

    def test_cannot_edit_submission_id_or_game(self):
        with self.assertRaises(admin.AdminError):
            admin.edit_entry(self.db_path, self.backup_dir, self.ids[0],
                             {"game": "Other Game"}, confirmed=True)
        self.assertEqual(admin.find_entry(self.db_path, self.ids[0])["game"], "Monster Maze")

    def test_hh_list_show_and_edit_keep_run_and_splits_consistent(self):
        rows = admin.list_hh_entries(self.db_path, {"context": "hh", "name": "havoc"})
        self.assertEqual(len(rows), 1)
        self.assertEqual(rows[0]["submission_id"], self.hh_id)
        before, after, backup = admin.edit_hh_entry(
            self.db_path, self.backup_dir, self.hh_id,
            {"name": "HavocCorrected", "context": "HHC", "time_ms": 35000, "successful": False},
            confirmed=True, actor="test-admin",
        )
        self.assertEqual(before["name"], "HavocRunner")
        self.assertEqual(after["name"], "HavocCorrected")
        self.assertEqual(after["context"], "HHC")
        self.assertEqual(after["time_ms"], 35000)
        self.assertFalse(after["successful"])
        self.assertEqual(after["run_status"], "PARTIAL")
        self.assertEqual(after["splits"][0]["objective"], "Pumpkins")
        self.assertTrue(backup.is_file())
        backup_db = sqlite3.connect(backup)
        self.assertEqual(backup_db.execute(
            "SELECT name,context,time_ms FROM community_hh_runs WHERE submission_id=?", (self.hh_id,)
        ).fetchone(), ("HavocRunner", "HH", 30000))
        backup_db.close()
        conn = sqlite3.connect(self.db_path)
        split = conn.execute(
            "SELECT name,context,run_successful,run_status,team_members_json FROM community_hh_splits WHERE submission_id=?",
            (self.hh_id,)
        ).fetchone()
        audit = conn.execute(
            "SELECT action,submission_id,actor FROM community_hh_run_admin_audit"
        ).fetchone()
        official = conn.execute("SELECT stage FROM runs").fetchone()[0]
        conn.close()
        self.assertEqual(split[:4], ("HavocCorrected", "HHC", 0, "PARTIAL"))
        self.assertEqual(json.loads(split[4])[0]["name"], "HavocCorrected")
        self.assertEqual(audit, ("edit", self.hh_id, "test-admin"))
        self.assertEqual(official, 44)
        self.assertEqual(admin.find_hh_entry(self.db_path, self.hh_id)["name"], "HavocCorrected")

    def test_hh_delete_cascades_splits_with_backup_and_audit(self):
        with self.assertRaises(admin.AdminError):
            admin.delete_hh_entry(self.db_path, self.backup_dir, self.hh_id)
        deleted, backup = admin.delete_hh_entry(
            self.db_path, self.backup_dir, self.hh_id, confirmed=True, actor="test-admin"
        )
        self.assertEqual(deleted["submission_id"], self.hh_id)
        self.assertEqual(len(deleted["splits"]), 1)
        self.assertTrue(backup.is_file())
        self.assertIsNone(admin.find_hh_entry(self.db_path, self.hh_id))
        conn = sqlite3.connect(self.db_path)
        runs = conn.execute("SELECT COUNT(*) FROM community_hh_runs WHERE submission_id=?", (self.hh_id,)).fetchone()[0]
        splits = conn.execute("SELECT COUNT(*) FROM community_hh_splits WHERE submission_id=?", (self.hh_id,)).fetchone()[0]
        backup_db = sqlite3.connect(backup)
        backup_splits = backup_db.execute("SELECT COUNT(*) FROM community_hh_splits WHERE submission_id=?", (self.hh_id,)).fetchone()[0]
        backup_db.close()
        audit = conn.execute("SELECT action,submission_id,after_json FROM community_hh_run_admin_audit").fetchone()
        official_count = conn.execute("SELECT COUNT(*) FROM runs").fetchone()[0]
        conn.close()
        self.assertEqual((runs, splits), (0, 0))
        self.assertEqual(backup_splits, 1)
        self.assertEqual(audit, ("delete", self.hh_id, None))
        self.assertEqual(official_count, 1)

    def test_invalid_hh_edit_leaves_run_unchanged(self):
        with self.assertRaises(admin.AdminError):
            admin.edit_hh_entry(self.db_path, self.backup_dir, self.hh_id, {"context": "MPS"}, confirmed=True)
        self.assertEqual(admin.find_hh_entry(self.db_path, self.hh_id)["context"], "HH")
        self.assertFalse(self.backup_dir.exists())

    def test_missing_database_is_not_created(self):
        missing = self.root / "missing.sqlite"
        with self.assertRaises(admin.AdminError):
            admin.list_entries(missing)
        self.assertFalse(missing.exists())


if __name__ == "__main__":
    unittest.main()
