import os
import sqlite3
import tempfile
import unittest
from datetime import datetime, timezone

import api_server
import competitive


class SeasonApiTests(unittest.TestCase):
    def setUp(self):
        fd, self.db_path = tempfile.mkstemp(prefix="monstermaze-season-api-", suffix=".db")
        os.close(fd)
        self.db = sqlite3.connect(self.db_path)
        competitive.ensure_schema(self.db)
        start = datetime(2026, 1, 5, tzinfo=timezone.utc)
        season = competitive.ensure_current_season(self.db, start)
        self.sid = int(season[0])
        self.db.execute(
            "UPDATE seasons SET status='archived', finalized_at=? WHERE id=?",
            (datetime(2026, 4, 6, tzinfo=timezone.utc).isoformat(), self.sid),
        )
        competitive.ensure_player(self.db, self.sid, "alice", "Alice")
        competitive.ensure_player(self.db, self.sid, "bob", "Bob")
        self.db.execute(
            "UPDATE season_players SET elo=1200,weekly_points=80,tournament_points=100,elo_component=1000,weekly_component=1000,tournament_component=1000,mmcl=1000 WHERE season_id=? AND uuid='alice'",
            (self.sid,),
        )
        self.db.execute(
            "UPDATE season_players SET elo=1100,weekly_points=40,tournament_points=30,elo_component=916.667,weekly_component=500,tournament_component=300,mmcl=622.667 WHERE season_id=? AND uuid='bob'",
            (self.sid,),
        )
        self.db.commit()
        self.old_db = api_server.DB
        api_server.DB = lambda: sqlite3.connect(self.db_path)

    def tearDown(self):
        api_server.DB = self.old_db
        self.db.close()
        try:
            os.unlink(self.db_path)
        except FileNotFoundError:
            pass

    def test_season_list_and_summary(self):
        listing = api_server.historical_get(["seasons"])
        self.assertEqual(listing["seasons"][0]["number"], 1)
        summary = api_server.historical_get(["seasons", str(self.sid)])
        self.assertEqual(summary["season"]["status"], "archived")
        self.assertEqual(summary["season"]["players"][0]["uuid"], "alice")

    def test_historical_leaderboards_do_not_recalculate(self):
        before = self.db.execute(
            "SELECT mmcl FROM season_players WHERE season_id=? AND uuid='bob'", (self.sid,)
        ).fetchone()[0]
        result = api_server.historical_get(["seasons", str(self.sid), "leaderboard", "mmcl"])
        self.assertEqual(result["rows"][0]["uuid"], "alice")
        after = self.db.execute(
            "SELECT mmcl FROM season_players WHERE season_id=? AND uuid='bob'", (self.sid,)
        ).fetchone()[0]
        self.assertEqual(before, after)

    def test_player_history_route(self):
        result = api_server.historical_get(["player", "alice", "seasons"])
        self.assertEqual(result["uuid"], "alice")
        self.assertEqual(result["seasons"][0]["season"], 1)
        self.assertEqual(result["seasons"][0]["mmcl"], 1000.0)

    def test_missing_season(self):
        result = api_server.historical_get(["seasons", "9999"])
        self.assertFalse(result["ok"])
        self.assertEqual(result["error"], "season_not_found")


if __name__ == "__main__":
    unittest.main()
