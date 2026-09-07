from datetime import datetime, timedelta, timezone
import sqlite3
import unittest

import competitive


class CompetitiveRatingTests(unittest.TestCase):
    def setUp(self):
        self.db = sqlite3.connect(":memory:")
        self.db.executescript(
            """
            CREATE TABLE seasons(
                id INTEGER PRIMARY KEY,
                name TEXT,
                start_ts TEXT NOT NULL,
                end_ts TEXT NOT NULL
            );
            CREATE TABLE competitions(
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                platform TEXT NOT NULL,
                mode TEXT NOT NULL,
                pattern INTEGER NOT NULL,
                kit TEXT NOT NULL,
                start_ts TEXT NOT NULL,
                end_ts TEXT NOT NULL
            );
            CREATE TABLE submissions(
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                uuid TEXT NOT NULL,
                name TEXT,
                platform TEXT NOT NULL,
                mode TEXT NOT NULL,
                pattern INTEGER NOT NULL,
                kit TEXT NOT NULL,
                stage INTEGER NOT NULL,
                submitted_at INTEGER NOT NULL
            );
            CREATE TABLE season_players(
                season_id INTEGER NOT NULL,
                uuid TEXT NOT NULL,
                name TEXT,
                elo REAL NOT NULL DEFAULT 1000,
                weekly_points INTEGER NOT NULL DEFAULT 0,
                tournament_points INTEGER NOT NULL DEFAULT 0,
                elo_component REAL NOT NULL DEFAULT 0,
                weekly_component REAL NOT NULL DEFAULT 0,
                tournament_component REAL NOT NULL DEFAULT 0,
                mmcl REAL NOT NULL DEFAULT 0,
                PRIMARY KEY(season_id, uuid)
            );
            CREATE TABLE permanent_ratings(
                uuid TEXT PRIMARY KEY,
                name TEXT,
                mmr REAL NOT NULL DEFAULT 0
            );
            """
        )
        self.season = (
            1,
            "S1",
            "2026-09-01T00:00:00+10:00",
            "2026-12-01T00:00:00+10:00",
        )
        self.db.execute(
            "INSERT INTO seasons(id,name,start_ts,end_ts) VALUES(?,?,?,?)",
            self.season,
        )
        self.sid = 1

    def tearDown(self):
        self.db.close()

    def add_competition(self, platform, kit, start, end, mode="solo", pattern=2):
        self.db.execute(
            "INSERT INTO competitions(platform,mode,pattern,kit,start_ts,end_ts) VALUES(?,?,?,?,?,?)",
            (platform, mode, pattern, kit, start.isoformat(), end.isoformat()),
        )

    def add_submission(
        self,
        uuid,
        stage,
        submitted_at,
        platform="1.8",
        mode="solo",
        pattern=2,
        kit="Slowballer",
        name=None,
    ):
        self.db.execute(
            "INSERT INTO submissions(uuid,name,platform,mode,pattern,kit,stage,submitted_at) VALUES(?,?,?,?,?,?,?,?)",
            (uuid, name or uuid, platform, mode, pattern, kit, stage, submitted_at),
        )

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

    def test_weekly_points_reset_on_recalculation(self):
        season_start = datetime.fromisoformat(self.season[2]).astimezone(timezone.utc)
        self.add_competition(platform="1.8", kit="Slowballer", start=season_start, end=season_start + timedelta(days=3))
        t = int((season_start + timedelta(hours=1)).timestamp() * 1000)
        self.add_submission("alice", 20, t)
        self.add_submission("bob", 10, t)
        self.db.commit()
        competitive.calculate_weekly(self.db, self.sid)
        competitive.calculate_weekly(self.db, self.sid)
        rows = dict(self.db.execute("SELECT uuid,weekly_points FROM season_players WHERE season_id=?", (self.sid,)).fetchall())
        self.assertEqual(rows["alice"], 133)
        self.assertEqual(rows["bob"], 67)

    def test_weekly_scoring_two_participants(self):
        season_start = datetime.fromisoformat(self.season[2]).astimezone(timezone.utc)
        self.add_competition(platform="1.8", kit="Slowballer", start=season_start, end=season_start + timedelta(days=3))
        t = int((season_start + timedelta(hours=1)).timestamp() * 1000)
        self.add_submission("alice", 20, t)
        self.add_submission("bob", 10, t)
        self.db.commit()
        competitive.calculate_weekly(self.db, self.sid)
        rows = dict(self.db.execute("SELECT uuid,weekly_points FROM season_players WHERE season_id=?", (self.sid,)).fetchall())
        self.assertEqual(rows["alice"], 133)
        self.assertEqual(rows["bob"], 67)
        self.assertEqual(rows["alice"] + rows["bob"], 200)

    def test_weekly_scoring_four_participants(self):
        season_start = datetime.fromisoformat(self.season[2]).astimezone(timezone.utc)
        self.add_competition(platform="1.8", kit="Slowballer", start=season_start, end=season_start + timedelta(days=3))
        t = int((season_start + timedelta(hours=1)).timestamp() * 1000)
        for uuid, stage in [("a", 40), ("b", 30), ("c", 20), ("d", 10)]:
            self.add_submission(uuid, stage, t)
        self.db.commit()
        competitive.calculate_weekly(self.db, self.sid)
        rows = dict(self.db.execute("SELECT uuid,weekly_points FROM season_players WHERE season_id=?", (self.sid,)).fetchall())
        self.assertEqual([rows[u] for u in ("a", "b", "c", "d")], [160, 120, 80, 40])

    def test_weekly_scoring_five_participants_preserves_pool(self):
        season_start = datetime.fromisoformat(self.season[2]).astimezone(timezone.utc)
        self.add_competition(platform="1.8", kit="Slowballer", start=season_start, end=season_start + timedelta(days=3))
        t = int((season_start + timedelta(hours=1)).timestamp() * 1000)
        for i, stage in enumerate([50, 40, 30, 20, 10]):
            self.add_submission(f"p{i}", stage, t)
        self.db.commit()
        competitive.calculate_weekly(self.db, self.sid)
        rows = dict(self.db.execute("SELECT uuid,weekly_points FROM season_players WHERE season_id=?", (self.sid,)).fetchall())
        self.assertEqual(sum(rows.values()), 500)
        self.assertEqual([rows[f"p{i}"] for i in range(5)], [167, 133, 100, 67, 33])

    def test_weekly_ties_share_average_placement_weight(self):
        season_start = datetime.fromisoformat(self.season[2]).astimezone(timezone.utc)
        self.add_competition(platform="1.8", kit="Slowballer", start=season_start, end=season_start + timedelta(days=3))
        t = int((season_start + timedelta(hours=1)).timestamp() * 1000)
        for uuid, stage in [("a", 40), ("b", 30), ("c", 30), ("d", 10)]:
            self.add_submission(uuid, stage, t)
        self.db.commit()
        competitive.calculate_weekly(self.db, self.sid)
        rows = dict(self.db.execute("SELECT uuid,weekly_points FROM season_players WHERE season_id=?", (self.sid,)).fetchall())
        self.assertEqual(rows["a"], 160)
        self.assertEqual(rows["b"], 100)
        self.assertEqual(rows["c"], 100)
        self.assertEqual(rows["d"], 40)
        self.assertEqual(sum(rows.values()), 400)

    def test_submission_outside_competition_not_counted(self):
        season_start = datetime.fromisoformat(self.season[2]).astimezone(timezone.utc)
        self.add_competition(platform="1.8", kit="Slowballer", start=season_start, end=season_start + timedelta(days=3))
        before = int((season_start - timedelta(hours=1)).timestamp() * 1000)
        inside = int((season_start + timedelta(hours=1)).timestamp() * 1000)
        self.add_submission("alice", 99, before)
        self.add_submission("bob", 20, inside)
        self.db.commit()
        competitive.calculate_weekly(self.db, self.sid)
        rows = dict(self.db.execute("SELECT uuid,weekly_points FROM season_players WHERE season_id=?", (self.sid,)).fetchall())
        self.assertEqual(rows["bob"], 100)
        self.assertNotIn("alice", rows)

    def test_submission_at_competition_end_not_counted(self):
        season_start = datetime.fromisoformat(self.season[2]).astimezone(timezone.utc)
        end = season_start + timedelta(days=3)
        self.add_competition(platform="1.8", kit="Slowballer", start=season_start, end=end)
        t = int(end.timestamp() * 1000)
        self.add_submission("alice", 20, t)
        self.db.commit()
        competitive.calculate_weekly(self.db, self.sid)
        rows = self.db.execute("SELECT uuid,weekly_points FROM season_players WHERE season_id=?", (self.sid,)).fetchall()
        self.assertEqual(rows, [])

    def test_best_attempt_is_used(self):
        season_start = datetime.fromisoformat(self.season[2]).astimezone(timezone.utc)
        self.add_competition(platform="1.8", kit="Slowballer", start=season_start, end=season_start + timedelta(days=3))
        t1 = int((season_start + timedelta(hours=1)).timestamp() * 1000)
        t2 = int((season_start + timedelta(hours=2)).timestamp() * 1000)
        self.add_submission("alice", 20, t1)
        self.add_submission("alice", 50, t2)
        self.add_submission("bob", 30, t1)
        self.db.commit()
        competitive.calculate_weekly(self.db, self.sid)
        rows = dict(self.db.execute("SELECT uuid,weekly_points FROM season_players WHERE season_id=?", (self.sid,)).fetchall())
        self.assertEqual(rows["alice"], 133)
        self.assertEqual(rows["bob"], 67)

    def test_competition_outside_season_not_counted(self):
        season_start = datetime.fromisoformat(self.season[2]).astimezone(timezone.utc)
        self.add_competition(platform="1.8", kit="Slowballer", start=season_start - timedelta(days=1), end=season_start + timedelta(days=1))
        t = int((season_start + timedelta(hours=1)).timestamp() * 1000)
        self.add_submission("alice", 20, t)
        self.db.commit()
        competitive.calculate_weekly(self.db, self.sid)
        rows = self.db.execute("SELECT uuid,weekly_points FROM season_players WHERE season_id=?", (self.sid,)).fetchall()
        self.assertEqual(rows, [])

    def test_timezone_boundary_is_local_time_safe(self):
        season_start = datetime.fromisoformat(self.season[2]).astimezone(timezone.utc)
        self.add_competition(platform="1.8", kit="Slowballer", start=season_start, end=season_start + timedelta(days=1))
        # 2026-09-01 00:30 Brisbane is still inside the competition.
        local = datetime(2026, 9, 1, 0, 30, tzinfo=competitive.TZ)
        self.add_submission("alice", 20, int(local.timestamp() * 1000))
        self.db.commit()
        competitive.calculate_weekly(self.db, self.sid)
        rows = dict(self.db.execute("SELECT uuid,weekly_points FROM season_players WHERE season_id=?", (self.sid,)).fetchall())
        self.assertEqual(rows["alice"], 100)

    def test_nonmatching_platform_or_kit_not_counted(self):
        season_start = datetime.fromisoformat(self.season[2]).astimezone(timezone.utc)
        self.add_competition(platform="1.8", kit="Slowballer", start=season_start, end=season_start + timedelta(days=3))
        t = int((season_start + timedelta(hours=1)).timestamp() * 1000)
        self.add_submission("alice", 20, t, platform="1.21", kit="Slowballer")
        self.add_submission("bob", 30, t, platform="1.8", kit="Repulsor")
        self.db.commit()
        competitive.calculate_weekly(self.db, self.sid)
        rows = self.db.execute("SELECT uuid,weekly_points FROM season_players WHERE season_id=?", (self.sid,)).fetchall()
        self.assertEqual(rows, [])

    def test_single_participant_gets_full_pool(self):
        season_start = datetime.fromisoformat(self.season[2]).astimezone(timezone.utc)
        self.add_competition(platform="1.8", kit="Slowballer", start=season_start, end=season_start + timedelta(days=3))
        t = int((season_start + timedelta(hours=1)).timestamp() * 1000)
        self.add_submission("alice", 20, t)
        self.db.commit()
        competitive.calculate_weekly(self.db, self.sid)
        row = self.db.execute("SELECT weekly_points FROM season_players WHERE season_id=? AND uuid='alice'", (self.sid,)).fetchone()
        self.assertEqual(row[0], 100)


if __name__ == "__main__":
    unittest.main()
