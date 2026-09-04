"""Small HTTP API used by public Monster Maze servers."""
from __future__ import annotations
import json, os, threading
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from urllib.parse import unquote
import competitive
import tournament

DB=INSERT_SUBMISSION=UPSERT_RUN=CREATE_COMPETITION=BOARD_ROWS=COMPETITION_ROWS=REFRESH_BOT=POST_FEED=None

def configure(*,db_fn,insert_submission,upsert_run,create_competition,board_rows,competition_rows=None,refresh_bot=None,post_feed=None):
    global DB,INSERT_SUBMISSION,UPSERT_RUN,CREATE_COMPETITION,BOARD_ROWS,COMPETITION_ROWS,REFRESH_BOT,POST_FEED
    DB=db_fn;INSERT_SUBMISSION=insert_submission;UPSERT_RUN=upsert_run;CREATE_COMPETITION= create_competition;BOARD_ROWS=board_rows;COMPETITION_ROWS=competition_rows;REFRESH_BOT=refresh_bot;POST_FEED=post_feed

def token_ok(h):
    expected=os.getenv("MM_API_TOKEN","").strip();return bool(expected) and h.headers.get("Authorization","")=="Bearer "+expected

def send_json(h,status,payload):
    body=json.dumps(payload,separators=(",",":"),ensure_ascii=False).encode("utf-8");h.send_response(status);h.send_header("Content-Type","application/json; charset=utf-8");h.send_header("Content-Length",str(len(body)));h.send_header("Cache-Control","no-store");h.end_headers();h.wfile.write(body)

def refresh_bot(platform):
    if REFRESH_BOT:
        try:REFRESH_BOT(platform)
        except Exception as exc:print(f"[api] leaderboard refresh failed: {exc}",flush=True)

def post_feed(run):
    if POST_FEED:
        try:POST_FEED(run)
        except Exception as exc:print(f"[api] feed post failed: {exc}",flush=True)

def background_updates(platform,run,should_feed):
    def work():
        if should_feed:post_feed(run)
        refresh_bot(platform)
    threading.Thread(target=work,name="MonsterMazeAPIUpdate",daemon=True).start()

def season_row(r):
    if not r:return None
    return {"uuid":r[0],"name":r[1],"elo":round(float(r[2]),3),"weeklyPoints":int(r[3]),"tournamentPoints":int(r[4]),"eloComponent":round(float(r[5]),3),"weeklyComponent":round(float(r[6]),3),"tournamentComponent":round(float(r[7]),3),"mmcl":round(float(r[8]),3)}

def competitive_get(parts):
    c=DB()
    try:
        competitive.ensure_schema(c);season=competitive.ensure_current_season(c);sid=int(season[0])
        if parts==["season","current"]:
            competitive.recalculate_components(c,sid);rows=c.execute("SELECT uuid,name,elo,weekly_points,tournament_points,elo_component,weekly_component,tournament_component,mmcl FROM season_players WHERE season_id=? ORDER BY mmcl DESC,uuid ASC",(sid,)).fetchall()
            return {"ok":True,"season":{"id":sid,"number":int(season[1]),"start":season[2],"end":season[3],"status":season[4]},"rows":[season_row(r) for r in rows]}
        if len(parts)==2 and parts[0] in ("mmcl","elo","weekly","tournament") and parts[1]=="leaderboard":
            competitive.recalculate_components(c,sid);col={"mmcl":"mmcl","elo":"elo","weekly":"weekly_points","tournament":"tournament_points"}[parts[0]];rows=c.execute(f"SELECT uuid,name,{col} FROM season_players WHERE season_id=? ORDER BY {col} DESC,uuid ASC LIMIT 25",(sid,)).fetchall()
            return {"ok":True,"seasonId":sid,"kind":parts[0],"rows":[{"uuid":u,"name":n,"score":round(float(v),3)} for u,n,v in rows]}
        if parts==["mmr","leaderboard"]:
            competitive.calculate_mmr(c);rows=c.execute("SELECT uuid,name,mmr FROM permanent_ratings ORDER BY mmr DESC,uuid ASC LIMIT 25").fetchall()
            return {"ok":True,"kind":"mmr","rows":[{"uuid":u,"name":n,"score":round(float(v),3)} for u,n,v in rows]}
        if len(parts)==3 and parts[0] in ("mmcl","season","mmr") and parts[1]=="player":
            uuid=parts[2].lower()
            if parts[0]=="mmr":
                competitive.calculate_mmr(c);r=c.execute("SELECT uuid,name,mmr FROM permanent_ratings WHERE uuid=?",(uuid,)).fetchone();return {"ok":True,"player":{"uuid":r[0],"name":r[1],"mmr":round(float(r[2]),3)} if r else None}
            competitive.recalculate_components(c,sid);r=c.execute("SELECT uuid,name,elo,weekly_points,tournament_points,elo_component,weekly_component,tournament_component,mmcl FROM season_players WHERE season_id=? AND uuid=?",(sid,uuid)).fetchone();return {"ok":True,"seasonId":sid,"player":season_row(r)}
        return None
    finally:c.close()

def historical_get(parts):
    c=DB()
    try:
        competitive.ensure_schema(c)
        if parts==["seasons"]:
            rows=c.execute("SELECT id,season_number,start_ts,end_ts,status,finalized_at FROM seasons ORDER BY season_number DESC").fetchall()
            return {"ok":True,"seasons":[{"id":int(sid),"number":int(num),"start":start,"end":end,"status":status,"finalizedAt":finalized} for sid,num,start,end,status,finalized in rows]}
        if len(parts)>=2 and parts[0]=="seasons":
            try:sid=int(parts[1])
            except ValueError:raise ValueError("invalid season id")
            row=c.execute("SELECT id FROM seasons WHERE id=?",(sid,)).fetchone()
            if not row:return {"ok":False,"error":"season_not_found"}
            if len(parts)==2:return {"ok":True,"season":competitive.season_summary(c,sid)}
            if len(parts)==3 and parts[2]=="leaderboard":return {"ok":True,"seasonId":sid,"kind":"mmcl","rows":competitive.season_leaderboard(c,sid,"mmcl",25)}
            if len(parts)==4 and parts[2]=="leaderboard":return {"ok":True,"seasonId":sid,"kind":parts[3].lower(),"rows":competitive.season_leaderboard(c,sid,parts[3],25)}
            if len(parts)==3 and parts[2]=="tournaments":return {"ok":True,"seasonId":sid,"tournaments":competitive.season_tournaments(c,sid)}
            if len(parts)==4 and parts[2]=="player":
                uuid=parts[3].lower();row=c.execute("SELECT 1 FROM season_players WHERE season_id=? AND lower(uuid)=?",(sid,uuid)).fetchone()
                return {"ok":True,"seasonId":sid,"player":next((p for p in competitive.season_summary(c,sid)["players"] if p["uuid"].lower()==uuid),None)} if row else {"ok":True,"seasonId":sid,"player":None}
        if len(parts)==3 and parts[0]=="player" and parts[2]=="seasons":return {"ok":True,"uuid":parts[1].lower(),"seasons":competitive.player_season_history(c,parts[1],100)}
        return None
    finally:c.close()

def tournament_payload(c,t):
    if not t:return None
    tid=int(t[0]);rows=c.execute("SELECT uuid,name,seed,placement,points FROM tournament_players WHERE tournament_id=? ORDER BY CASE WHEN placement IS NULL THEN 99 ELSE placement END,registered_at ASC",(tid,)).fetchall()
    matches=c.execute("SELECT id,round_number,slot,player1_uuid,player2_uuid,best_of,player1_wins,player2_wins,winner_uuid,status FROM tournament_matches WHERE tournament_id=? ORDER BY round_number,slot",(tid,)).fetchall()
    return {"id":tid,"seasonId":int(t[1]),"number":int(t[2]),"name":t[3],"registrationStart":t[4],"registrationEnd":t[5],"start":t[6],"status":t[7],"bracketSize":t[8],"players":[{"uuid":u,"name":n,"seed":s,"placement":p,"points":int(pt)} for u,n,s,p,pt in rows],"matches":[{"id":int(i),"round":int(r),"slot":int(sl),"player1":p1,"player2":p2,"bestOf":int(bo),"player1Wins":int(w1),"player2Wins":int(w2),"winner":w,"status":st} for i,r,sl,p1,p2,bo,w1,w2,w,st in matches]}

def tournament_get(parts):
    c=DB()
    try:
        competitive.ensure_schema(c);tournament.ensure_schema(c);season=competitive.ensure_current_season(c);sid=int(season[0])
        if parts==["current"]:
            row=c.execute("SELECT id,season_id,number,name,registration_start,registration_end,start_ts,status,bracket_size FROM tournaments WHERE season_id=? AND status!='complete' ORDER BY number DESC LIMIT 1",(sid,)).fetchone();return {"ok":True,"tournament":tournament_payload(c,row)}
        if len(parts)==1:
            tid=int(parts[0]);row=c.execute("SELECT id,season_id,number,name,registration_start,registration_end,start_ts,status,bracket_size FROM tournaments WHERE id=?",(tid,)).fetchone();return {"ok":True,"tournament":tournament_payload(c,row)}
        if len(parts)==2 and parts[0]=="player":
            current=c.execute("SELECT id FROM tournaments WHERE season_id=? AND status!='complete' ORDER BY number DESC LIMIT 1",(sid,)).fetchone()
            if not current:return {"ok":True,"tournamentId":None,"match":None}
            tid=int(current[0]);return {"ok":True,"tournamentId":tid,"match":tournament.current_match(c,tid,parts[1])}
        return None
    finally:c.close()

class Handler(BaseHTTPRequestHandler):
    server_version="MonsterMazeAPI/1.5"
    def log_message(self,fmt,*args):print(f"[api] {self.address_string()} - {fmt % args}",flush=True)
    def do_GET(self):
        path=unquote(self.path.split("?",1)[0]).rstrip("/")
        if path=="/health":send_json(self,200,{"ok":True,"service":"monstermaze-api"});return
        if not token_ok(self):send_json(self,401,{"ok":False,"error":"unauthorized"});return
        parts=path.strip("/").split("/")
        try:
            if len(parts)==4 and parts[:3]==["api","v1","challenge"]:
                platform=parts[3]
                if platform not in ("1.8","1.21"):raise ValueError("unsupported platform")
                comp=CREATE_COMPETITION(platform);send_json(self,200,{"ok":True,"platform":platform,"week":comp["week_key"],"number":comp["number"],"mode":comp["mode"],"pattern":int(comp["pattern"]),"kit":comp["kit"],"start":comp["start_ts"],"end":comp["end_ts"],"status":comp["status"]});return
            if len(parts)==5 and parts[:3]==["api","v1","challenge"] and parts[4]=="leaderboard":
                platform=parts[3]
                if platform not in ("1.8","1.21"):raise ValueError("unsupported platform")
                comp=CREATE_COMPETITION(platform);rows=COMPETITION_ROWS(comp,10);send_json(self,200,{"ok":True,"platform":platform,"week":comp["week_key"],"number":comp["number"],"mode":comp["mode"],"pattern":int(comp["pattern"]),"kit":comp["kit"],"start":comp["start_ts"],"end":comp["end_ts"],"status":comp["status"],"rows":[{"name":n,"stage":int(s),"timeMs":int(t)} for n,s,t in rows]});return
            if len(parts) in (6,7) and parts[:3]==["api","v1","leaderboard"]:
                platform,mode=parts[3],parts[4]
                if platform not in ("1.8","1.21"):raise ValueError("unsupported platform")
                if len(parts)==6 and parts[5]=="overall":rows=BOARD_ROWS("platform=? AND mode=?",[platform,mode],10);kind,pattern="overall",None
                elif len(parts)==7 and parts[5]=="pattern":
                    pattern=int(parts[6]);
                    if not 0<=pattern<3:raise ValueError("invalid pattern")
                    rows=BOARD_ROWS("platform=? AND mode=? AND pattern=?",[platform,mode,pattern],10);kind="pattern"
                elif len(parts)==7 and parts[5]=="kit":
                    kit=str(parts[6]);kit="Slowball" if kit.lower()=="slowballer" else kit
                    if kit not in ("Jumper","Slowball","Body Builder","Repulsor","Maverick"):raise ValueError("invalid kit")
                    rows=BOARD_ROWS("platform=? AND mode=? AND kit=?",[platform,mode,kit],10);kind,pattern="kit",None
                else:raise ValueError("unsupported leaderboard route")
                send_json(self,200,{"ok":True,"platform":platform,"mode":mode,"kind":kind,"pattern":pattern,"rows":[{"name":n,"kit":k,"stage":s} for n,k,s in rows]});return
            if len(parts)==6 and parts[:3]==["api","v1","pb"]:
                platform,mode,uuid=parts[3],parts[4],parts[5]
                if platform not in ("1.8","1.21"):raise ValueError("unsupported platform")
                c=DB();rows=c.execute("SELECT pattern,kit,stage,time_ms FROM runs WHERE platform=? AND mode=? AND uuid=? ORDER BY pattern ASC,stage DESC,time_ms ASC,kit ASC",(platform,mode.lower(),uuid.lower())).fetchall();c.close();send_json(self,200,{"ok":True,"platform":platform,"mode":mode.lower(),"uuid":uuid.lower(),"rows":[{"pattern":int(p),"kit":k,"stage":int(s),"timeMs":int(t)} for p,k,s,t in rows]});return
            if len(parts)>=4 and parts[:3]==["api","v1"]:
                if parts[3]=="tournament":
                    if parts[4:]==["leaderboard"]:
                        result=competitive_get(["tournament","leaderboard"]);send_json(self,200,result);return
                    result=tournament_get(parts[4:])
                    if result is not None:send_json(self,200,result);return
                if parts[3] in ("seasons","player"):
                    result=historical_get(parts[3:])
                    if result is not None:send_json(self,200,result);return
                result=competitive_get(parts[3:])
                if result is not None:send_json(self,200,result);return
        except (ValueError,TypeError,KeyError,IndexError) as exc:send_json(self,400,{"ok":False,"error":str(exc)});return
        except Exception as exc:print(f"[api] GET failed: {exc}",flush=True);send_json(self,500,{"ok":False,"error":"internal_error"});return
        send_json(self,404,{"ok":False,"error":"not_found"})

    def do_POST(self,):
        path=unquote(self.path.split("?",1)[0]).rstrip("/")
        if not token_ok(self):send_json(self,401,{"ok":False,"error":"unauthorized"});return
        try:
            length=int(self.headers.get("Content-Length","0"))
            if length<=0 or length>64*1024:raise ValueError("invalid content length")
            payload=json.loads(self.rfile.read(length).decode("utf-8"))
            if path=="/api/v1/runs":
                required=("submissionId","platform","mode","pattern","kit","uuid","name","stage","timeMs");missing=[k for k in required if k not in payload]
                if missing:raise ValueError("missing fields: "+",".join(missing))
                platform=str(payload["platform"])
                if platform not in ("1.8","1.21"):raise ValueError("unsupported platform")
                pattern=int(payload["pattern"]);stage=int(payload["stage"])
                if not 0<=pattern<3:raise ValueError("invalid pattern")
                if stage<1 or stage>10000:raise ValueError("invalid stage")
                kit=str(payload["kit"]);kit="Slowball" if kit.lower()=="slowballer" else kit
                if kit not in ("Jumper","Slowball","Body Builder","Repulsor","Maverick"):raise ValueError("invalid kit")
                normalized={"submission_id":str(payload["submissionId"])[:256],"platform":platform,"plugin":str(payload.get("plugin","1.0.0"))[:64],"mode":str(payload["mode"]).lower()[:64],"pattern":pattern,"kit":kit,"uuid":str(payload["uuid"]).lower()[:64],"name":str(payload["name"])[:256],"stage":stage,"time_ms":max(0,int(payload.get("timeMs",0))),"config_hash":str(payload.get("configHash",""))[:128],"submitted_at":max(0,int(payload.get("submittedAt",0)))}
                if not normalized["submitted_at"]:
                    import time;normalized["submitted_at"]=int(time.time()*1000)
                inserted=INSERT_SUBMISSION(normalized);improved=UPSERT_RUN(normalized)
                if improved:
                    c=DB();competitive.calculate_mmr(c);c.close()
                send_json(self,200,{"ok":True,"accepted":True,"newSubmission":bool(inserted),"newLifetimePB":bool(improved)});background_updates(platform,normalized,bool(inserted));return
            if path=="/api/v1/matches":
                required=("matchId","platform","mode","pattern","kit","startedAt","endedAt","players");missing=[k for k in required if k not in payload]
                if missing:raise ValueError("missing fields: "+",".join(missing))
                platform=str(payload["platform"])
                if platform not in ("1.8","1.21"):raise ValueError("unsupported platform")
                if not isinstance(payload["players"],list) or len(payload["players"])<2 or len(payload["players"])>64:raise ValueError("invalid player list")
                players=[];seen=set()
                for p in payload["players"]:
                    uuid=str(p.get("uuid","")).lower()[:64]
                    if not uuid or uuid in seen:raise ValueError("invalid or duplicate player uuid")
                    seen.add(uuid);placement=int(p["placement"]);tick=int(p["eliminationTick"])
                    if placement<1 or placement>len(payload["players"]) or tick<-1:raise ValueError("invalid placement or elimination tick")
                    players.append({"uuid":uuid,"name":str(p.get("name",""))[:256],"placement":placement,"elimination_tick":tick})
                tournament_id=payload.get("tournamentId")
                tournament_match_id=payload.get("tournamentMatchId")
                tournament_game_number=payload.get("tournamentGameNumber")
                if tournament_id is not None and (tournament_match_id is None or tournament_game_number is None):raise ValueError("tournament match metadata is incomplete")
                c=DB();competitive.ensure_schema(c);accepted=competitive.record_match(c,{"id":str(payload["matchId"])[:128],"platform":platform,"mode":str(payload["mode"]).lower()[:64],"pattern":int(payload["pattern"]),"kit":str(payload["kit"])[:64],"started_at":int(payload["startedAt"]),"ended_at":int(payload["endedAt"]),"tournament_id":tournament_id,"tournament_match_id":tournament_match_id,"tournament_game_number":tournament_game_number},players);c.close();send_json(self,200,{"ok":True,"accepted":bool(accepted),"matchId":str(payload["matchId"])});return
            send_json(self,404,{"ok":False,"error":"not_found"})
        except (ValueError,TypeError,KeyError,json.JSONDecodeError) as exc:send_json(self,400,{"ok":False,"error":str(exc)})
        except Exception as exc:print(f"[api] POST failed: {exc}",flush=True);send_json(self,500,{"ok":False,"error":"internal_error"})

def start_server(*,host="0.0.0.0",port=8090):
    server=ThreadingHTTPServer((host,int(port)),Handler);threading.Thread(target=server.serve_forever,name="MonsterMazeAPI",daemon=True).start();print(f"[api] listening on {host}:{port}",flush=True);return server
