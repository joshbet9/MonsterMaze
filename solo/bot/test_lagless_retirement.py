import sqlite3
import unittest

import api_server


class LaglessRetirementTests(unittest.TestCase):
    def test_lagless_is_not_an_allowed_mode(self):
        self.assertEqual(api_server.validate_mode("1.8", "Modern"), "modern")
        self.assertEqual(api_server.validate_mode("1.21", "Classic"), "classic")
        with self.assertRaises(ValueError):
            api_server.validate_mode("1.8", "Lagless")
        with self.assertRaises(ValueError):
            api_server.validate_mode("1.21", "Lagless")

    def test_cleanup_removes_lagless_records_but_preserves_supported_modes(self):
        c = sqlite3.connect(":memory:")
        c.execute("CREATE TABLE submissions(id TEXT PRIMARY KEY, mode TEXT)")
        c.execute("CREATE TABLE runs(platform TEXT, mode TEXT, pattern INTEGER, kit TEXT, uuid TEXT, stage INTEGER)")
        c.execute("CREATE TABLE permanent_ratings(uuid TEXT PRIMARY KEY, name TEXT, mmr REAL, updated_at INTEGER)")
        c.execute("INSERT INTO submissions VALUES('old-lagless', 'Lagless')")
        c.execute("INSERT INTO submissions VALUES('modern-run', 'Modern')")
        c.execute("INSERT INTO runs VALUES('1.8', 'lagless', 0, 'Jumper', 'old', 50)")
        c.execute("INSERT INTO runs VALUES('1.8', 'modern', 0, 'Jumper', 'new', 25)")
        c.execute("INSERT INTO permanent_ratings VALUES('old', 'Old', 500, 1)")
        c.execute("INSERT INTO permanent_ratings VALUES('new', 'New', 250, 1)")
        api_server.retired_mode_cleanup(c)
        self.assertEqual(c.execute("SELECT COUNT(*) FROM submissions WHERE lower(mode)='lagless'").fetchone()[0], 0)
        self.assertEqual(c.execute("SELECT COUNT(*) FROM runs WHERE lower(mode)='lagless'").fetchone()[0], 0)
        self.assertEqual(c.execute("SELECT COUNT(*) FROM runs WHERE lower(mode)='modern'").fetchone()[0], 1)
        self.assertEqual(c.execute("SELECT COUNT(*) FROM permanent_ratings WHERE uuid='old'").fetchone()[0], 0)
        self.assertEqual(c.execute("SELECT COUNT(*) FROM permanent_ratings WHERE uuid='new'").fetchone()[0], 1)
        c.close()


if __name__ == "__main__":
    unittest.main()
