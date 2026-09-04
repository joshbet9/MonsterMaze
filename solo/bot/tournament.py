"""Authoritative seasonal tournament/bracket management for Monster Maze."""
from __future__ import annotations
import random
import time
import sqlite3


def ensure_schema(c: sqlite3.Connection) -> None:
    c.execute("""CREATE TABLE IF NOT EXISTS tournament_matches(
        id INTEGER PRIMARY KEY AUTOINCREMENT,tournament_id INTEGER NOT NULL,round_number INTEGER NOT NULL,slot INTEGER NOT NULL,
        player1_uuid TEXT,player2_uuid TEXT,best_of INTEGER NOT NULL DEFAULT 3,player1_wins INTEGER NOT NULL DEFAULT 0,player2_wins INTEGER NOT NULL DEFAULT 0,
        winner_uuid TEXT,loser_uuid TEXT,status TEXT NOT NULL DEFAULT 'pending',next_match_id INTEGER,next_slot INTEGER,created_at INTEGER NOT NULL,completed_at INTEGER,
        UNIQUE(tournament_id,round_number,slot))""")
    c.execute("""CREATE TABLE IF NOT EXISTS tournament_games(
        id INTEGER PRIMARY KEY AUTOINCREMENT,tournament_match_id INTEGER NOT NULL,game_number INTEGER NOT NULL,platform TEXT NOT NULL,mode TEXT NOT NULL,
        pattern INTEGER NOT NULL,kit TEXT NOT NULL,match_id TEXT NOT NULL UNIQUE,winner_uuid TEXT NOT NULL,created_at INTEGER NOT NULL,
        UNIQUE(tournament_match_id,game_number))""")
    c.commit()


def next_power_of_two(n: int) -> int:
    x=1
    while x<n:x*=2
    return x


def create_tournament(c,season_id,number,name,registration_start,registration_end,start_ts):
    ensure_schema(c);cur=c.execute("INSERT INTO tournaments(season_id,number,name,registration_start,registration_end,start_ts,status) VALUES(?,?,?,?,?,?, 'registration')",(season_id,number,name,registration_start,registration_end,start_ts));c.commit();return int(cur.lastrowid)


def register(c,tournament_id,uuid,name):
    ensure_schema(c);t=c.execute("SELECT season_id,status,registration_end FROM tournaments WHERE id=?",(tournament_id,)).fetchone()
    if not t:raise ValueError("unknown tournament")
    if t[1]!="registration":raise ValueError("registration is closed")
    now=int(time.time()*1000)
    if t[2] is not None and now>int(t[2]):raise ValueError("registration is closed")
    u=uuid.lower();c.execute("INSERT OR IGNORE INTO season_players(season_id,uuid,name) VALUES(?,?,?)",(int(t[0]),u,name));c.execute("INSERT OR IGNORE INTO tournament_players(tournament_id,uuid,name,registered_at) VALUES(?,?,?,?)",(tournament_id,u,name,now));c.commit()


def _activate_if_ready(c,match_id):
    row=c.execute("SELECT player1_uuid,player2_uuid,status FROM tournament_matches WHERE id=?",(match_id,)).fetchone()
    if row and row[2]=='pending' and row[0] and row[1]:c.execute("UPDATE tournament_matches SET status='ready' WHERE id=?",(match_id,))


def _advance(c,tournament_match_id,winner_uuid):
    row=c.execute("SELECT next_match_id,next_slot FROM tournament_matches WHERE id=?",(tournament_match_id,)).fetchone()
    if not row or row[0] is None:return
    col="player1_uuid" if int(row[1])==1 else "player2_uuid";c.execute("UPDATE tournament_matches SET %s=? WHERE id=?"%col,(winner_uuid.lower(),int(row[0])));_activate_if_ready(c,int(row[0]))


def build_bracket(c,tournament_id):
    ensure_schema(c);t=c.execute("SELECT status FROM tournaments WHERE id=?",(tournament_id,)).fetchone()
    if not t:raise ValueError("unknown tournament")
    if t[0] not in ('registration','bracket'):raise ValueError("tournament cannot be bracketed in its current state")
    if c.execute("SELECT COUNT(*) FROM tournament_matches WHERE tournament_id=?",(tournament_id,)).fetchone()[0]:return c.execute("SELECT bracket_size FROM tournaments WHERE id=?",(tournament_id,)).fetchone()[0]
    players=c.execute("SELECT uuid,name FROM tournament_players WHERE tournament_id=? ORDER BY registered_at ASC,uuid ASC",(tournament_id,)).fetchall()
    if len(players)<2:raise ValueError("at least two registered players are required")
    size=next_power_of_two(len(players));rounds=size.bit_length()-1;now=int(time.time()*1000);seeded=list(players);random.Random(int(tournament_id)).shuffle(seeded);ids={}
    for rnd in range(1,rounds+1):
        for slot in range(1,size//(2**rnd)+1):
            cur=c.execute("INSERT INTO tournament_matches(tournament_id,round_number,slot,best_of,status,created_at) VALUES(?,?,?,?, 'pending',?)",(tournament_id,rnd,slot,3,now));ids[(rnd,slot)]=int(cur.lastrowid)
    for rnd in range(1,rounds):
        for slot in range(1,size//(2**rnd)+1):
            nxt=ids[(rnd+1,(slot+1)//2)];target=1 if slot%2 else 2;c.execute("UPDATE tournament_matches SET next_match_id=?,next_slot=? WHERE id=?",(nxt,target,ids[(rnd,slot)]))
    first=size//2;byes=size-len(players);index=0
    for slot in range(1,first+1):
        if slot<=byes:
            p1=seeded[index][0];index+=1;p2=None
        else:
            p1=seeded[index][0] if index<len(seeded) else None;index+=1 if p1 else 0;p2=seeded[index][0] if index<len(seeded) else None;index+=1 if p2 else 0
        c.execute("UPDATE tournament_matches SET player1_uuid=?,player2_uuid=? WHERE id=?",(p1,p2,ids[(1,slot)]))
    for slot in range(1,first+1):
        mid=ids[(1,slot)];p1,p2=c.execute("SELECT player1_uuid,player2_uuid FROM tournament_matches WHERE id=?",(mid,)).fetchone()
        if p1 and p2:c.execute("UPDATE tournament_matches SET status='ready' WHERE id=?",(mid,))
        elif p1 or p2:
            winner=p1 or p2;c.execute("UPDATE tournament_matches SET status='bye',winner_uuid=?,completed_at=? WHERE id=?",(winner,now,mid));_advance(c,mid,winner)
    if len(players)>=4:
        cur=c.execute("INSERT INTO tournament_matches(tournament_id,round_number,slot,best_of,status,created_at) VALUES(?,?,?,?, 'pending',?)",(tournament_id,rounds+1,1,3,now));third=int(cur.lastrowid)
        c.execute("UPDATE tournament_matches SET next_match_id=?,next_slot=? WHERE id=?",(third,1,ids[(rounds-1,1)]));c.execute("UPDATE tournament_matches SET next_match_id=?,next_slot=? WHERE id=?",(third,2,ids[(rounds-1,2)]))
    c.execute("UPDATE tournaments SET status='bracket',bracket_size=? WHERE id=?",(size,tournament_id));c.commit();return size


def current_match(c,tournament_id,uuid):
    ensure_schema(c);u=uuid.lower();row=c.execute("SELECT id,round_number,slot,player1_uuid,player2_uuid,best_of,player1_wins,player2_wins,status FROM tournament_matches WHERE tournament_id=? AND status IN ('ready','active') AND (player1_uuid=? OR player2_uuid=?) ORDER BY round_number ASC,slot ASC LIMIT 1",(tournament_id,u,u)).fetchone()
    if not row:return None
    return {"id":int(row[0]),"round":int(row[1]),"slot":int(row[2]),"player1":row[3],"player2":row[4],"bestOf":int(row[5]),"player1Wins":int(row[6]),"player2Wins":int(row[7]),"status":row[8]}


def record_game(c,tournament_match_id,game_number,platform,mode,pattern,kit,match_id,winner_uuid):
    ensure_schema(c);row=c.execute("SELECT tournament_id,player1_uuid,player2_uuid,status,player1_wins,player2_wins,round_number FROM tournament_matches WHERE id=?",(tournament_match_id,)).fetchone()
    if not row:raise ValueError("unknown tournament match")
    if row[3] not in ('ready','active'):raise ValueError("tournament match is not playable")
    winner=winner_uuid.lower()
    if winner not in (row[1],row[2]):raise ValueError("winner is not a player in this match")
    c.execute("INSERT INTO tournament_games(tournament_match_id,game_number,platform,mode,pattern,kit,match_id,winner_uuid,created_at) VALUES(?,?,?,?,?,?,?,?,?)",(tournament_match_id,game_number,platform,mode,pattern,kit,match_id,winner,int(time.time()*1000)))
    p1w,p2w=int(row[4]),int(row[5]);p1w+=winner==row[1];p2w+=winner==row[2];c.execute("UPDATE tournament_matches SET status='active',player1_wins=?,player2_wins=? WHERE id=?",(p1w,p2w,tournament_match_id))
    if p1w>=2 or p2w>=2:
        champ=row[1] if p1w>=2 else row[2];loser=row[2] if champ==row[1] else row[1];now=int(time.time()*1000);c.execute("UPDATE tournament_matches SET status='complete',winner_uuid=?,loser_uuid=?,completed_at=? WHERE id=?",(champ,loser,now,tournament_match_id));_advance(c,tournament_match_id,champ)
        bracket_size=c.execute("SELECT bracket_size FROM tournaments WHERE id=?",(row[0],)).fetchone()[0];final_round=int(bracket_size).bit_length()-1
        player_count=c.execute("SELECT COUNT(*) FROM tournament_players WHERE tournament_id=?",(row[0],)).fetchone()[0]
        if player_count>=4 and int(row[6])==final_round-1 and final_round>=2:
            third=c.execute("SELECT id FROM tournament_matches WHERE tournament_id=? AND round_number=?",(row[0],final_round+1)).fetchone()
            if third:
                c.execute("UPDATE tournament_matches SET player1_uuid=? WHERE id=? AND player1_uuid IS NULL AND player2_uuid IS NULL",(loser,third[0]));c.execute("UPDATE tournament_matches SET player2_uuid=? WHERE id=? AND player1_uuid IS NOT NULL AND player2_uuid IS NULL",(loser,third[0]));_activate_if_ready(c,third[0])
        c.execute("UPDATE tournament_players SET placement=0 WHERE tournament_id=? AND uuid=? AND placement IS NULL",(row[0],loser))
    c.commit()


def finalize(c,tournament_id):
    ensure_schema(c);t=c.execute("SELECT season_id,status,bracket_size FROM tournaments WHERE id=?",(tournament_id,)).fetchone()
    if not t:raise ValueError("unknown tournament")
    if t[1]=='complete':return
    player_count=c.execute("SELECT COUNT(*) FROM tournament_players WHERE tournament_id=?",(tournament_id,)).fetchone()[0]
    rounds=int(t[2]).bit_length()-1;final=c.execute("SELECT winner_uuid,loser_uuid,status FROM tournament_matches WHERE tournament_id=? AND round_number=?",(tournament_id,rounds)).fetchone()
    if not final or final[2]!='complete':raise ValueError("final is not complete")
    third=None
    if player_count>=4:
        third=c.execute("SELECT winner_uuid,loser_uuid,status FROM tournament_matches WHERE tournament_id=? AND round_number=?",(tournament_id,rounds+1)).fetchone()
        if not third or third[2]!='complete':raise ValueError("third-place playoff is not complete")
    champion,runner=final[0],final[1];c.execute("UPDATE tournament_players SET placement=1 WHERE tournament_id=? AND uuid=?",(tournament_id,champion));c.execute("UPDATE tournament_players SET placement=2 WHERE tournament_id=? AND uuid=?",(tournament_id,runner))
    if third:
        c.execute("UPDATE tournament_players SET placement=3 WHERE tournament_id=? AND uuid=?",(tournament_id,third[0]));c.execute("UPDATE tournament_players SET placement=4 WHERE tournament_id=? AND uuid=?",(tournament_id,third[1]))
    elif player_count==3:
        c.execute("UPDATE tournament_players SET placement=3 WHERE tournament_id=? AND placement=0",(tournament_id,))
    sid=int(t[0]);rows=c.execute("SELECT uuid,placement FROM tournament_players WHERE tournament_id=?",(tournament_id,)).fetchall()
    for u,place in rows:
        pts={1:100,2:75,3:50,4:30}.get(int(place or 0),10);c.execute("UPDATE tournament_players SET points=? WHERE tournament_id=? AND uuid=?",(pts,tournament_id,u));c.execute("INSERT OR IGNORE INTO season_players(season_id,uuid,name) SELECT ?,uuid,name FROM tournament_players WHERE tournament_id=? AND uuid=?",(sid,tournament_id,u));c.execute("UPDATE season_players SET tournament_points=tournament_points+? WHERE season_id=? AND uuid=?",(pts,sid,u.lower()))
    c.execute("UPDATE tournaments SET status='complete' WHERE id=?",(tournament_id,));c.commit()
