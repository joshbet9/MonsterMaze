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
            self.db, datetime(2026, 8, 1, 12, tzinfo=timezone.utc)
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
        self.assertEqual(rows["alice"], 266)
        self.assertEqual(rows["bob"], 134)
        self.assertEqual(rows["alice"] + rows["bob"], 400)

    def test_weekly_active_competition_does_not_award_points(self):
        start = datetime.fromisoformat(self.season[2]).astimezone(timezone.utc)
        active_end = datetime(2026, 11, 1, tzinfo=timezone.utc)
        self.add_competition(start=start, end=active_end)
        self.add_players(("alice", "Alice"), ("bob", "Bob"))
        submitted_at = int((start + timedelta(hours=1)).timestamp() * 1000)
        self.add_submission("alice", 64, submitted_at)
        self.add_submission("bob", 32, submitted_at)
        self.db.commit()
        competitive.calculate_weekly(self.db, self.sid)
        rows = dict(self.db.execute("SELECT uuid,weekly_points FROM season_players WHERE season_id=?", (self.sid,)).fetchall())
        self.assertEqual(rows["alice"], 0)
        self.assertEqual(rows["bob"], 0)
        self.assertEqual(sum(rows.values()), 0)

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
        self.assertLess(after, before)

    def test_get_mmr_target_prefers_weakest_configuration(self):
        self.db.execute("INSERT INTO runs VALUES('1.8','modern',0,'Jumper','a','A',10,1000)")
        self.db.execute("INSERT INTO runs VALUES('1.8','classic',1,'Repulsor','a','A',4,1000)")
        self.db.execute("INSERT INTO runs VALUES('1.8','original',0,'Slowball','b','B',20,1000)")
        self.db.commit()
        target = competitive.get_mmr_target(self.db, "a", "1.8")
        self.assertEqual(target["mode"], "classic")
        self.assertEqual(target["pattern"], 1)
        self.assertEqual(target["kit"], "Repulsor")
        self.assertEqual(target["pb"], 4)
        self.assertEqual(target["worldBest"], 4)

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
        self.add_players(("a", "A"))
        self.db.execute("INSERT INTO tournaments VALUES(?,?,?,?,?,?,?,?,?)", (self.sid, 1, "Test", 0, 0, 1, "registration", 2, None))
        self.db.commit()
        competitive.finalize_season(self.db, self.sid)
        season_status = self.db.execute("SELECT status FROM seasons WHERE id=?", (self.sid,)).fetchone()[0]
        tournament_status = self.db.execute("SELECT status FROM tournaments WHERE season_id=?", (self.sid,)).fetchone()[0]
        self.assertEqual(season_status, "archived")
        self.assertEqual(tournament_status, "complete")
