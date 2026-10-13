"""Tests for the safe JNP Mod community-run administration tool."""
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
        conn = sqlite3.connect(self.db_path)
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

    def test_missing_database_is_not_created(self):
        missing = self.root / "missing.sqlite"
        with self.assertRaises(admin.AdminError):
            admin.list_entries(missing)
        self.assertFalse(missing.exists())


if __name__ == "__main__":
    unittest.main()
