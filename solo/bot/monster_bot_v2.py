"""Monster Maze SOLO Discord bot with lifetime PBs and complete weekly attempt history."""
import asyncio, json, os, random, re, sqlite3
from datetime import datetime, timedelta, timezone
from zoneinfo import ZoneInfo
import discord

HERE=os.path.dirname(os.path.abspath(__file__))
CFG=os.path.join(HERE,"config.json")
DB=os.path.join(HERE,"leaderboard.db")
PLATFORMS=("1.8","1.21")
PLATFORM_LABELS={"1.8":"Minecraft 1.8.9","1.21":"Minecraft 1.21.11"}
KITS=["Jumper","Slowball","Body Builder","Repulsor","Maverick"]
KIT_LABELS={"Slowball":"Slowballer"}
PATTERNS=3
MAX_HTTP_RETRIES=5
REFRESH_DELAY=2.0
DEFAULT_COMPETITION_CHANNEL="competitions"
DEFAULT_TZ="Australia/Brisbane"
HISTORY_WINDOW=8

def normalize_kit(kit):
    if not kit:return None
    x=kit.strip()
    if x.lower()=="slowballer":return "Slowball"
    return x

def kit_label(kit):return KIT_LABELS.get(kit,kit)

def load_config():
    with open(CFG,encoding="utf-8") as f:return json.load(f)

def db():
    c=sqlite3.connect(DB); c.execute("PRAGMA journal_mode=WAL")
    cols=[r[1] for r in c.execute("PRAGMA table_info(runs)").fetchall()]
    if not cols:
        c.execute("CREATE TABLE runs(platform TEXT NOT NULL,mode TEXT NOT NULL,pattern INTEGER NOT NULL,kit TEXT NOT NULL,uuid TEXT NOT NULL,name TEXT,stage INTEGER NOT NULL,time_ms INTEGER NOT NULL,PRIMARY KEY(platform,mode,pattern,kit,uuid))")
    elif "platform" not in cols:
        c.execute("ALTER TABLE runs RENAME TO runs_legacy")
        c.execute("CREATE TABLE runs(platform TEXT NOT NULL,mode TEXT NOT NULL,pattern INTEGER NOT NULL,kit TEXT NOT NULL,uuid TEXT NOT NULL,name TEXT,stage INTEGER NOT NULL,time_ms INTEGER NOT NULL,PRIMARY KEY(platform,mode,pattern,kit,uuid))")
        c.execute("INSERT INTO runs SELECT '1.8',mode,pattern,kit,uuid,name,stage,time_ms FROM runs_legacy")
        c.execute("DROP TABLE runs_legacy")
    c.execute("CREATE TABLE IF NOT EXISTS submissions(id TEXT PRIMARY KEY,platform TEXT NOT NULL,mode TEXT NOT NULL,pattern INTEGER NOT NULL,kit TEXT NOT NULL,uuid TEXT NOT NULL,name TEXT,stage INTEGER NOT NULL,time_ms INTEGER NOT NULL,submitted_at INTEGER NOT NULL)")
    c.execute("CREATE TABLE IF NOT EXISTS boards(board_key TEXT PRIMARY KEY,channel_id TEXT,msg_id TEXT)")
    c.execute("""CREATE TABLE IF NOT EXISTS competitions(
        platform TEXT NOT NULL,number INTEGER NOT NULL,week_key TEXT NOT NULL,mode TEXT NOT NULL,pattern INTEGER NOT NULL,kit TEXT NOT NULL,
        start_ts TEXT NOT NULL,end_ts TEXT NOT NULL,channel_id TEXT,msg_id TEXT,status TEXT NOT NULL,standings_json TEXT,
        PRIMARY KEY(platform,week_key),UNIQUE(platform,number))""")
    c.commit(); return c

def upsert_run(r):
    r["kit"]=normalize_kit(r.get("kit"))
    if any(k not in r for k in ("platform","mode","pattern","kit","uuid","name","stage")) or r["platform"] not in PLATFORMS or r["kit"] not in KITS:return False
    if not 0<=int(r["pattern"])<PATTERNS:return False
    key=(r["platform"],r["mode"],int(r["pattern"]),r["kit"],r["uuid"])
    c=db(); old=c.execute("SELECT stage,time_ms FROM runs WHERE platform=? AND mode=? AND pattern=? AND kit=? AND uuid=?",key).fetchone()
    better=old is None or int(r["stage"])>old[0] or (int(r["stage"])==old[0] and int(r.get("time_ms",0))<old[1])
    if better:
        c.execute("INSERT INTO runs(platform,mode,pattern,kit,uuid,name,stage,time_ms) VALUES(?,?,?,?,?,?,?,?) ON CONFLICT(platform,mode,pattern,kit,uuid) DO UPDATE SET name=excluded.name,stage=excluded.stage,time_ms=excluded.time_ms",key+(r["name"],int(r["stage"]),int(r.get("time_ms",0))))
    c.commit(); c.close(); return better

def insert_submission(r,message_id=None):
    sid=r.get("submission_id") or (f"discord-{message_id}" if message_id else f"{r['uuid']}-{r['submitted_at']}-{r['stage']}-{r['pattern']}-{r['kit']}")
    r["submission_id"]=sid
    c=db(); cur=c.execute("SELECT 1 FROM submissions WHERE id=?",(sid,)).fetchone()
    if cur:c.close();return False
    c.execute("INSERT INTO submissions VALUES(?,?,?,?,?,?,?,?,?,?)",(sid,r["platform"],r["mode"],int(r["pattern"]),r["kit"],r["uuid"],r["name"],int(r["stage"]),int(r.get("time_ms",0)),int(r["submitted_at"])))
    c.commit();c.close();return True

def board_rows(where,args,limit):
    c=db(); q="""WITH ranked AS(SELECT name,kit,stage,time_ms,ROW_NUMBER() OVER(PARTITION BY uuid ORDER BY stage DESC,time_ms ASC,kit ASC,name ASC) rn FROM runs WHERE %s) SELECT name,kit,stage FROM ranked WHERE rn=1 ORDER BY stage DESC,time_ms ASC,name ASC LIMIT ?"""%where
    rows=c.execute(q,tuple(args)+(limit,)).fetchall();c.close();return rows

def overall_board(p,m,n):return board_rows("platform=? AND mode=?",[p,m],n)
def pattern_board(p,m,x,n):return board_rows("platform=? AND mode=? AND pattern=?",[p,m,x],n)
def kit_board(p,m,x,k,n):return board_rows("platform=? AND mode=? AND pattern=? AND kit=?",[p,m,x,k],n)

def competition_rows(comp,n):
    c=db(); rows=c.execute("""WITH ranked AS(
      SELECT name,stage,time_ms,ROW_NUMBER() OVER(PARTITION BY uuid ORDER BY stage DESC,time_ms ASC,submitted_at DESC) rn
      FROM submissions WHERE platform=? AND mode=? AND pattern=? AND kit=? AND submitted_at>=? AND submitted_at<?)
      SELECT name,stage,time_ms FROM ranked WHERE rn=1 ORDER BY stage DESC,time_ms ASC,name ASC LIMIT ?""",(comp["platform"],comp["mode"],comp["pattern"],comp["kit"],comp["start_epoch"],comp["end_epoch"],n)).fetchall();c.close();return rows

def tz_for(cfg):
    try:return ZoneInfo(cfg.get("competition_timezone",DEFAULT_TZ))
    except Exception:return ZoneInfo(DEFAULT_TZ)

def week_window(tz):
    local=datetime.now(timezone.utc).astimezone(tz); start=(local-timedelta(days=local.weekday())).replace(hour=0,minute=0,second=0,microsecond=0); end=start+timedelta(days=7)
    return start,end,start.strftime("%G-W%V")

def valid_kits(mode):return [k for k in KITS if not(mode.lower()=="original" and k=="Maverick")]

def competition_from_row(row):
    keys=("platform","number","week_key","mode","pattern","kit","start_ts","end_ts","channel_id","msg_id","status","standings_json")
    d=dict(zip(keys,row));d["start_epoch"]=int(datetime.fromisoformat(d["start_ts"]).timestamp()*1000);d["end_epoch"]=int(datetime.fromisoformat(d["end_ts"]).timestamp()*1000);return d

def create_competition(platform,cfg):
    tz=tz_for(cfg);start,end,wk=week_window(tz);c=db();row=c.execute("SELECT platform,number,week_key,mode,pattern,kit,start_ts,end_ts,channel_id,msg_id,status,standings_json FROM competitions WHERE platform=? AND week_key=?",(platform,wk)).fetchone()
    if row:c.close();return competition_from_row(row)
    modes=list(cfg.get("platform_modes",{}).get(platform,cfg.get("modes",[])));recent=c.execute("SELECT mode,pattern,kit FROM competitions WHERE platform=? ORDER BY number DESC LIMIT ?",(platform,HISTORY_WINDOW)).fetchall();recent=set(recent)
    candidates=[(m.lower(),p,k) for m in modes for p in range(PATTERNS) for k in valid_kits(m)];fresh=[x for x in candidates if x not in recent];chosen=random.SystemRandom().choice(fresh or candidates)
    num=c.execute("SELECT COALESCE(MAX(number),0)+1 FROM competitions WHERE platform=?",(platform,)).fetchone()[0];st=start.astimezone(timezone.utc).isoformat();en=end.astimezone(timezone.utc).isoformat()
    c.execute("INSERT INTO competitions VALUES(?,?,?,?,?,?,?,?,NULL,NULL,'current',NULL)",(platform,num,wk,chosen[0],chosen[1],chosen[2],st,en));c.commit();c.close()
    return {"platform":platform,"number":num,"week_key":wk,"mode":chosen[0],"pattern":chosen[1],"kit":chosen[2],"start_ts":st,"end_ts":en,"channel_id":None,"msg_id":None,"status":"current","standings_json":None,"start_epoch":int(start.timestamp()*1000),"end_epoch":int(end.timestamp()*1000)}

def archive_competition(comp):
    if comp["status"]=="archived" and comp.get("standings_json"):return comp
    rows=competition_rows(comp,25);snap=[{"name":n,"stage":s,"time_ms":t} for n,s,t in rows];encoded=json.dumps(snap,separators=(",",":"),ensure_ascii=False)
    c=db();c.execute("UPDATE competitions SET status='archived',standings_json=? WHERE platform=? AND week_key=?",(encoded,comp["platform"],comp["week_key"]));c.commit();c.close();comp["status"]="archived";comp["standings_json"]=encoded;return comp

def parse_embed(embed,message_id=None,message_time=None):
    title=embed.title or "";m=re.search(r"(?:new PB|Solo Run) \(stage (\d+)\)|Solo Run \(stage (\d+)\)",title,re.I)
    if not m:return None
    stage=int(m.group(1) or m.group(2));fields={f.name.strip().lower():f.value.strip() for f in embed.fields};mode=fields.get("mode");kit=normalize_kit(fields.get("kit"));pt=fields.get("pattern","")
    if not mode or not kit:return None
    pm=re.search(r"Maze\s+(\d+)",pt,re.I)
    if not pm or not 0<=int(pm.group(1))-1<PATTERNS:return None
    platform="1.8" if fields.get("minecraft","").startswith("1.8") else "1.21" if fields.get("minecraft","").startswith("1.21") else None
    footer=embed.footer.text if embed.footer and embed.footer.text else ""
    if not platform:
        x=re.search(r"platform\s+(1\.8|1\.21)",footer,re.I);platform=x.group(1) if x else "1.8"
    um=re.search(r"uuid\s+([0-9a-f-]{8,})",footer,re.I)
    if not um:return None
    sm=re.search(r"submission\s+([^\s|]+)",footer,re.I);tm=re.search(r"submittedAt\s+(\d+)",footer,re.I)
    time_ms=0;tx=re.search(r"(\d+)m\s+(\d+)s",fields.get("time",""));
    if tx:time_ms=int(tx.group(1))*60000+int(tx.group(2))*1000
    submitted=int(tm.group(1)) if tm else int(message_time.timestamp()*1000) if message_time else 0
    name=re.split(r"\s-\s(?:new PB|Solo Run)",title,1,flags=re.I)[0].strip()[:256]
    return {"name":name,"platform":platform,"mode":mode.lower()[:64],"pattern":int(pm.group(1))-1,"kit":kit,"stage":stage,"time_ms":time_ms,"uuid":um.group(1).lower(),"submitted_at":submitted,"submission_id":sm.group(1) if sm else (f"discord-{message_id}" if message_id else None)}

def get_board_msg(k):
    c=db();r=c.execute("SELECT channel_id,msg_id FROM boards WHERE board_key=?",(k,)).fetchone();c.close();return r

def set_board_msg(k,ch,msg):
    c=db();c.execute("INSERT INTO boards VALUES(?,?,?) ON CONFLICT(board_key) DO UPDATE SET channel_id=excluded.channel_id,msg_id=excluded.msg_id",(k,str(ch),str(msg)));c.commit();c.close()

class MonsterBot(discord.Client):
    def __init__(self,cfg):
        super().__init__(intents=discord.Intents(messages=True,message_content=True,guilds=True));self.cfg=cfg;self.top_n=max(1,min(int(cfg.get("top_n",10)),25));self.platform_modes={p:list(cfg.get("platform_modes",{}).get(p,cfg.get("modes",["modern"]))) for p in PLATFORMS};self.tasks={};self.lock=asyncio.Lock();self.ready_once=False;self.tz=tz_for(cfg)
    def channel(self,ref):
        if ref is None:return None
        try:return self.get_channel(int(ref))
        except Exception:pass
        for g in self.guilds:
            x=discord.utils.get(g.text_channels,name=str(ref))
            if x:return x
    def feeds(self):return [x for r in self.cfg.get("feed_channels",[]) if (x:=self.channel(r))]
    def competition_channel(self):return self.channel(self.cfg.get("competition_channel",DEFAULT_COMPETITION_CHANNEL))
    async def call(self,fn,label):
        delay=1
        for i in range(MAX_HTTP_RETRIES+1):
            try:return await fn()
            except discord.HTTPException as e:
                if i>=MAX_HTTP_RETRIES or e.status not in(429,500,502,503,504):raise
                await asyncio.sleep(min(float(getattr(e,"retry_after",delay)),30));delay=min(delay*2,30)
    async def ingest(self,embed,message_id=None,message_time=None):
        r=parse_embed(embed,message_id,message_time)
        if not r or r["mode"] not in [m.lower() for m in self.platform_modes.get(r["platform"],[])]:return None
        if r["kit"] not in valid_kits(r["mode"]):return None
        new=insert_submission(r,message_id)
        upsert_run(r)
        return r if new else None
    async def rebuild(self):
        async with self.lock:
            seen=new=0
            for ch in self.feeds():
                async for msg in ch.history(limit=None,oldest_first=False):
                    for e in msg.embeds:
                        r=await self.ingest(e,msg.id,msg.created_at)
                        if r:seen+=1;new+=1
            print(f"rescanned complete solo submissions ({new} new attempt records)")
            for p in PLATFORMS:await self.refresh_platform(p)
            await self.refresh_competitions()
    def lines(self,rows):
        out=[]
        for i,(n,k,s) in enumerate(rows,1):out.append(f"{ {1:':first_place:',2:':second_place:',3:':third_place:'}.get(i,f'{i}.')} **{n}** — stage {s}"+(f" ({kit_label(k)})" if k else ""))
        return out or ["No runs yet."]
    def board_embed(self,p,m,kind):
        e=discord.Embed(title=f"{m.capitalize()} — {PLATFORM_LABELS[p]} — {kind.capitalize()}",color=0x33AA66)
        if kind=="overall":e.add_field(name="Top Stages (all patterns/kits)",value="\n".join(self.lines(overall_board(p,m,self.top_n))),inline=False)
        elif kind=="mazepattern":
            for x in range(PATTERNS):e.add_field(name=f"Maze Pattern {x+1}",value="\n".join(self.lines(pattern_board(p,m,x,self.top_n))),inline=False)
        else:
            for x in range(PATTERNS):
                for k in KITS:e.add_field(name=f"Pattern {x+1} — {kit_label(k)}",value="\n".join(self.lines(kit_board(p,m,x,k,self.top_n))),inline=False)
        return e
    async def post_edit(self,key,ref,embed,label):
        ch=self.channel(ref)
        if not ch:return
        stored=get_board_msg(key);msg=None
        if stored:
            try:msg=await self.call(lambda:ch.fetch_message(int(stored[1])),label)
            except discord.NotFound:pass
        try:
            if msg:await self.call(lambda:msg.edit(embed=embed),label)
            else:
                msg=await self.call(lambda:ch.send(embed=embed),label);set_board_msg(key,ch.id,msg.id)
        except discord.HTTPException as e:print(f"board {label} failed: {e}")
    async def refresh_platform(self,p):
        chans=self.cfg.get("channels",{}).get(p,{})
        for m in self.platform_modes[p]:
            for kind in("overall","mazepattern","kits"):
                if chans.get(kind):await self.post_edit(f"{p}|{kind}|{m}",chans[kind],self.board_embed(p,m,kind),f"{p} {kind}")
    def comp_embed(self,c,archived=False):
        e=discord.Embed(title=f"{'ARCHIVED' if archived else 'CURRENT'} COMPETITION — {PLATFORM_LABELS[c['platform']]}",color=0xF1C40F)
        e.add_field(name="Challenge",value=f"**{c['mode'].capitalize()}**\nMaze Pattern **{c['pattern']+1}**\nKit **{kit_label(c['kit'])}**",inline=False)
        end=datetime.fromisoformat(c["end_ts"]).astimezone(self.tz);e.add_field(name="Competition",value=f"#{c['number']:03d} • {c['week_key']}\n{'Ended' if archived else 'Ends'} {discord.utils.format_dt(end,'F') if archived else discord.utils.format_dt(end,'R')}",inline=False)
        rows=competition_rows(c,self.top_n) if not(c["status"]=="archived" and c.get("standings_json")) else [(x["name"],x["stage"],x["time_ms"]) for x in json.loads(c["standings_json"])]
        e.add_field(name="Standings",value="\n".join(f"{i}. **{n}** — stage {s}" for i,(n,s,t) in enumerate(rows[:self.top_n],1)) if rows else "No qualifying runs yet.",inline=False)
        e.set_footer(text="Weekly standings use all verified solo attempts submitted during this competition week.");return e
    async def refresh_competitions(self):
        ch=self.competition_channel()
        if not ch:return
        for p in PLATFORMS:
            c=create_competition(p,self.cfg)
            if c and c.get("msg_id"):
                try:m=await self.call(lambda:ch.fetch_message(int(c["msg_id"])),f"competition {p}");await self.call(lambda:m.edit(embed=self.comp_embed(c)),f"competition {p}")
                except discord.NotFound:m=None
                except discord.HTTPException:continue
            else:
                m=await self.call(lambda:ch.send(embed=self.comp_embed(c)),f"post competition {p}");
                try:await self.call(lambda:m.pin(),f"pin competition {p}")
                except discord.HTTPException:pass
                c["channel_id"]=ch.id;c["msg_id"]=m.id;db().execute if False else None
                cx=db();cx.execute("UPDATE competitions SET channel_id=?,msg_id=? WHERE platform=? AND week_key=?",(str(ch.id),str(m.id),p,c["week_key"]));cx.commit();cx.close()
        cdb=db();rows=cdb.execute("SELECT platform,number,week_key,mode,pattern,kit,start_ts,end_ts,channel_id,msg_id,status,standings_json FROM competitions ORDER BY number DESC").fetchall();cdb.close()
        now=datetime.now(timezone.utc)
        for row in rows:
            c=competition_from_row(row)
            if c["status"]!="archived" and now>=datetime.fromisoformat(c["end_ts"]):
                c=archive_competition(c)
            if c.get("msg_id"):
                try:m=await self.call(lambda:ch.fetch_message(int(c["msg_id"])),"archive refresh");await self.call(lambda:m.edit(embed=self.comp_embed(c,c["status"]=="archived")),"archive refresh")
                except discord.HTTPException:pass
    async def on_ready(self):
        if self.ready_once:return
        self.ready_once=True;await self.rebuild();asyncio.create_task(self.scheduler())
    async def on_message(self,message):
        if message.author==self.user:return
        cmd=message.content.strip().lower()
        if cmd=="!rebuild":await message.channel.send("Rebuilding complete solo attempt history...");await self.rebuild();await message.channel.send("Done.");return
        if cmd=="!competition":await self.refresh_competitions();await message.channel.send("Weekly competitions refreshed.");return
        refs={str(x) for x in self.cfg.get("feed_channels",[])}
        if str(message.channel.id) not in refs and message.channel.name not in refs:return
        for e in message.embeds:
            r=await self.ingest(e,message.id,message.created_at)
            if r:await self.refresh_platform(r["platform"]);await self.refresh_competitions()
    async def scheduler(self):
        while True:
            try:
                await self.refresh_competitions();now=datetime.now(timezone.utc).astimezone(self.tz);n=(now-timedelta(days=now.weekday())+timedelta(days=7)).replace(hour=0,minute=0,second=0,microsecond=0);await asyncio.sleep(max(30,(n-now).total_seconds()+2))
            except asyncio.CancelledError:raise
            except Exception as e:print(f"competition scheduler failed: {e!r}");await asyncio.sleep(60)

def main():
    if not os.path.exists(CFG):print(f"Missing config.json: {CFG}");return
    cfg=load_config();MonsterBot(cfg).run(cfg["token"])
if __name__=="__main__":main()
