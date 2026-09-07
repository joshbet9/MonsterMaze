import sqlite3
import unittest
from datetime import datetime, timezone, timedelta

import competitive


class CompetitiveTests(unittest.TestCase):
    def setUp(self):
        self.db = sqlite3.connect(":memory:")
        competitive.ensure_schema(self.db)
        self.db.execute("CREATE TABLE competitions(platform TEXT,mode TEXT,pattern INTEGER,kit TEXT,start_ts TEXT,end_ts TEXT)")
        self.db.execute("CREATE TABLE submissions(uuid TEXT,name TEXT,platform TEXT,mode TEXT,pattern INTEGER,kit TEXT,submitted_at INTEGER,stage INTEGER)")
        self.db.execute("CREATE TABLE runs(platform TEXT,mode TEXT,pattern INTEGER,kit TEXT,uuid TEXT,name TEXT,stage INTEGER,time_ms INTEGER,PRIMARY KEY(platform,mode,pattern,kit,uuid))")
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

    def add_competition(self, platform="1.21", mode="modern", pattern=1, kit="Jumper", start=None, end=None):
        start = start or datetime.fromisoformat(self.season[2]).astimezone(timezone.utc)
        end = end or start + timedelta(days=7)
        self.db.execute("INSERT INTO competitions VALUES(?,?,?,?,?,?)", (platform, mode, pattern, kit, start.isoformat(), end.isoformat()))
        return start, end

    def add_submission(self, uuid, stage, submitted_at, platform="1.21", mode="modern", pattern=1, kit="Jumper", name=None):
        self.db.execute("INSERT INTO submissions VALUES(?,?,?,?,?,?,?,?)", (uuid, name or uuid, platform, mode, pattern, kit, int(submitted_at), int(stage)))

    def test_weekly_points_are_linear_dynamic_pool(self):
        start = datetime.fromisoformat(self.season[2]).astimezone(timezone.utc)
        end = start + timedelta(days=7)
        self.add_competition(start=start, end=end)
        submitted_at = int((start + timedelta(hours=1)).timestamp() * 1000)
        for uuid, stage in (("a", 10), ("b", 8), ("c", 8), ("d", 4)):
            self.add_submission(uuid, stage, submitted_at)
        self.db.commit()
        competitive.calculate_weekly(self.db, self.sid)
        rows = dict(self.db.execute("SELECT uuid,weekly_points FROM season_players WHERE season_id=?", (self.sid,)).fetchall())
        self.assertEqual(rows, {"a": 160, "b": 100, "c": 100, "d": 40})
        self.assertEqual(sum(rows.values()), 400)

    def test_weekly_points_accumulate_across_competitions(self):
        season_start = datetime.fromisoformat(self.season[2]).astimezone(timezone.utc)
        self.add_competition(platform="1.8", kit="Slowballer", start=season_start, end=season_start + timedelta(days=3))
        self.add_competition(platform="1.21", kit="Repulsor", start=season_start + timedelta(days=3), end=season_start + timedelta(days=6))
        t1 = int((season_start + timedelta(hours=1)).timestamp() * 1000)
        t2 = int((season_start + timedelta(days=4)).timestamp() * 1000)
        self.add_submission("alice", 20, t1, platform="1.8", kit="Slowballer")
        self.add_submission("bob", 10, t1, platform="1.8", kit="Slowballer")
        self.add_submission("alice", 30, t2, platform="1.21", kit="Repulsor")
        self.add_submission("bob", 20, t2, platform="1.21", kit="Repulsor")
        self.db.commit()
        competitive.calculate_weekly(self.db, self.sid)
        rows = dict(self.db.execute("SELECT uuid,weekly_points FROM season_players WHERE season_id=?", (self.sid,)).fetchall())
        self.assertEqual(rows["alice"], 266)
        self.assertEqual(rows["bob"], 134)
        self.assertEqual(rows["alice"] + rows["bob"], 400)

    def test_weekly_active_competition_does_not_award_points(self):
        self.add_players(("alice", "Alice"), ("bob", "Bob"))
        start = datetime.now(timezone.utc) - timedelta(hours=1)
        end = datetime.now(timezone.utc) + timedelta(days=1)
        self.add_competition(start=start, end=end)
        submitted_at = int((start + timedelta(minutes=10)).timestamp() * 1000)
        self.add_submission("alice", 20, submitted_at)
        self.add_submission("bob", 10, submitted_at)
        self.db.commit()
        competitive.calculate_weekly(self.db, self.sid)
        rows = dict(self.db.execute("SELECT uuid,weekly_points FROM season_players WHERE season_id=?", (self.sid,)).fetchall())
        self.assertEqual(rows["alice"], 0)
        self.assertEqual(rows["bob"], 0)

    def test_mmr_recalculates_against_current_kit_best(self):
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

    def test_get_mmr_target_prefers_weakest_configuration(self):
        self.db.execute("INSERT INTO runs VALUES('1.8','modern',0,'Jumper','a','A',10,1000)")
        self.db.execute("INSERT INTO runs VALUES('1.8','classic',1,'Repulsor','a','A',4,1000)")
        self.db.execute("INSERT INTO runs VALUES('1.8','original',0,'Slowball','b','B',20,1000)")
        self.db.commit()
        target = competitive.get_mmr_target(self.db, "a", "1.8")
        self.assertEqual(target["mode"], "original")
        self.assertEqual(target["pattern"], 0)
        self.assertEqual(target["kit"], "Slowball")
        self.assertEqual(target["pb"], 0)
        self.assertEqual(target["worldBest"], 20)

    def test_tournament_points_recalculate_mmcl(self):
        self.add_players(("a", "A"), ("b", "B"))
        self.db.execute("INSERT INTO tournaments VALUES(?,?,?,?,?,?,?,?,?)", (self.sid, 1, "Test", 0, 0, 1, "registration", 2, None))
        tournament_id = self.db.execute("SELECT id FROM tournaments ORDER BY id DESC LIMIT 1").fetchone()[0]
        self.db.execute("INSERT INTO tournament_players VALUES(?,?,?,?,?,?,?)", (tournament_id, "a", "A", 1, 1, None, 0))
        self.db.execute("INSERT INTO tournament_players VALUES(?,?,?,?,?,?,?)", (tournament_id, "b", "B", 2, 1, None, 0))
        self.db.commit()
        competitive.award_tournament_points(self.db, tournament_id, {"a": 1, "b": 2})
        rows = dict(self.db.execute("SELECT uuid,tournament_points FROM season_players WHERE season_id=?", (self.sid,)).fetchall())
        self.assertEqual(rows["a"], 100)
        self.assertEqual(rows["b"], 75)

    def test_finalize_season_archives_and_closes_tournaments(self):
        self.add_players(("a", "Alice"))
        self.db.execute("INSERT INTO tournaments VALUES(?,?,?,?,?,?,?,?,?)", (self.sid, 1, "Test", 0, 0, 1, "registration", 2, None))
        self.db.commit()
        competitive.finalize_season(self.db, self.sid)
        season_status = self.db.execute("SELECT status FROM seasons WHERE id=?", (self.sid,)).fetchone()[0]
        tournament_status = self.db.execute("SELECT status FROM tournaments WHERE season_id=?", (self.sid,)).fetchone()[0]
        self.assertEqual(season_status, "archived")
        self.assertEqual(tournament_status, "complete")

    def test_season_rollover_archives_old_and_creates_new(self):
        self.add_players(("a", "Alice"), ("b", "Bob"))
        self.db.execute("UPDATE season_players SET elo=1200,weekly_points=100,tournament_points=50 WHERE season_id=? AND uuid='a'", (self.sid,))
        self.db.execute("UPDATE season_players SET elo=1000,weekly_points=50,tournament_points=25 WHERE season_id=? AND uuid='b'", (self.sid,))
        self.db.commit()
        rollover_time = datetime(2026, 12, 1, 12, tzinfo=timezone.utc)
        new_season = competitive.ensure_current_season(self.db, rollover_time)
        self.assertEqual(int(new_season[1]), int(self.season[1]) + 1)
        old = self.db.execute("SELECT status,finalized_at FROM seasons WHERE id=?", (self.sid,)).fetchone()
        self.assertEqual(old[0], "archived")
        self.assertIsNotNone(old[1])
        current = self.db.execute("SELECT status FROM seasons WHERE id=?", (int(new_season[0]),)).fetchone()[0]
        self.assertEqual(current, "current")
        historical = competitive.season_summary(self.db, self.sid)
        self.assertEqual(historical["status"], "archived")
        self.assertEqual(historical["number"], int(self.season[1]))
        self.assertEqual(historical["players"][0]["uuid"], "a")
        self.assertGreater(historical["players"][0]["mmcl"], historical["players"][1]["mmcl"])
        mmcl = competitive.season_leaderboard(self.db, self.sid, "mmcl", 10)
        self.assertEqual(mmcl[0]["rank"], 1)
        self.assertEqual(mmcl[0]["uuid"], "a")
        history = competitive.player_season_history(self.db, "A")
        self.assertEqual(len(history), 1)
        self.assertEqual(history[0]["season"], int(self.season[1]))
        self.assertEqual(history[0]["status"], "archived")

    def test_season_summary_does_not_recalculate_archived_values(self):
        self.add_players(("a", "Alice"))
        self.db.execute("UPDATE season_players SET elo=1500,weekly_points=200,tournament_points=100 WHERE season_id=? AND uuid='a'", (self.sid,))
        self.db.commit()
        competitive.finalize_season(self.db, self.sid)
        archived_mmcl = self.db.execute("SELECT mmcl FROM season_players WHERE season_id=? AND uuid='a'", (self.sid,)).fetchone()[0]
        summary = competitive.season_summary(self.db, self.sid)
        self.assertEqual(summary["players"][0]["mmcl"], round(archived_mmcl, 3))


if __name__ == "__main__":
    unittest.main()
