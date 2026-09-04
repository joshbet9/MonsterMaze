import sqlite3
import unittest

import competitive
import tournament


class TournamentTests(unittest.TestCase):
    def make_tournament(self, count, number=1):
        db = sqlite3.connect(":memory:")
        competitive.ensure_schema(db)
        tournament.ensure_schema(db)
        season = competitive.ensure_current_season(db)
        sid = int(season[0])
        tid = tournament.create_tournament(db, sid, number, "Test Cup", 0, 9999999999999, 0)
        for i in range(count):
            tournament.register(db, tid, "p%d" % i, "P%d" % i)
        return db, tid

    def setUp(self):
        self.db, self.tid = self.make_tournament(4)
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

    def test_record_game_rejects_skipped_game_number(self):
        mid = self.db.execute("SELECT id,player1_uuid FROM tournament_matches WHERE tournament_id=? AND round_number=1 LIMIT 1", (self.tid,)).fetchone()
        with self.assertRaises(ValueError):
            tournament.record_game(self.db, mid[0], 2, "1.8", "modern", 0, "Jumper", "skip", mid[1])

    def test_dynamic_brackets_for_two_through_eight_players(self):
        for count in range(2, 9):
            db, tid = self.make_tournament(count, number=count)
            try:
                size = tournament.build_bracket(db, tid)
                self.assertEqual(size, tournament.next_power_of_two(count))
                first = db.execute(
                    "SELECT player1_uuid,player2_uuid,status FROM tournament_matches WHERE tournament_id=? AND round_number=1 ORDER BY slot",
                    (tid,),
                ).fetchall()
                self.assertEqual(len(first), size // 2)
                registered = {"p%d" % i for i in range(count)}
                assigned = set()
                byes = 0
                for p1, p2, status in first:
                    if p1:
                        self.assertIn(p1, registered)
                        self.assertNotIn(p1, assigned)
                        assigned.add(p1)
                    if p2:
                        self.assertIn(p2, registered)
                        self.assertNotIn(p2, assigned)
                        assigned.add(p2)
                    if status == "bye":
                        byes += 1
                    else:
                        self.assertEqual(status, "ready")
                self.assertEqual(assigned, registered)
                self.assertEqual(byes, size - count)
                third_count = db.execute(
                    "SELECT COUNT(*) FROM tournament_matches WHERE tournament_id=? AND round_number=?",
                    (tid, size.bit_length()),
                ).fetchone()[0]
                self.assertEqual(third_count, 1 if count >= 4 else 0)
            finally:
                db.close()


if __name__ == "__main__":
    unittest.main()
