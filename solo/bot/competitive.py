"""Authoritative Monster Maze competitive rating/season calculations."""
from __future__ import annotations

import sqlite3
import time
from datetime import datetime, timedelta, timezone
from fractions import Fraction
from zoneinfo import ZoneInfo
import tournament

TZ=ZoneInfo("Australia/Brisbane")
K_FACTOR=32.0
SEASON_WEEKS=13
WEEKLY_POOL_BASE=100
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


def calculate_weekly(c,sid):
    row=c.execute("SELECT start_ts,end_ts FROM seasons WHERE id=?",(sid,)).fetchone()
    if not row:return
    season_start=datetime.fromisoformat(row[0])
    season_end=datetime.fromisoformat(row[1])
    now=datetime.now(timezone.utc)

    # Season timestamps are stored in the competition timezone, while
    # competitions are stored as UTC ISO-8601 timestamps. Compare parsed
    # instants so the local Monday/UTC Sunday boundary is handled correctly.
    # Only completed competitions contribute points; active/future
    # competitions must not award weekly points before their end time.
    comps=[]
    for comp in c.execute("SELECT platform,mode,pattern,kit,start_ts,end_ts FROM competitions").fetchall():
        comp_start=datetime.fromisoformat(comp[4])
        comp_end=datetime.fromisoformat(comp[5])
        if comp_start >= season_start and comp_end <= season_end and comp_end <= now:
            comps.append(comp)

    # Recalculate from scratch so this function is idempotent and cannot
    # double-award weekly points when called repeatedly.
    c.execute("UPDATE season_players SET weekly_points=0 WHERE season_id=?",(sid,))

    for platform,mode,pattern,kit,cs,ce in comps:
        a=int(datetime.fromisoformat(cs).timestamp()*1000)
        b=int(datetime.fromisoformat(ce).timestamp()*1000)
        rows=c.execute("""
            SELECT uuid,MAX(stage),MAX(name)
            FROM submissions
            WHERE platform=? AND mode=? AND pattern=? AND kit=?
              AND submitted_at>=? AND submitted_at<?
            GROUP BY uuid
            ORDER BY MAX(stage) DESC, lower(uuid) ASC
        """,(platform,mode,pattern,kit,a,b)).fetchall()
        if not rows:continue
        points=_linear_pool_points(rows)
        for u,_,name in rows:
            ensure_player(c,sid,u,name)
        for u,pts in points.items():
            c.execute("UPDATE season_players SET weekly_points=weekly_points+? WHERE season_id=? AND uuid=?",(pts,sid,u))


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


def get_mmr_target(c, uuid, platform):
    """Return the player's weakest eligible MMR configuration for a platform.

    Missing PBs count as zero. Only configurations with an existing world-best
    are eligible. Selection is based on the lowest PB/world-best percentage.
    """
    ensure_schema(c)
    platform = str(platform)
    uuid = str(uuid).lower()
    boards = c.execute("""
        SELECT platform, mode, pattern, kit, MAX(stage)
        FROM runs
        WHERE platform=?
        GROUP BY platform, mode, pattern, kit
    """, (platform,)).fetchall()
    if not boards:
        return None
    best_target = None
    for p, mode, pattern, kit, best in boards:
        pb = c.execute("SELECT stage FROM runs WHERE platform=? AND mode=? AND pattern=? AND kit=? AND uuid=?", (p, mode, pattern, kit, uuid)).fetchone()
        ratio = float(pb[0]) / float(best) if pb and best else 0.0
        target = (ratio, mode, pattern, kit, int(best), int(pb[0]) if pb else 0)
        if best_target is None or target < best_target:
            best_target = target
    if best_target is None:
        return None
    ratio, mode, pattern, kit, best, pb = best_target
    return {"platform": platform, "mode": mode, "pattern": pattern, "kit": kit, "bestStage": best, "pbStage": pb, "ratio": ratio}
