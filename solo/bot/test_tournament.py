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

    def play_series(self, match_id, winner, loser, tag):
        tournament.record_game(self.db, match_id, 1, "1.8", "modern", 0, "Jumper", "%s-1" % tag, winner)
        tournament.record_game(self.db, match_id, 2, "1.21", "modern", 1, "Repulsor", "%s-2" % tag, winner)

    def test_dynamic_four_player_bracket_and_third_place(self):
        rows = self.db.execute("SELECT id,round_number,slot,player1_uuid,player2_uuid FROM tournament_matches WHERE tournament_id=? AND round_number=1 ORDER BY slot", (self.tid,)).fetchall()
        for mid, rnd, slot, p1, p2 in rows:
            self.play_series(mid, p1, p2, "r1-%d" % slot)

        semis = self.db.execute("SELECT id,round_number,slot,player1_uuid,player2_uuid FROM tournament_matches WHERE tournament_id=? AND round_number=2 ORDER BY slot", (self.tid,)).fetchall()
        self.assertEqual(len(semis), 2)
        for mid, rnd, slot, p1, p2 in semis:
            self.play_series(mid, p1, p2, "semi-%d" % slot)

        third = self.db.execute("SELECT id,player1_uuid,player2_uuid,status FROM tournament_matches WHERE tournament_id=? AND round_number=3", (self.tid,)).fetchone()
        self.assertEqual(third[3], "ready")
        self.assertNotEqual(third[1], third[2])

        final = self.db.execute("SELECT id,player1_uuid,player2_uuid,status FROM tournament_matches WHERE tournament_id=? AND round_number=2 AND slot=1", (self.tid,)).fetchone()
        # The final is round 2 for a four-player bracket only if the schema has
        # two round-one matches; verify the actual final instead of assuming it.
        final = self.db.execute("SELECT id,player1_uuid,player2_uuid,status FROM tournament_matches WHERE tournament_id=? AND round_number=2 ORDER BY slot DESC LIMIT 1", (self.tid,)).fetchone()
        self.assertEqual(final[3], "complete")

        # The bracket's true final is round 2, slot 1 for four players.
        final = self.db.execute("SELECT id,player1_uuid,player2_uuid FROM tournament_matches WHERE tournament_id=? AND round_number=2 AND slot=1", (self.tid,)).fetchone()
        self.play_series(final[0], final[1], final[2], "final")
        tournament.playoff = None

        tournament.finalize(self.db, self.tid)
        placements = dict(self.db.execute("SELECT uuid,placement FROM tournament_players WHERE tournament_id=?", (self.tid,)).fetchall())
        points = dict(self.db.execute("SELECT uuid,points FROM tournament_players WHERE tournament_id=?", (self.tid,)).fetchall())
        self.assertEqual(sorted(placements.values())[:4], [1, 2, 3, 4])
        self.assertEqual(sorted(points.values()), [30, 50, 75, 100])


if __name__ == "__main__":
    unittest.main()
