"""Small HTTP API used by public Monster Maze servers.

The Discord bot remains the source of truth. This module deliberately uses only
Python's standard library so it adds no runtime dependency to the bot.
"""
from __future__ import annotations

import json
import os
import threading
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from urllib.parse import unquote

import competitive

DB = None
INSERT_SUBMISSION = None
UPSERT_RUN = None
CREATE_COMPETITION = None
BOARD_ROWS = None
COMPETITION_ROWS = None
REFRESH_BOT = None
POST_FEED = None


def configure(*, db_fn, insert_submission, upsert_run, create_competition,
              board_rows, competition_rows=None, refresh_bot=None, post_feed=None):
    global DB, INSERT_SUBMISSION, UPSERT_RUN, CREATE_COMPETITION, BOARD_ROWS, COMPETITION_ROWS, REFRESH_BOT, POST_FEED
    DB = db_fn
    INSERT_SUBMISSION = insert_submission
    UPSERT_RUN = upsert_run
    CREATE_COMPETITION = create_competition
    BOARD_ROWS = board_rows
    COMPETITION_ROWS = competition_rows
    REFRESH_BOT = refresh_bot
    POST_FEED = post_feed


def token_ok(handler: BaseHTTPRequestHandler) -> bool:
    expected = os.getenv("MM_API_TOKEN", "").strip()
    if not expected:
        return False
    return handler.headers.get("Authorization", "") == "Bearer " + expected


def send_json(handler, status: int, payload: dict):
    body = json.dumps(payload, separators=(",", ":"), ensure_ascii=False).encode("utf-8")
    handler.send_response(status)
    handler.send_header("Content-Type", "application/json; charset=utf-8")
    handler.send_header("Content-Length", str(len(body)))
    handler.send_header("Cache-Control", "no-store")
    handler.end_headers()
    handler.wfile.write(body)


def refresh_bot(platform: str):
    if REFRESH_BOT:
        try:
            REFRESH_BOT(platform)
        except Exception as exc:
            print(f"[api] leaderboard refresh failed: {exc}", flush=True)


def post_feed(run: dict):
    if POST_FEED:
        try:
            POST_FEED(run)
        except Exception as exc:
            print(f"[api] feed post failed: {exc}", flush=True)


def background_updates(platform: str, run: dict, should_feed: bool):
    def work():
        if should_feed:
            post_feed(run)
        refresh_bot(platform)
    threading.Thread(target=work, name="MonsterMazeAPIUpdate", daemon=True).start()


def _competitive_get(path_parts):
    c = DB()
    try:
        competitive.ensure_schema(c)
        season = competitive.ensure_current_season(c)
        sid = int(season[0])
        if path_parts == ["season", "current"]:
            competitive.recalculate_components(c, sid)
            rows = c.execute("SELECT uuid,name,elo,weekly_points,tournament_points,elo_component,weekly_component,tournament_component,mmcl FROM season_players WHERE season_id=? ORDER BY mmcl DESC,uuid ASC",(sid,)).fetchall()
            return {"ok":True,"season":{"id":sid,"number":int(season[1]),"start":season[2],"end":season[3],"status":season[4]},"rows":[_season_row(r) for r in rows]}
        if len(path_parts) == 3 and path_parts[0] in ("mmcl","elo") and path_parts[1] == "leaderboard":
            kind=path_parts[0]
            competitive.recalculate_components(c,sid)
            col="mmcl" if kind=="mmcl" else "elo"
            rows=c.execute(f"SELECT uuid,name,{col} FROM season_players WHERE season_id=? ORDER BY {col} DESC,uuid ASC LIMIT 25",(sid,)).fetchall()
            return {"ok":True,"seasonId":sid,"kind":kind,"rows":[{"uuid":u,"name":n,"score":round(float(v),3)} for u,n,v in rows]}
        if len(path_parts) == 2 and path_parts[0] == "mmcl" and path_parts[1].startswith("player-"):
            uuid=path_parts[1][7:].lower()
            competitive.recalculate_components(c,sid)
            r=c.execute("SELECT uuid,name,elo,weekly_points,tournament_points,elo_component,weekly_component,tournament_component,mmcl FROM season_players WHERE season_id=? AND uuid=?",(sid,uuid)).fetchone()
            return {"ok":True,"seasonId":sid,"player":_season_row(r) if r else None}
        if len(path_parts) == 3 and path_parts[0] == "season" and path_parts[1] == "player":
            uuid=path_parts[2].lower()
            competitive.recalculate_components(c,sid)
            r=c.execute("SELECT uuid,name,elo,weekly_points,tournament_points,elo_component,weekly_component,tournament_component,mmcl FROM season_players WHERE season_id=? AND uuid=?",(sid,uuid)).fetchone()
            return {"ok":True,"seasonId":sid,"player":_season_row(r) if r else None}
        return None
    finally:
        c.close()


def _season_row(r):
    if not r:return None
    return {"uuid":r[0],"name":r[1],"elo":round(float(r[2]),3),"weeklyPoints":int(r[3]),"tournamentPoints":int(r[4]),"eloComponent":round(float(r[5]),3),"weeklyComponent":round(float(r[6]),3),"tournamentComponent":round(float(r[7]),3),"mmcl":round(float(r[8]),3)}


class Handler(BaseHTTPRequestHandler):
    server_version = "MonsterMazeAPI/1.1"

    def log_message(self, fmt, *args):
        print(f"[api] {self.address_string()} - {fmt % args}", flush=True)

    def do_GET(self):
        path = unquote(self.path.split("?", 1)[0]).rstrip("/")
        if path == "/health":
            send_json(self, 200, {"ok": True, "service": "monstermaze-api"})
            return
        if not token_ok(self):
            send_json(self, 401, {"ok": False, "error": "unauthorized"})
            return
        parts = path.strip("/").split("/")
        try:
            if len(parts) == 4 and parts[:3] == ["api", "v1", "challenge"]:
                platform = parts[3]
                if platform not in ("1.8", "1.21"): raise ValueError("unsupported platform")
                comp = CREATE_COMPETITION(platform)
                send_json(self, 200, {"ok":True,"platform":platform,"week":comp["week_key"],"number":comp["number"],"mode":comp["mode"],"pattern":int(comp["pattern"]),"kit":comp["kit"],"start":comp["start_ts"],"end":comp["end_ts"],"status":comp["status"]}); return
            if len(parts) == 5 and parts[:3] == ["api", "v1", "challenge"] and parts[4] == "leaderboard":
                platform=parts[3]
                if platform not in ("1.8","1.21"): raise ValueError("unsupported platform")
                if COMPETITION_ROWS is None: raise ValueError("competition standings unavailable")
                comp=CREATE_COMPETITION(platform); rows=COMPETITION_ROWS(comp,10)
                send_json(self,200,{"ok":True,"platform":platform,"week":comp["week_key"],"number":comp["number"],"mode":comp["mode"],"pattern":int(comp["pattern"]),"kit":comp["kit"],"start":comp["start_ts"],"end":comp["end_ts"],"status":comp["status"],"rows":[{"name":n,"stage":int(s),"timeMs":int(t)} for n,s,t in rows]}); return
            if len(parts) in (6,7) and parts[:3] == ["api","v1","leaderboard"]:
                platform,mode=parts[3],parts[4]
                if platform not in ("1.8","1.21"): raise ValueError("unsupported platform")
                if len(parts)==6 and parts[5]=="overall": rows=BOARD_ROWS("platform=? AND mode=?",[platform,mode],10); kind,pattern="overall",None
                elif len(parts)==7 and parts[5]=="pattern":
                    pattern=int(parts[6]);
                    if not 0<=pattern<3: raise ValueError("invalid pattern")
                    rows=BOARD_ROWS("platform=? AND mode=? AND pattern=?",[platform,mode,pattern],10); kind="pattern"
                elif len(parts)==7 and parts[5]=="kit":
                    kit=str(parts[6]); kit="Slowball" if kit.lower()=="slowballer" else kit
                    if kit not in ("Jumper","Slowball","Body Builder","Repulsor","Maverick"): raise ValueError("invalid kit")
                    rows=BOARD_ROWS("platform=? AND mode=? AND kit=?",[platform,mode,kit],10); kind,pattern="kit",None
                else: raise ValueError("unsupported leaderboard route")
                send_json(self,200,{"ok":True,"platform":platform,"mode":mode,"kind":kind,"pattern":pattern,"rows":[{"name":n,"kit":k,"stage":s} for n,k,s in rows]}); return
            if len(parts)==6 and parts[:3]==["api","v1","pb"]:
                platform,mode,uuid=parts[3],parts[4],parts[5]
                if platform not in ("1.8","1.21"): raise ValueError("unsupported platform")
                c=DB(); rows=c.execute("SELECT pattern,kit,stage,time_ms FROM runs WHERE platform=? AND mode=? AND uuid=? ORDER BY pattern ASC,stage DESC,time_ms ASC,kit ASC",(platform,mode.lower(),uuid.lower())).fetchall(); c.close()
                send_json(self,200,{"ok":True,"platform":platform,"mode":mode.lower(),"uuid":uuid.lower(),"rows":[{"pattern":int(p),"kit":k,"stage":int(s),"timeMs":int(t)} for p,k,s,t in rows]}); return
            if len(parts)>=4 and parts[:3]==["api","v1"]:
                result=_competitive_get(parts[3:])
                if result is not None: send_json(self,200,result); return
        except (ValueError,TypeError,KeyError,IndexError) as exc:
            send_json(self,400,{"ok":False,"error":str(exc)}); return
        except Exception as exc:
            print(f"[api] GET failed: {exc}",flush=True); send_json(self,500,{"ok":False,"error":"internal_error"}); return
        send_json(self,404,{"ok":False,"error":"not_found"})

    def do_POST(self):
        path=unquote(self.path.split("?",1)[0]).rstrip("/")
        if not token_ok(self): send_json(self,401,{"ok":False,"error":"unauthorized"}); return
        try:
            length=int(self.headers.get("Content-Length","0"))
            if length<=0 or length>64*1024: raise ValueError("invalid content length")
            raw=self.rfile.read(length); payload=json.loads(raw.decode("utf-8"))
            if path=="/api/v1/runs":
                required=("submissionId","platform","mode","pattern","kit","uuid","name","stage","timeMs")
                missing=[k for k in required if k not in payload]
                if missing: raise ValueError("missing fields: "+",".join(missing))
                platform=str(payload["platform"])
                if platform not in ("1.8","1.21"): raise ValueError("unsupported platform")
                pattern=int(payload["pattern"])
                if not 0<=pattern<3: raise ValueError("invalid pattern")
                stage=int(payload["stage"])
                if stage<1 or stage>10000: raise ValueError("invalid stage")
                kit=str(payload["kit"]); kit="Slowball" if kit.lower()=="slowballer" else kit
                if kit not in ("Jumper","Slowball","Body Builder","Repulsor","Maverick"): raise ValueError("invalid kit")
                normalized={"submission_id":str(payload["submissionId"])[:256],"platform":platform,"plugin":str(payload.get("plugin","1.0.0"))[:64],"mode":str(payload["mode"]).lower()[:64],"pattern":pattern,"kit":kit,"uuid":str(payload["uuid"]).lower()[:64],"name":str(payload["name"])[:256],"stage":stage,"time_ms":max(0,int(payload.get("timeMs",0))),"config_hash":str(payload.get("configHash",""))[:128],"submitted_at":max(0,int(payload.get("submittedAt",0)))}
                if not normalized["submitted_at"]:
                    import time; normalized["submitted_at"]=int(time.time()*1000)
                inserted=INSERT_SUBMISSION(normalized); improved=UPSERT_RUN(normalized)
                send_json(self,200,{"ok":True,"accepted":True,"newSubmission":bool(inserted),"newLifetimePB":bool(improved)}); background_updates(platform,normalized,bool(inserted)); return
            if path=="/api/v1/matches":
                required=("matchId","platform","mode","pattern","kit","startedAt","endedAt","players")
                missing=[k for k in required if k not in payload]
                if missing: raise ValueError("missing fields: "+",".join(missing))
                platform=str(payload["platform"])
                if platform not in ("1.8","1.21"): raise ValueError("unsupported platform")
                players=payload["players"]
                if not isinstance(players,list) or len(players)<2: raise ValueError("a multiplayer match needs at least 2 players")
                if len(players)>64: raise ValueError("too many players")
                seen=set(); normalized_players=[]
                for p in players:
                    uuid=str(p.get("uuid","")).lower()[:64]
                    if not uuid or uuid in seen: raise ValueError("invalid or duplicate player uuid")
                    seen.add(uuid)
                    placement=int(p["placement"]); tick=int(p["eliminationTick"])
                    if placement<1 or tick<0: raise ValueError("invalid placement or elimination tick")
                    normalized_players.append({"uuid":uuid,"name":str(p.get("name",""))[:256],"placement":placement,"elimination_tick":tick})
                c=DB(); competitive.ensure_schema(c); competitive.record_match(c,{"id":str(payload["matchId"])[:128],"platform":platform,"mode":str(payload["mode"]).lower()[:64],"pattern":int(payload["pattern"]),"kit":str(payload["kit"])[:64],"started_at":int(payload["startedAt"]),"ended_at":int(payload["endedAt"]),"tournament_id":payload.get("tournamentId")},normalized_players); c.close()
                send_json(self,200,{"ok":True,"accepted":True,"matchId":str(payload["matchId"])}) ; return
            send_json(self,404,{"ok":False,"error":"not_found"})
        except (ValueError,TypeError,KeyError,json.JSONDecodeError) as exc:
            send_json(self,400,{"ok":False,"error":str(exc)})
        except Exception as exc:
            print(f"[api] POST failed: {exc}",flush=True); send_json(self,500,{"ok":False,"error":"internal_error"})


def start_server(*,host="0.0.0.0",port=8090):
    server=ThreadingHTTPServer((host,int(port)),Handler)
    thread=threading.Thread(target=server.serve_forever,name="MonsterMazeAPI",daemon=True); thread.start()
    print(f"[api] listening on {host}:{port}",flush=True); return server
