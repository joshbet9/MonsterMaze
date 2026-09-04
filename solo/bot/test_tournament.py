import sqlite3
import unittest

import competitive
import tournament


class TournamentTests(unittest.TestCase):
    def setUp(self):
        self.db = sqlite3.connect(":memory:")
        competitive.ensure_schema(self.db)
        tournament.ensure_schema(self.db)
        season = competitive.ensure_current_season(self.db)
        self.sid = int(season[0])
        self.tid = tournament.create_tournament(self.db, self.sid, 1, "Test Cup", 0, 9999999999999, 0)
        for i in range(4):
            tournament.register(self.db, self.tid, "p%d" % i, "P%d" % i)
        self.assertEqual(tournament.build_bracket(self.db, self.tid), 4)

    def tearDown(self):
        self.db.close()

    def play_series(self, match_id, winner, tag):
        tournament.record_game(self.db, match_id, 1, "1.8", "modern", 0, "Jumper", "%s-1" % tag, winner)
        tournament.record_game(self.db, match_id, 2, "1.21", "modern", 1, "Repulsor", "%s-2" % tag, winner)

    def test_dynamic_four_player_bracket_and_third_place(self):
        first_round = self.db.execute(
            "SELECT id,slot,player1_uuid,player2_uuid FROM tournament_matches WHERE tournament_id=? AND round_number=1 ORDER BY slot",
            (self.tid,),
        ).fetchall()
        self.assertEqual(len(first_round), 2)
        for mid, slot, p1, p2 in first_round:
            self.play_series(mid, p1, "r1-%d" % slot)

        final = self.db.execute(
            "SELECT id,player1_uuid,player2_uuid,status FROM tournament_matches WHERE tournament_id=? AND round_number=2",
            (self.tid,),
        ).fetchone()
        self.assertEqual(final[3], "ready")
        self.assertNotEqual(final[1], final[2])

        third = self.db.execute(
            "SELECT id,player1_uuid,player2_uuid,status FROM tournament_matches WHERE tournament_id=? AND round_number=3",
            (self.tid,),
        ).fetchone()
        self.assertEqual(third[3], "ready")
        self.assertNotEqual(third[1], third[2])

        self.play_series(final[0], final[1], "final")
        self.play_series(third[0], third[1], "third")
        tournament.finalize(self.db, self.tid)

        placements = dict(self.db.execute("SELECT uuid,placement FROM tournament_players WHERE tournament_id=?", (self.tid,)).fetchall())
        points = dict(self.db.execute("SELECT uuid,points FROM tournament_players WHERE tournament_id=?", (self.tid,)).fetchall())
        self.assertEqual(sorted(placements.values()), [1, 2, 3, 4])
        self.assertEqual(sorted(points.values()), [30, 50, 75, 100])


if __name__ == "__main__":
    unittest.main()
