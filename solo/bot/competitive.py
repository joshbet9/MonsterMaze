"""Authoritative Monster Maze competitive rating/season calculations.

This module intentionally contains no Discord code.  The Discord bot/API owns the
SQLite database and calls these functions; 1.8 and 1.21 servers only submit
immutable game results.
"""
from __future__ import annotations

import json
import sqlite3
import time
from datetime import datetime, timedelta, timezone
from zoneinfo import ZoneInfo

TZ = ZoneInfo("Australia/Brisbane")
K_FACTOR = 32.0
SEASON_WEEKS = 13
TOURNAMENT_POINTS = {1: 100, 2: 75, 3: 50, 4: 30}
PARTICIPATION_POINTS = 10


def ensure_schema(c: sqlite3.Connection) -> None:
    c.execute("""CREATE TABLE IF NOT EXISTS seasons(
        id INTEGER PRIMARY KEY AUTOINCREMENT,
        season_number INTEGER NOT NULL UNIQUE,
        start_ts TEXT NOT NULL,
        end_ts TEXT NOT NULL,
        status TEXT NOT NULL,
        finalized_at TEXT
    )""")
    c.execute("""CREATE TABLE IF NOT EXISTS season_players(
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
        PRIMARY KEY(season_id,uuid)
    )""")
    c.execute("""CREATE TABLE IF NOT EXISTS matches(
        id TEXT PRIMARY KEY,
        platform TEXT NOT NULL,
        mode TEXT NOT NULL,
        pattern INTEGER NOT NULL,
        kit TEXT NOT NULL,
        started_at INTEGER NOT NULL,
        ended_at INTEGER NOT NULL,
        season_id INTEGER NOT NULL,
        tournament_id INTEGER,
        processed_at INTEGER NOT NULL
    )""")
    c.execute("""CREATE TABLE IF NOT EXISTS match_players(
        match_id TEXT NOT NULL,
        uuid TEXT NOT NULL,
        name TEXT,
        placement INTEGER NOT NULL,
        elimination_tick INTEGER NOT NULL,
        result REAL NOT NULL,
        PRIMARY KEY(match_id,uuid)
    )""")
    c.execute("""CREATE TABLE IF NOT EXISTS tournaments(
        id INTEGER PRIMARY KEY AUTOINCREMENT,
        season_id INTEGER NOT NULL,
        number INTEGER NOT NULL,
        name TEXT NOT NULL,
        registration_start INTEGER,
        registration_end INTEGER,
        start_ts INTEGER,
        status TEXT NOT NULL,
        bracket_size INTEGER,
        UNIQUE(season_id,number)
    )""")
    c.execute("""CREATE TABLE IF NOT EXISTS tournament_players(
        tournament_id INTEGER NOT NULL,
        uuid TEXT NOT NULL,
        name TEXT,
        seed INTEGER,
        registered_at INTEGER NOT NULL,
        placement INTEGER,
        points INTEGER NOT NULL DEFAULT 0,
        PRIMARY KEY(tournament_id,uuid)
    )""")
    c.commit()


def _week_start(now: datetime | None = None) -> datetime:
    local = (now or datetime.now(timezone.utc)).astimezone(TZ)
    return (local - timedelta(days=local.weekday())).replace(hour=0, minute=0, second=0, microsecond=0)


def ensure_current_season(c: sqlite3.Connection, now: datetime | None = None):
    ensure_schema(c)
    start = _week_start(now)
    row = c.execute("SELECT id,season_number,start_ts,end_ts,status FROM seasons WHERE status='current' ORDER BY id DESC LIMIT 1").fetchone()
    if row:
        end = datetime.fromisoformat(row[3]).astimezone(TZ)
        if start < end:
            return row
        finalize_season(c, row[0])
    n = c.execute("SELECT COALESCE(MAX(season_number),0)+1 FROM seasons").fetchone()[0]
    end = start + timedelta(weeks=SEASON_WEEKS)
    c.execute("INSERT INTO seasons(season_number,start_ts,end_ts,status) VALUES(?,?,?,'current')",(n,start.isoformat(),end.isoformat()))
    c.commit()
    return c.execute("SELECT id,season_number,start_ts,end_ts,status FROM seasons WHERE id=last_insert_rowid()").fetchone()


def season_id(c: sqlite3.Connection) -> int:
    return int(ensure_current_season(c)[0])


def ensure_player(c: sqlite3.Connection, sid: int, uuid: str, name: str | None = None):
    c.execute("INSERT INTO season_players(season_id,uuid,name) VALUES(?,?,?) ON CONFLICT(season_id,uuid) DO UPDATE SET name=COALESCE(excluded.name,season_players.name)",(sid,uuid.lower(),name))


def expected(a: float, b: float) -> float:
    return 1.0 / (1.0 + 10.0 ** ((b - a) / 400.0))


def _actual(place_a: int, place_b: int) -> float:
    if place_a < place_b: return 1.0
    if place_a > place_b: return 0.0
    return 0.5


def record_match(c: sqlite3.Connection, match: dict, players: list[dict]) -> bool:
    """Persist one completed game and apply one fixed-K multiplayer ELO update.

    Placement must already encode the server's tick-based tie semantics.  The
    function is idempotent by match id and performs all rating changes from the
    pre-match ratings in one transaction.
    """
    ensure_schema(c)
    if c.execute("SELECT 1 FROM matches WHERE id=?",(match["id"],)).fetchone(): return False
    sid = int(match.get("season_id") or ensure_current_season(c)[0])
    c.execute("INSERT INTO matches VALUES(?,?,?,?,?,?,?,?,?,?)",(
        match["id"],match["platform"],match["mode"],int(match["pattern"]),match["kit"],
        int(match["started_at"]),int(match["ended_at"]),sid,match.get("tournament_id"),int(time.time()*1000)))
    for p in players:
        ensure_player(c,sid,p["uuid"],p.get("name"))
    ratings={p["uuid"].lower():float(c.execute("SELECT elo FROM season_players WHERE season_id=? AND uuid=?",(sid,p["uuid"].lower())).fetchone()[0]) for p in players}
    updates={}
    for p in players:
        u=p["uuid"].lower(); actual=sum(_actual(int(p["placement"]),int(q["placement"])) for q in players if q is not p)/(len(players)-1)
        exp=sum(expected(ratings[u],ratings[q["uuid"].lower()]) for q in players if q is not p)/(len(players)-1)
        updates[u]=ratings[u]+K_FACTOR*(actual-exp)
        c.execute("INSERT INTO match_players VALUES(?,?,?,?,?,?)",(match["id"],u,p.get("name"),int(p["placement"]),int(p["elimination_tick"]),actual))
    for u,r in updates.items(): c.execute("UPDATE season_players SET elo=? WHERE season_id=? AND uuid=?",(r,sid,u))
    if match.get("tournament_id") is not None:
        _sync_tournament_game(c,int(match["tournament_id"]),players)
    recalculate_components(c,sid)
    c.commit()
    return True


def _sync_tournament_game(c, tournament_id: int, players: list[dict]):
    # Tournament bracket management owns final placement. This hook records the
    # participants and leaves points untouched until an explicit final placement
    # is submitted, preventing intermediate BO3 games from awarding points.
    now=int(time.time()*1000)
    for p in players:
        c.execute("INSERT OR IGNORE INTO tournament_players(tournament_id,uuid,name,registered_at) VALUES(?,?,?,?)",(tournament_id,p["uuid"].lower(),p.get("name"),now))


def calculate_weekly(c: sqlite3.Connection, sid: int) -> None:
    """Weekly raw score = sum of each player's best stage in every one of the
    26 challenge instances belonging to the 13-week season. Time is ignored."""
    row=c.execute("SELECT start_ts,end_ts FROM seasons WHERE id=?",(sid,)).fetchone()
    if not row:return
    start=int(datetime.fromisoformat(row[0]).timestamp()*1000); end=int(datetime.fromisoformat(row[1]).timestamp()*1000)
    players=c.execute("SELECT DISTINCT uuid FROM submissions WHERE submitted_at>=? AND submitted_at<?",(start,end)).fetchall()
    comps=c.execute("SELECT platform,mode,pattern,kit,start_ts,end_ts FROM competitions WHERE start_ts>=? AND end_ts<=?",(row[0],row[1])).fetchall()
    for (u,) in players:
        total=0
        for platform,mode,pattern,kit,cs,ce in comps:
            a=int(datetime.fromisoformat(cs).timestamp()*1000); b=int(datetime.fromisoformat(ce).timestamp()*1000)
            best=c.execute("SELECT MAX(stage) FROM submissions WHERE uuid=? AND platform=? AND mode=? AND pattern=? AND kit=? AND submitted_at>=? AND submitted_at<?",(u,platform,mode,pattern,kit,a,b)).fetchone()[0]
            if best is not None: total+=int(best)
        ensure_player(c,sid,u)
        c.execute("UPDATE season_players SET weekly_points=? WHERE season_id=? AND uuid=?",(total,sid,u))


def recalculate_components(c: sqlite3.Connection, sid: int):
    calculate_weekly(c,sid)
    rows=c.execute("SELECT uuid,elo,weekly_points,tournament_points FROM season_players WHERE season_id=?",(sid,)).fetchall()
    if not rows:return
    max_elo=max(float(r[1]) for r in rows) or 1.0
    max_week=max(int(r[2]) for r in rows) or 1
    max_tour=max(int(r[3]) for r in rows) or 1
    for u,e,w,t in rows:
        ec=float(e)/max_elo*1000.0
        wc=float(w)/max_week*1000.0
        tc=float(t)/max_tour*1000.0 if max_tour else 0.0
        mmcl=ec*.40+wc*.30+tc*.30
        c.execute("UPDATE season_players SET elo_component=?,weekly_component=?,tournament_component=?,mmcl=? WHERE season_id=? AND uuid=?",(ec,wc,tc,mmcl,sid,u))


def award_tournament_points(c: sqlite3.Connection, tournament_id: int, placements: dict[str,int]):
    """Award final tournament points exactly once: 1/2/3/4 => 100/75/50/30,
    every other participant => 10. The caller supplies final bracket placement."""
    t=c.execute("SELECT season_id FROM tournaments WHERE id=?",(tournament_id,)).fetchone()
    if not t: raise ValueError("unknown tournament")
    sid=int(t[0])
    for uuid,place in placements.items():
        pts=TOURNAMENT_POINTS.get(int(place),PARTICIPATION_POINTS)
        c.execute("UPDATE tournament_players SET placement=?,points=? WHERE tournament_id=? AND uuid=?",(int(place),pts,tournament_id,uuid.lower()))
        c.execute("UPDATE season_players SET tournament_points=tournament_points+? WHERE season_id=? AND uuid=?",(pts,sid,uuid.lower()))
    recalculate_components(c,sid); c.commit()


def finalize_season(c: sqlite3.Connection, sid: int):
    recalculate_components(c,sid)
    now=datetime.now(timezone.utc).isoformat()
    c.execute("UPDATE seasons SET status='archived',finalized_at=? WHERE id=?",(now,sid))
    c.commit()
