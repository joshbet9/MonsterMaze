"""Authoritative seasonal tournament/bracket management for Monster Maze."""
from __future__ import annotations
import random
import time
import sqlite3


def ensure_schema(c: sqlite3.Connection) -> None:
    c.execute("""CREATE TABLE IF NOT EXISTS tournament_matches(
        id INTEGER PRIMARY KEY AUTOINCREMENT,
        tournament_id INTEGER NOT NULL,
        round_number INTEGER NOT NULL,
        slot INTEGER NOT NULL,
        player1_uuid TEXT, player2_uuid TEXT,
        best_of INTEGER NOT NULL DEFAULT 3,
        player1_wins INTEGER NOT NULL DEFAULT 0,
        player2_wins INTEGER NOT NULL DEFAULT 0,
        winner_uuid TEXT, loser_uuid TEXT,
        status TEXT NOT NULL DEFAULT 'pending',
        next_match_id INTEGER, next_slot INTEGER,
        created_at INTEGER NOT NULL,
        completed_at INTEGER,
        UNIQUE(tournament_id,round_number,slot))""")
    c.execute("""CREATE TABLE IF NOT EXISTS tournament_games(
        id INTEGER PRIMARY KEY AUTOINCREMENT,
        tournament_match_id INTEGER NOT NULL,
        game_number INTEGER NOT NULL,
        platform TEXT NOT NULL,
        mode TEXT NOT NULL,
        pattern INTEGER NOT NULL,
        kit TEXT NOT NULL,
        match_id TEXT NOT NULL UNIQUE,
        winner_uuid TEXT NOT NULL,
        created_at INTEGER NOT NULL,
        UNIQUE(tournament_match_id,game_number))""")
    c.commit()


def next_power_of_two(n: int) -> int:
    x = 1
    while x < n:
        x *= 2
    return x


def create_tournament(c, season_id, number, name, registration_start, registration_end, start_ts):
    ensure_schema(c)
    cur = c.execute("INSERT INTO tournaments(season_id,number,name,registration_start,registration_end,start_ts,status) VALUES(?,?,?,?,?,?, 'registration')",
                    (season_id, number, name, registration_start, registration_end, start_ts))
    c.commit()
    return int(cur.lastrowid)


def register(c, tournament_id, uuid, name):
    ensure_schema(c)
    t = c.execute("SELECT status,registration_end FROM tournaments WHERE id=?", (tournament_id,)).fetchone()
    if not t:
        raise ValueError("unknown tournament")
    if t[0] != "registration":
        raise ValueError("registration is closed")
    now = int(time.time() * 1000)
    if t[1] is not None and now > int(t[1]):
        raise ValueError("registration is closed")
    c.execute("INSERT OR IGNORE INTO tournament_players(tournament_id,uuid,name,registered_at) VALUES(?,?,?,?)",
              (tournament_id, uuid.lower(), name, now))
    c.commit()


def _advance(c, tournament_match_id, winner_uuid):
    row = c.execute("SELECT next_match_id,next_slot FROM tournament_matches WHERE id=?", (tournament_match_id,)).fetchone()
    if not row or row[0] is None:
        return
    next_id, slot = int(row[0]), int(row[1])
    col = "player1_uuid" if slot == 1 else "player2_uuid"
    c.execute("UPDATE tournament_matches SET %s=? WHERE id=?" % col, (winner_uuid.lower(), next_id))
    _activate_if_ready(c, next_id)


def _activate_if_ready(c, match_id):
    row = c.execute("SELECT player1_uuid,player2_uuid,status FROM tournament_matches WHERE id=?", (match_id,)).fetchone()
    if not row or row[2] != "pending":
        return
    p1, p2 = row[0], row[1]
    if p1 and p2:
        c.execute("UPDATE tournament_matches SET status='ready' WHERE id=?", (match_id,))


def build_bracket(c, tournament_id):
    ensure_schema(c)
    t = c.execute("SELECT status FROM tournaments WHERE id=?", (tournament_id,)).fetchone()
    if not t:
        raise ValueError("unknown tournament")
    if t[0] not in ("registration", "bracket"):
        raise ValueError("tournament cannot be bracketed in its current state")
    existing = c.execute("SELECT COUNT(*) FROM tournament_matches WHERE tournament_id=?", (tournament_id,)).fetchone()[0]
    if existing:
        return existing
    players = c.execute("SELECT uuid,name FROM tournament_players WHERE tournament_id=? ORDER BY registered_at ASC,uuid ASC", (tournament_id,)).fetchall()
    if len(players) < 2:
        raise ValueError("at least two registered players are required")
    size = next_power_of_two(len(players))
    seeded = list(players)
    random.Random(int(tournament_id)).shuffle(seeded)
    now = int(time.time() * 1000)
    rounds = size.bit_length() - 1
    ids = {}
    for rnd in range(1, rounds + 1):
        count = size // (2 ** rnd)
        for slot in range(1, count + 1):
            cur = c.execute("INSERT INTO tournament_matches(tournament_id,round_number,slot,best_of,status,created_at) VALUES(?,?,?,?, 'pending',?)",
                            (tournament_id, rnd, slot, 3, now))
            ids[(rnd, slot)] = int(cur.lastrowid)
    for rnd in range(1, rounds):
        count = size // (2 ** rnd)
        for slot in range(1, count + 1):
            nxt = ids[(rnd + 1, (slot + 1) // 2)]
            target_slot = 1 if slot % 2 == 1 else 2
            c.execute("UPDATE tournament_matches SET next_match_id=?,next_slot=? WHERE id=?", (nxt, target_slot, ids[(rnd, slot)]))
    first_count = size // 2
    for slot in range(1, first_count + 1):
        i = (slot - 1) * 2
        p1 = seeded[i][0] if i < len(seeded) else None
        p2 = seeded[i + 1][0] if i + 1 < len(seeded) else None
        c.execute("UPDATE tournament_matches SET player1_uuid=?,player2_uuid=? WHERE id=?", (p1, p2, ids[(1, slot)]))
    # A bye immediately advances the registered player into round two.
    for slot in range(1, first_count + 1):
        mid = ids[(1, slot)]
        row = c.execute("SELECT player1_uuid,player2_uuid FROM tournament_matches WHERE id=?", (mid,)).fetchone()
        if row[0] and row[1]:
            c.execute("UPDATE tournament_matches SET status='ready' WHERE id=?", (mid,))
        elif row[0] or row[1]:
            winner = row[0] or row[1]
            c.execute("UPDATE tournament_matches SET status='bye',winner_uuid=?,completed_at=? WHERE id=?", (winner, now, mid))
            _advance(c, mid, winner)
    # Third-place playoff: the two semifinal losers meet for 3rd/4th.
    if rounds >= 2:
        semi_count = 2
        cur = c.execute("INSERT INTO tournament_matches(tournament_id,round_number,slot,best_of,status,created_at) VALUES(?,?,?,?, 'pending',?)",
                        (tournament_id, rounds + 1, 1, 3, now))
        third_id = int(cur.lastrowid)
        c.execute("UPDATE tournament_matches SET next_match_id=?,next_slot=? WHERE id=?", (third_id, 1, ids[(rounds - 1, 1)]))
        c.execute("UPDATE tournament_matches SET next_match_id=?,next_slot=? WHERE id=?", (third_id, 2, ids[(rounds - 1, 2)]))
    c.execute("UPDATE tournaments SET status='bracket',bracket_size=? WHERE id=?", (size, tournament_id))
    c.commit()
    return size


def current_match(c, tournament_id, uuid):
    ensure_schema(c)
    u = uuid.lower()
    row = c.execute("""SELECT id,round_number,slot,player1_uuid,player2_uuid,best_of,player1_wins,player2_wins,status
                      FROM tournament_matches WHERE tournament_id=? AND status IN ('ready','pending')
                      AND (player1_uuid=? OR player2_uuid=?) ORDER BY round_number ASC,slot ASC LIMIT 1""", (tournament_id, u, u)).fetchone()
    if not row:
        return None
    if row[8] == 'pending':
        return None
    return {"id":int(row[0]),"round":int(row[1]),"slot":int(row[2]),"player1":row[3],"player2":row[4],"bestOf":int(row[5]),"player1Wins":int(row[6]),"player2Wins":int(row[7]),"status":row[8]}


def record_game(c, tournament_match_id, game_number, platform, mode, pattern, kit, match_id, winner_uuid):
    ensure_schema(c)
    row = c.execute("SELECT tournament_id,player1_uuid,player2_uuid,status,player1_wins,player2_wins FROM tournament_matches WHERE id=?", (tournament_match_id,)).fetchone()
    if not row:
        raise ValueError("unknown tournament match")
    if row[3] not in ('ready', 'active'):
        raise ValueError("tournament match is not playable")
    winner = winner_uuid.lower()
    if winner not in (row[1], row[2]):
        raise ValueError("winner is not a player in this match")
    c.execute("INSERT INTO tournament_games(tournament_match_id,game_number,platform,mode,pattern,kit,match_id,winner_uuid,created_at) VALUES(?,?,?,?,?,?,?,?,?)",
              (tournament_match_id,game_number,platform,mode,pattern,kit,match_id,winner,int(time.time()*1000)))
    p1w, p2w = int(row[4]), int(row[5])
    if winner == row[1]: p1w += 1
    else: p2w += 1
    c.execute("UPDATE tournament_matches SET status='active',player1_wins=?,player2_wins=? WHERE id=?", (p1w,p2w,tournament_match_id))
    if p1w >= 2 or p2w >= 2:
        champ = row[1] if p1w >= 2 else row[2]
        loser = row[2] if champ == row[1] else row[1]
        c.execute("UPDATE tournament_matches SET status='complete',winner_uuid=?,loser_uuid=?,completed_at=? WHERE id=?", (champ,loser,int(time.time()*1000),tournament_match_id))
        _advance(c,tournament_match_id,champ)
        c.execute("UPDATE tournament_players SET placement=COALESCE(placement,0) WHERE tournament_id=? AND uuid=?", (row[0],loser))
    c.commit()


def finalize(c, tournament_id):
    ensure_schema(c)
    t = c.execute("SELECT season_id,status FROM tournaments WHERE id=?", (tournament_id,)).fetchone()
    if not t:
        raise ValueError("unknown tournament")
    if t[1] == 'complete':
        return
    final = c.execute("SELECT id,winner_uuid,loser_uuid,status FROM tournament_matches WHERE tournament_id=? AND round_number=(SELECT MAX(round_number) FROM tournament_matches WHERE tournament_id=? AND round_number <= (SELECT CAST(LOG(bracket_size,2) AS INTEGER) FROM tournaments WHERE id=?))", (tournament_id,tournament_id,tournament_id)).fetchone()
    if not final or final[3] != 'complete':
        raise ValueError("final is not complete")
    champion, runner = final[1], final[2]
    third = c.execute("SELECT winner_uuid,loser_uuid,status FROM tournament_matches WHERE tournament_id=? ORDER BY round_number DESC LIMIT 1", (tournament_id,)).fetchone()
    third_winner = third[0] if third and third[2] == 'complete' else None
    third_loser = third[1] if third and third[2] == 'complete' else None
    c.execute("UPDATE tournament_players SET placement=1 WHERE tournament_id=? AND uuid=?", (tournament_id,champion))
    c.execute("UPDATE tournament_players SET placement=2 WHERE tournament_id=? AND uuid=?", (tournament_id,runner))
    if third_winner: c.execute("UPDATE tournament_players SET placement=3 WHERE tournament_id=? AND uuid=?", (tournament_id,third_winner))
    if third_loser: c.execute("UPDATE tournament_players SET placement=4 WHERE tournament_id=? AND uuid=?", (tournament_id,third_loser))
    sid=int(t[0])
    for u,place in c.execute("SELECT uuid,placement FROM tournament_players WHERE tournament_id=? AND placement IS NOT NULL", (tournament_id,)).fetchall():
        pts={1:100,2:75,3:50,4:30}.get(int(place),10)
        c.execute("UPDATE tournament_players SET points=? WHERE tournament_id=? AND uuid=?", (pts,tournament_id,u))
        c.execute("UPDATE season_players SET tournament_points=tournament_points+? WHERE season_id=? AND uuid=?", (pts,sid,u.lower()))
    c.execute("UPDATE tournaments SET status='complete' WHERE id=?", (tournament_id,))
    c.commit()
