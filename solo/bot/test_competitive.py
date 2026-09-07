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

    def test_one_v_one_elo(self):
        self.add_players(("a", "Alice"), ("b", "Bob"))
        competitive.record_match(self.db, {
            "id": "m1", "platform": "1.8", "mode": "modern", "pattern": 0,
            "kit": "Jumper", "started_at": 1, "ended_at": 2, "season_id": self.sid,
        }, [
            {"uuid": "a", "name": "Alice", "placement": 1, "elimination_tick": -1},
            {"uuid": "b", "name": "Bob", "placement": 2, "elimination_tick": 10},
        ])
        rows = dict(self.db.execute("SELECT uuid,elo FROM season_players WHERE season_id=?", (self.sid,)).fetchall())
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
        rows = dict(self.db.execute("SELECT uuid,elo FROM season_players WHERE season_id=?", (self.sid,)).fetchall())
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
        rows = dict(self.db.execute("SELECT uuid,elo FROM season_players WHERE season_id=?", (self.sid,)).fetchall())
        self.assertGreater(rows["a"], 1000.0)
        self.assertAlmostEqual(rows["b"], rows["c"], places=6)

    def test_weekly_points_include_competition_across_local_utc_boundary(self):
        season_start = datetime.fromisoformat(self.season[2])
        competition_start = season_start.astimezone(timezone.utc)
        competition_end = competition_start + timedelta(days=7)
        self.add_competition(start=competition_start, end=competition_end)
        submitted_at = int((competition_start + timedelta(days=1)).timestamp() * 1000)
        self.add_submission("alice", 3, submitted_at)
        self.add_submission("bob", 2, submitted_at)
        self.db.commit()
        competitive.calculate_weekly(self.db, self.sid)
        rows = dict(self.db.execute("SELECT uuid,weekly_points FROM season_players WHERE season_id=?", (self.sid,)).fetchall())
        self.assertEqual(rows["alice"], 133)
        self.assertEqual(rows["bob"], 67)
        self.assertEqual(rows["alice"] + rows["bob"], 200)

    def test_weekly_linear_pool_four_participants(self):
        start = datetime.fromisoformat(self.season[2]).astimezone(timezone.utc)
        end = start + timedelta(days=7)
        self.add_competition(start=start, end=end)
        submitted_at = int((start + timedelta(hours=1)).timestamp() * 1000)
        for uuid, stage in (("a", 64), ("b", 62), ("c", 51), ("d", 36)):
            self.add_submission(uuid, stage, submitted_at)
        self.db.commit()
        competitive.calculate_weekly(self.db, self.sid)
        rows = dict(self.db.execute("SELECT uuid,weekly_points FROM season_players WHERE season_id=?", (self.sid,)).fetchall())
        self.assertEqual(rows, {"a": 160, "b": 120, "c": 80, "d": 40})
        self.assertEqual(sum(rows.values()), 400)

    def test_weekly_linear_pool_is_idempotent_and_last_place_scores(self):
        start = datetime.fromisoformat(self.season[2]).astimezone(timezone.utc)
        end = start + timedelta(days=7)
        self.add_competition(start=start, end=end)
        submitted_at = int((start + timedelta(hours=1)).timestamp() * 1000)
        for uuid, stage in (("a", 10), ("b", 8), ("c", 6), ("d", 4), ("e", 2)):
            self.add_submission(uuid, stage, submitted_at)
        self.db.commit()
        competitive.calculate_weekly(self.db, self.sid)
        first = dict(self.db.execute("SELECT uuid,weekly_points FROM season_players WHERE season_id=?", (self.sid,)).fetchall())
        competitive.calculate_weekly(self.db, self.sid)
        second = dict(self.db.execute("SELECT uuid,weekly_points FROM season_players WHERE season_id=?", (self.sid,)).fetchall())
        self.assertEqual(first, second)
        self.assertGreater(second["e"], 0)
        self.assertEqual(sum(second.values()), 500)

    def test_weekly_ties_share_linear_rank_weights(self):
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
        self.assertEqual(rows["alice"], 400)
        self.assertEqual(rows["bob"], 200)

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

    def test_incomplete_tournament_closes_on_season_rollover(self):
        tournament = __import__("tournament")
        tid = tournament.create_tournament(self.db, self.sid, 1, "Rollover Test", None, None, None)
        self.db.execute("UPDATE tournaments SET status='active', bracket_size=4 WHERE id=?", (tid,))
        self.db.commit()
        new_season = competitive.ensure_current_season(self.db, datetime(2026, 12, 1, 12, tzinfo=timezone.utc))
        old_status = self.db.execute("SELECT status FROM tournaments WHERE id=?", (tid,)).fetchone()[0]
        self.assertEqual(old_status, "complete")
        self.assertEqual(int(new_season[1]), 2)
        self.assertEqual(self.db.execute("SELECT status FROM seasons WHERE id=?", (self.sid,)).fetchone()[0], "archived")
        self.assertEqual(self.db.execute("SELECT COUNT(*) FROM tournament_players WHERE tournament_id=? AND placement IS NOT NULL", (tid,)).fetchone()[0], 0)


if __name__ == "__main__":
    unittest.main()
