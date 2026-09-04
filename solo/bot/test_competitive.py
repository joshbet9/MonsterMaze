import sqlite3
import unittest
from datetime import datetime, timezone

import competitive


class CompetitiveTests(unittest.TestCase):
    def setUp(self):
        self.db = sqlite3.connect(":memory:")
        competitive.ensure_schema(self.db)
        self.season = competitive.ensure_current_season(
            self.db, datetime(2026, 8, 31, 12, tzinfo=timezone.utc)
        )
        self.sid = int(self.season[0])

    def tearDown(self):
        self.db.close()

    def add_players(self, *players):
        for uuid, name in players:
            competitive.ensure_player(self.db, self.sid, uuid, name)
        self.db.commit()

    def test_one_v_one_elo(self):
        self.add_players(("a", "Alice"), ("b", "Bob"))
        competitive.record_match(self.db, {
            "id": "m1", "platform": "1.8", "mode": "modern", "pattern": 0,
            "kit": "Jumper", "started_at": 1, "ended_at": 2, "season_id": self.sid,
        }, [
            {"uuid": "a", "name": "Alice", "placement": 1, "elimination_tick": -1},
            {"uuid": "b", "name": "Bob", "placement": 2, "elimination_tick": 10},
        ])
        rows = dict(self.db.execute(
            "SELECT uuid,elo FROM season_players WHERE season_id=?", (self.sid,)
        ).fetchall())
        self.assertAlmostEqual(rows["a"], 1016.0, places=6)
        self.assertAlmostEqual(rows["b"], 984.0, places=6)

    def test_four_player_reverse_upset(self):
        self.add_players(("a", "A"), ("b", "B"), ("c", "C"), ("d", "D"))
        for uuid, elo in (("a", 1400), ("b", 1200), ("c", 1000), ("d", 800)):
            self.db.execute("UPDATE season_players SET elo=? WHERE season_id=? AND uuid=?", (elo, self.sid, uuid))
        self.db.commit()
        competitive.record_match(self.db, {
            "id": "m2", "platform": "1.8", "mode": "modern", "pattern": 1,
            "kit": "Jumper", "started_at": 1, "ended_at": 2, "season_id": self.sid,
        }, [
            {"uuid": "d", "name": "D", "placement": 1, "elimination_tick": -1},
            {"uuid": "c", "name": "C", "placement": 2, "elimination_tick": 10},
            {"uuid": "b", "name": "B", "placement": 3, "elimination_tick": 20},
            {"uuid": "a", "name": "A", "placement": 4, "elimination_tick": 30},
        ])
        rows = dict(self.db.execute(
            "SELECT uuid,elo FROM season_players WHERE season_id=?", (self.sid,)
        ).fetchall())
        self.assertAlmostEqual(rows["a"], 1371.9, delta=0.2)
        self.assertAlmostEqual(rows["b"], 1190.3, delta=0.2)
        self.assertAlmostEqual(rows["c"], 1009.7, delta=0.2)
        self.assertAlmostEqual(rows["d"], 828.1, delta=0.2)

    def test_tied_placements_use_half_score(self):
        self.add_players(("a", "A"), ("b", "B"), ("c", "C"))
        competitive.record_match(self.db, {
            "id": "m3", "platform": "1.8", "mode": "modern", "pattern": 0,
            "kit": "Jumper", "started_at": 1, "ended_at": 2, "season_id": self.sid,
        }, [
            {"uuid": "a", "name": "A", "placement": 1, "elimination_tick": -1},
            {"uuid": "b", "name": "B", "placement": 2, "elimination_tick": 10},
            {"uuid": "c", "name": "C", "placement": 2, "elimination_tick": 10},
        ])
        rows = dict(self.db.execute(
            "SELECT uuid,elo FROM season_players WHERE season_id=?", (self.sid,)
        ).fetchall())
        self.assertGreater(rows["a"], 1000.0)
        self.assertAlmostEqual(rows["b"], rows["c"], places=6)

    def test_mmr_recalculates_against_current_kit_best(self):
        self.db.execute("CREATE TABLE runs(platform TEXT,mode TEXT,pattern INTEGER,kit TEXT,uuid TEXT,name TEXT,stage INTEGER,time_ms INTEGER,PRIMARY KEY(platform,mode,pattern,kit,uuid))")
        self.db.execute("INSERT INTO runs VALUES('1.8','modern',0,'Jumper','a','A',10,1000)")
        self.db.execute("INSERT INTO runs VALUES('1.8','modern',0,'Jumper','b','B',8,1000)")
        self.db.commit()
        competitive.calculate_mmr(self.db)
        before = self.db.execute("SELECT mmr FROM permanent_ratings WHERE uuid='b'").fetchone()[0]
        self.db.execute("UPDATE runs SET stage=20 WHERE uuid='a'")
        self.db.commit()
        competitive.calculate_mmr(self.db)
        after = self.db.execute("SELECT mmr FROM permanent_ratings WHERE uuid='b'").fetchone()[0]
        self.assertAlmostEqual(before, 800.0, places=6)
        self.assertAlmostEqual(after, 400.0, places=6)


if __name__ == "__main__":
    unittest.main()
