"""Authoritative Monster Maze competitive rating/season calculations."""
from __future__ import annotations

import sqlite3
import time
from datetime import datetime, timedelta, timezone
from zoneinfo import ZoneInfo
import tournament

TZ=ZoneInfo("Australia/Brisbane")
K_FACTOR=32.0
SEASON_WEEKS=13
TOURNAMENT_POINTS={1:100,2:75,3:50,4:30}
PARTICIPATION_POINTS=10


def ensure_schema(c:sqlite3.Connection)->None:
    c.execute("""CREATE TABLE IF NOT EXISTS permanent_ratings(
        uuid TEXT PRIMARY KEY,name TEXT,mmr REAL NOT NULL DEFAULT 0,updated_at INTEGER NOT NULL DEFAULT 0)""")
    c.execute("""CREATE TABLE IF NOT EXISTS seasons(
        id INTEGER PRIMARY KEY AUTOINCREMENT,season_number INTEGER NOT NULL UNIQUE,start_ts TEXT NOT NULL,end_ts TEXT NOT NULL,status TEXT NOT NULL,finalized_at TEXT)""")
    c.execute("""CREATE TABLE IF NOT EXISTS season_players(
        season_id INTEGER NOT NULL,uuid TEXT NOT NULL,name TEXT,elo REAL NOT NULL DEFAULT 1000,weekly_points INTEGER NOT NULL DEFAULT 0,tournament_points INTEGER NOT NULL DEFAULT 0,elo_component REAL NOT NULL DEFAULT 0,weekly_component REAL NOT NULL DEFAULT 0,tournament_component REAL NOT NULL DEFAULT 0,mmcl REAL NOT NULL DEFAULT 0,PRIMARY KEY(season_id,uuid))""")
    c.execute("""CREATE TABLE IF NOT EXISTS matches(
        id TEXT PRIMARY KEY,platform TEXT NOT NULL,mode TEXT NOT NULL,pattern INTEGER NOT NULL,kit TEXT NOT NULL,started_at INTEGER NOT NULL,ended_at INTEGER NOT NULL,season_id INTEGER NOT NULL,tournament_id INTEGER,processed_at INTEGER NOT NULL)""")
    c.execute("""CREATE TABLE IF NOT EXISTS match_players(
        match_id TEXT NOT NULL,uuid TEXT NOT NULL,name TEXT,placement INTEGER NOT NULL,elimination_tick INTEGER NOT NULL,result REAL NOT NULL,PRIMARY KEY(match_id,uuid))""")
    c.execute("""CREATE TABLE IF NOT EXISTS tournaments(
        id INTEGER PRIMARY KEY AUTOINCREMENT,season_id INTEGER NOT NULL,number INTEGER NOT NULL,name TEXT NOT NULL,registration_start INTEGER,registration_end INTEGER,start_ts INTEGER,status TEXT NOT NULL,bracket_size INTEGER,UNIQUE(season_id,number))""")
    c.execute("""CREATE TABLE IF NOT EXISTS tournament_players(
        tournament_id INTEGER NOT NULL,uuid TEXT NOT NULL,name TEXT,seed INTEGER,registered_at INTEGER NOT NULL,placement INTEGER,points INTEGER NOT NULL DEFAULT 0,PRIMARY KEY(tournament_id,uuid))""")
    tournament.ensure_schema(c)
    c.commit()


def _week_start(now=None):
    local=(now or datetime.now(timezone.utc)).astimezone(TZ)
    return (local-timedelta(days=local.weekday())).replace(hour=0,minute=0,second=0,microsecond=0)


def ensure_current_season(c,now=None):
    ensure_schema(c); start=_week_start(now)
    row=c.execute("SELECT id,season_number,start_ts,end_ts,status FROM seasons WHERE status='current' ORDER BY id DESC LIMIT 1").fetchone()
    if row:
        end=datetime.fromisoformat(row[3]).astimezone(TZ)
        if start<end:return row
        finalize_season(c,row[0])
    n=c.execute("SELECT COALESCE(MAX(season_number),0)+1 FROM seasons").fetchone()[0]; end=start+timedelta(weeks=SEASON_WEEKS)
    c.execute("INSERT INTO seasons(season_number,start_ts,end_ts,status) VALUES(?,?,?,'current')",(n,start.isoformat(),end.isoformat())); c.commit()
    return c.execute("SELECT id,season_number,start_ts,end_ts,status FROM seasons WHERE id=last_insert_rowid()").fetchone()


def ensure_player(c,sid,uuid,name=None):
    u=uuid.lower(); c.execute("INSERT INTO season_players(season_id,uuid,name) VALUES(?,?,?) ON CONFLICT(season_id,uuid) DO UPDATE SET name=COALESCE(excluded.name,season_players.name)",(sid,u,name))


def expected(a,b):return 1.0/(1.0+10.0**((b-a)/400.0))

def _actual(a,b):return 1.0 if a<b else 0.0 if a>b else 0.5


def _find_tournament_assignment(c,sid,players):
    if len(players)!=2:return None
    ids=sorted([players[0]["uuid"].lower(),players[1]["uuid"].lower()])
    row=c.execute("""SELECT tm.id,tm.tournament_id,tm.player1_wins,tm.player2_wins
        FROM tournament_matches tm JOIN tournaments t ON t.id=tm.tournament_id
        WHERE t.season_id=? AND t.status='bracket' AND tm.status IN ('ready','active')
          AND ((lower(tm.player1_uuid)=? AND lower(tm.player2_uuid)=?) OR (lower(tm.player1_uuid)=? AND lower(tm.player2_uuid)=?))
        ORDER BY t.number DESC,tm.round_number ASC,tm.slot ASC LIMIT 1""",(sid,ids[0],ids[1],ids[1],ids[0])).fetchone()
    if not row:return None
    return int(row[1]),int(row[0]),int(row[2])+int(row[3])+1


def record_match(c,match,players):
    ensure_schema(c)
    if c.execute("SELECT 1 FROM matches WHERE id=?",(match["id"],)).fetchone():return False
    sid=int(match.get("season_id") or ensure_current_season(c)[0])
    assignment=None
    tournament_id=match.get("tournament_id")
    tournament_match_id=match.get("tournament_match_id")
    tournament_game_number=match.get("tournament_game_number")
    if tournament_id is None and tournament_match_id is None and len(players)==2:
        assignment=_find_tournament_assignment(c,sid,players)
        if assignment:
            tournament_id,tournament_match_id,tournament_game_number=assignment
    if tournament_id is not None:
        if len(players)!=2 or sorted(int(p["placement"]) for p in players)!=[1,2]:raise ValueError("tournament matches must be completed 1v1")
        if tournament_match_id is None or tournament_game_number is None:raise ValueError("tournament game metadata is incomplete")
    c.execute("INSERT INTO matches VALUES(?,?,?,?,?,?,?,?,?,?)",(match["id"],match["platform"],match["mode"],int(match["pattern"]),match["kit"],int(match["started_at"]),int(match["ended_at"]),sid,tournament_id,int(time.time()*1000)))
    for p in players:ensure_player(c,sid,p["uuid"],p.get("name"))
    ratings={p["uuid"].lower():float(c.execute("SELECT elo FROM season_players WHERE season_id=? AND uuid=?",(sid,p["uuid"].lower())).fetchone()[0]) for p in players}
    updates={}
    for p in players:
        u=p["uuid"].lower(); actual=sum(_actual(int(p["placement"]),int(q["placement"])) for q in players if q is not p)/(len(players)-1); exp=sum(expected(ratings[u],ratings[q["uuid"].lower()]) for q in players if q is not p)/(len(players)-1)
        updates[u]=ratings[u]+K_FACTOR*(actual-exp)
        c.execute("INSERT INTO match_players VALUES(?,?,?,?,?,?)",(match["id"],u,p.get("name"),int(p["placement"]),int(p["elimination_tick"]),actual))
    for u,r in updates.items():c.execute("UPDATE season_players SET elo=? WHERE season_id=? AND uuid=?",(r,sid,u))
    if tournament_id is not None:
        winner=next(p["uuid"] for p in players if int(p["placement"])==1)
        tournament.record_game(c,int(tournament_match_id),int(tournament_game_number),match["platform"],match["mode"],int(match["pattern"]),match["kit"],str(match["id"]),winner)
    recalculate_components(c,sid); calculate_mmr(c); c.commit(); return True


def calculate_weekly(c,sid):
    row=c.execute("SELECT start_ts,end_ts FROM seasons WHERE id=?",(sid,)).fetchone()
    if not row:return
    start=int(datetime.fromisoformat(row[0]).timestamp()*1000); end=int(datetime.fromisoformat(row[1]).timestamp()*1000)
    comps=c.execute("SELECT platform,mode,pattern,kit,start_ts,end_ts FROM competitions WHERE start_ts>=? AND end_ts<=?",(row[0],row[1])).fetchall()
    players=c.execute("SELECT DISTINCT uuid FROM submissions WHERE submitted_at>=? AND submitted_at<?",(start,end)).fetchall()
    for (u,) in players:
        total=0
        for platform,mode,pattern,kit,cs,ce in comps:
            a=int(datetime.fromisoformat(cs).timestamp()*1000); b=int(datetime.fromisoformat(ce).timestamp()*1000)
            best=c.execute("SELECT MAX(stage) FROM submissions WHERE uuid=? AND platform=? AND mode=? AND pattern=? AND kit=? AND submitted_at>=? AND submitted_at<?",(u,platform,mode,pattern,kit,a,b)).fetchone()[0]
            if best is not None:total+=int(best)
        ensure_player(c,sid,u); c.execute("UPDATE season_players SET weekly_points=? WHERE season_id=? AND uuid=?",(total,sid,u))


def recalculate_components(c,sid):
    calculate_weekly(c,sid); rows=c.execute("SELECT uuid,elo,weekly_points,tournament_points FROM season_players WHERE season_id=?",(sid,)).fetchall()
    if not rows:return
    me=max(float(r[1]) for r in rows) or 1.0; mw=max(int(r[2]) for r in rows) or 1; mt=max(int(r[3]) for r in rows)
    for u,e,w,t in rows:
        ec=float(e)/me*1000.0; wc=float(w)/mw*1000.0; tc=float(t)/mt*1000.0 if mt else 0.0; mmcl=ec*.40+wc*.30+tc*.30
        c.execute("UPDATE season_players SET elo_component=?,weekly_component=?,tournament_component=?,mmcl=? WHERE season_id=? AND uuid=?",(ec,wc,tc,mmcl,sid,u))


def calculate_mmr(c):
    """Permanent all-time MMR. Every current kit leaderboard contributes: a
    player's PB is normalized against the current best stage for that exact
    platform/mode/pattern/kit leaderboard; missing leaderboards score zero.
    Because this is recalculated from runs, a new world best can lower other
    players' normalized MMR without changing their PBs."""
    ensure_schema(c)
    boards=c.execute("SELECT platform,mode,pattern,kit,MAX(stage) FROM runs GROUP BY platform,mode,pattern,kit").fetchall()
    if not boards:return
    players=c.execute("SELECT DISTINCT uuid,name FROM runs").fetchall()
    now=int(time.time()*1000); count=len(boards)
    for uuid,name in players:
        total=0.0
        for platform,mode,pattern,kit,best in boards:
            pb=c.execute("SELECT stage FROM runs WHERE platform=? AND mode=? AND pattern=? AND kit=? AND uuid=?",(platform,mode,pattern,kit,uuid)).fetchone()
            if pb and best:total+=(float(pb[0])/float(best))*1000.0
        mmr=total/count if count else 0.0
        c.execute("INSERT INTO permanent_ratings(uuid,name,mmr,updated_at) VALUES(?,?,?,?) ON CONFLICT(uuid) DO UPDATE SET name=COALESCE(excluded.name,permanent_ratings.name),mmr=excluded.mmr,updated_at=excluded.updated_at",(uuid.lower(),name,mmr,now))
    c.commit()


def award_tournament_points(c,tournament_id,placements):
    t=c.execute("SELECT season_id FROM tournaments WHERE id=?",(tournament_id,)).fetchone()
    if not t:raise ValueError("unknown tournament")
    sid=int(t[0])
    for uuid,place in placements.items():
        pts=TOURNAMENT_POINTS.get(int(place),PARTICIPATION_POINTS); u=uuid.lower()
        ensure_player(c,sid,u); c.execute("UPDATE tournament_players SET placement=?,points=? WHERE tournament_id=? AND uuid=?",(int(place),pts,tournament_id,u)); c.execute("UPDATE season_players SET tournament_points=tournament_points+? WHERE season_id=? AND uuid=?",(pts,sid,u))
    recalculate_components(c,sid); c.commit()


def finalize_season(c,sid):
    recalculate_components(c,sid); c.execute("UPDATE seasons SET status='archived',finalized_at=? WHERE id=?",(datetime.now(timezone.utc).isoformat(),sid)); c.commit()
