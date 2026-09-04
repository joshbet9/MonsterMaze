"""Discord tournament management and competitive ranking boards for Monster Maze."""
import asyncio
import time
from datetime import datetime, timezone

import discord
import competitive
import tournament
from monster_bot_v2 import MonsterBot, load_config, db

PREFIX = "!tournament"
SEASON_PREFIX = "!season"
POLL_SECONDS = 30
ANNOUNCEMENT_TABLE = "tournament_discord_announcements"
ARCHIVE_TABLE = "season_discord_archives"


def schema(c):
    competitive.ensure_schema(c)
    c.execute(f"""CREATE TABLE IF NOT EXISTS {ANNOUNCEMENT_TABLE}(
        tournament_id INTEGER PRIMARY KEY, channel_id TEXT, message_id TEXT,
        announced_matches INTEGER NOT NULL DEFAULT 0)""")
    c.execute(f"""CREATE TABLE IF NOT EXISTS {ARCHIVE_TABLE}(
        season_id INTEGER PRIMARY KEY, channel_id TEXT NOT NULL, message_id TEXT NOT NULL,
        published_at INTEGER NOT NULL)""")
    c.commit()


def trow(c, tid):
    return c.execute("SELECT id,season_id,number,name,registration_start,registration_end,start_ts,status,bracket_size FROM tournaments WHERE id=?", (tid,)).fetchone()


def player_names(c, tid):
    return dict(c.execute("SELECT lower(uuid),name FROM tournament_players WHERE tournament_id=?", (tid,)).fetchall())


def pname(uuid, names):
    return "BYE" if not uuid else names.get(uuid.lower(), uuid[:8])


def dt(ms, style="F"):
    return discord.utils.format_dt(datetime.fromtimestamp(int(ms) / 1000, tz=timezone.utc), style)


def bracket_embed(c, tid):
    t = trow(c, tid)
    if not t:
        return None
    names = player_names(c, tid)
    matches = c.execute("SELECT id,round_number,slot,player1_uuid,player2_uuid,best_of,player1_wins,player2_wins,winner_uuid,status FROM tournament_matches WHERE tournament_id=? ORDER BY round_number,slot", (tid,)).fetchall()
    e = discord.Embed(title=f"🏆 Tournament #{t[2]:03d} — {t[3]}", color=0xF1C40F)
    e.add_field(name="Status", value=f"**{t[7].upper()}**\nStarts {dt(t[6], 'R')}", inline=False)
    e.add_field(name="Registration", value=f"Closes {dt(t[5])}\nStarts {dt(t[6])}", inline=False)
    if not matches:
        count = c.execute("SELECT COUNT(*) FROM tournament_players WHERE tournament_id=?", (tid,)).fetchone()[0]
        e.add_field(name="Players", value=str(count), inline=False)
        e.set_footer(text="Registration is open • Bracket is generated automatically at start time")
        return e
    rounds = {}
    for m in matches:
        rounds.setdefault(m[1], []).append(m)
    final_round = int(t[8]).bit_length() - 1 if t[8] else max(rounds)
    for r in sorted(rounds):
        if r == final_round:
            label = "🏆 Final"
        elif r == final_round + 1 and int(t[8] or 0) >= 4:
            label = "🥉 Third Place"
        elif r == final_round - 1:
            label = "Semifinals"
        else:
            label = f"Round {r}"
        lines = []
        for m in rounds[r]:
            icon = {"complete":"✅", "bye":"⏭️", "ready":"🎮", "active":"🎮"}.get(m[9], "⏳")
            lines.append(f"{icon} **{pname(m[3],names)}** vs **{pname(m[4],names)}** — {m[6]}–{m[7]} ({m[9]})")
        e.add_field(name=label, value="\n".join(lines)[:1024] or "—", inline=False)
    e.set_footer(text="Best-of-3 • First to 2 wins advances")
    return e


def season_archive_channel_ref(cfg):
    return cfg.get("competitive_channels", {}).get("season_archive", "season-archive")


def _archive_rows(c, sid, column, limit):
    return c.execute(f"""SELECT name,{column} FROM season_players
        WHERE season_id=? ORDER BY {column} DESC,name ASC LIMIT ?""", (sid, limit)).fetchall()


def _archive_lines(rows, integer=False):
    if integer:
        return [f"{i}. **{name or 'Unknown'}** — {int(value)} pts" for i,(name,value) in enumerate(rows, 1)]
    return [f"{i}. **{name or 'Unknown'}** — {float(value):.1f}" for i,(name,value) in enumerate(rows, 1)]


def season_archive_embed(c, sid, top_n=10):
    season = c.execute("SELECT id,season_number,start_ts,end_ts,status,finalized_at FROM seasons WHERE id=?", (sid,)).fetchone()
    if not season or season[4] != "archived":
        return None

    mmcl = _archive_rows(c, sid, "mmcl", top_n)
    elo = _archive_rows(c, sid, "elo", top_n)
    weekly = _archive_rows(c, sid, "weekly_points", top_n)
    tournament_points = _archive_rows(c, sid, "tournament_points", top_n)

    e = discord.Embed(
        title=f"📜 Season {int(season[1])} — Archived",
        description=f"{dt(int(datetime.fromisoformat(season[2]).timestamp() * 1000))} → {dt(int(datetime.fromisoformat(season[3]).timestamp() * 1000))}",
        color=0x9B59B6,
    )

    champion = mmcl[0] if mmcl else None
    if champion:
        e.add_field(name="👑 Season Champion", value=f"**{champion[0] or 'Unknown'}** — {float(champion[1]):.1f} MMCL", inline=False)
    else:
        e.add_field(name="👑 Season Champion", value="No ranked players.", inline=False)

    e.add_field(name="MMCL — Final", value="\n".join(_archive_lines(mmcl)) or "No results.", inline=False)
    e.add_field(name="ELO — Final", value="\n".join(_archive_lines(elo)) or "No results.", inline=False)
    e.add_field(name="Weekly — Final", value="\n".join(_archive_lines(weekly, True)) or "No results.", inline=False)
    e.add_field(name="Tournament — Final", value="\n".join(_archive_lines(tournament_points, True)) or "No results.", inline=False)

    tournaments = c.execute("""SELECT id,number,name,status FROM tournaments
        WHERE season_id=? ORDER BY number""", (sid,)).fetchall()
    if tournaments:
        lines = []
        for tid, number, name, status in tournaments:
            placements = c.execute("""SELECT placement,name,points FROM tournament_players
                WHERE tournament_id=? AND placement IS NOT NULL ORDER BY placement ASC LIMIT 4""", (tid,)).fetchall()
            if placements:
                winners = " • ".join(f"{int(p)}. {n or 'Unknown'} ({int(pts)} pts)" for p,n,pts in placements)
            else:
                winners = "No recorded placements"
            lines.append(f"**#{int(number):03d} — {name}** ({status})\n{winners}")
        e.add_field(name="🏆 Season Tournaments", value="\n\n".join(lines)[:1024], inline=False)
    else:
        e.add_field(name="🏆 Season Tournaments", value="No tournaments recorded.", inline=False)

    e.set_footer(text="Historical season record • MMCL = ELO 40% + Weekly 30% + Tournament 30%")
    return e


class TournamentBot(MonsterBot):
    def __init__(self, cfg):
        super().__init__(cfg)
        self.tournament_task = None
        self.tournament_lock = asyncio.Lock()

    @staticmethod
    def staff(message):
        return bool(message.guild and message.author.guild_permissions.manage_guild)

    def competitive_channel_ref(self, kind):
        return self.cfg.get("competitive_channels", {}).get(kind)

    def competitive_embed(self, kind):
        c = db()
        try:
            competitive.ensure_schema(c)
            sid = int(competitive.ensure_current_season(c)[0])
            season = c.execute("SELECT season_number FROM seasons WHERE id=?", (sid,)).fetchone()
            season_no = int(season[0]) if season else sid
            limit = self.top_n
            if kind == "mmr":
                rows = c.execute("SELECT name,mmr FROM permanent_ratings ORDER BY mmr DESC,name ASC LIMIT ?", (limit,)).fetchall()
                title = "MMR — All-Time"
                footer = "MMR is permanent and is calculated from all kit PBs normalized against each kit leaderboard best."
                lines = [f"{i}. **{n or 'Unknown'}** — {float(v):.1f}" for i,(n,v) in enumerate(rows,1)]
            else:
                cols = {"elo":"elo", "weekly":"weekly_points", "tournament":"tournament_points", "mmcl":"mmcl"}
                col = cols[kind]
                rows = c.execute(f"SELECT name,{col},elo,weekly_points,tournament_points,elo_component,weekly_component,tournament_component FROM season_players WHERE season_id=? ORDER BY {col} DESC,name ASC LIMIT ?", (sid,limit)).fetchall()
                labels = {"mmcl":"MMCL Score", "elo":"ELO", "weekly":"Weekly Score", "tournament":"Tournament Points"}
                title = f"{labels[kind]} — Season {season_no}"
                if kind == "mmcl":
                    lines = [f"{i}. **{r[0] or 'Unknown'}** — **{float(r[1]):.1f}**  *(ELO {float(r[5]):.1f} • Weekly {float(r[6]):.1f} • Tournament {float(r[7]):.1f})*" for i,r in enumerate(rows,1)]
                    footer = "MMCL = ELO 40% + Weekly 30% + Tournament 30%. Components reset each season."
                elif kind == "elo":
                    lines = [f"{i}. **{r[0] or 'Unknown'}** — {float(r[1]):.1f}" for i,r in enumerate(rows,1)]
                    footer = "Season ELO rating. Resets at the start of each season."
                elif kind == "weekly":
                    lines = [f"{i}. **{r[0] or 'Unknown'}** — {int(r[1])} pts" for i,r in enumerate(rows,1)]
                    footer = "Current-season weekly competitive score."
                else:
                    lines = [f"{i}. **{r[0] or 'Unknown'}** — {int(r[1])} pts" for i,r in enumerate(rows,1)]
                    footer = "Current-season tournament points from completed tournaments."
            e = discord.Embed(title=title, color=0xF1C40F)
            e.add_field(name="Rankings", value="\n".join(lines) if lines else "No ranked players yet.", inline=False)
            e.set_footer(text=footer)
            return e
        finally:
            c.close()

    async def refresh_competitive_rankings(self):
        for kind in ("mmcl", "mmr", "elo", "weekly", "tournament"):
            ref = self.competitive_channel_ref(kind)
            if ref:
                await self.post_edit(f"competitive|{kind}", ref, self.competitive_embed(kind), f"competitive {kind}")

    async def publish_season_archives(self):
        ref = season_archive_channel_ref(self.cfg)
        channel = self.channel(ref)
        if not channel:
            return
        c = db()
        try:
            schema(c)
            seasons = c.execute("SELECT id,season_number FROM seasons WHERE status='archived' ORDER BY season_number ASC").fetchall()
            pending = []
            for sid, number in seasons:
                exists = c.execute(f"SELECT 1 FROM {ARCHIVE_TABLE} WHERE season_id=?", (int(sid),)).fetchone()
                if not exists:
                    pending.append((int(sid), int(number)))
        finally:
            c.close()

        for sid, number in pending:
            c = db()
            try:
                schema(c)
                embed = season_archive_embed(c, sid, self.top_n)
                if not embed:
                    continue
            finally:
                c.close()
            try:
                msg = await self.call(lambda: channel.send(embed=embed), f"season {number} archive")
                try:
                    await self.call(lambda: msg.pin(), f"season {number} archive pin")
                except discord.HTTPException:
                    pass
                c = db()
                try:
                    schema(c)
                    c.execute(f"INSERT OR IGNORE INTO {ARCHIVE_TABLE}(season_id,channel_id,message_id,published_at) VALUES(?,?,?,?)", (sid, str(channel.id), str(msg.id), int(time.time()*1000)))
                    c.commit()
                finally:
                    c.close()
            except discord.HTTPException as exc:
                print(f"season {number} archive publish failed: {exc}", flush=True)

    async def announce(self, tid):
        channel = self.channel(
            self.competitive_channel_ref("tournament_registration")
            or "tournament-registration"
        )
        if not channel:
            return
        async with self.tournament_lock:
            c = db()
            try:
                schema(c); t = trow(c, tid)
                if not t:
                    return
                embed = bracket_embed(c, tid)
                stored = c.execute(f"SELECT channel_id,message_id FROM {ANNOUNCEMENT_TABLE} WHERE tournament_id=?", (tid,)).fetchone()
            finally:
                c.close()
            msg = None
            if stored:
                try:
                    msg = await self.call(lambda: channel.fetch_message(int(stored[1])), "tournament announcement")
                except discord.NotFound:
                    pass
            try:
                if msg:
                    await self.call(lambda: msg.edit(embed=embed), "tournament announcement")
                else:
                    msg = await self.call(lambda: channel.send(embed=embed), "tournament announcement")
                    try:
                        await self.call(lambda: msg.pin(), "tournament announcement pin")
                    except discord.HTTPException:
                        pass
                    c = db()
                    try:
                        schema(c)
                        c.execute(f"INSERT INTO {ANNOUNCEMENT_TABLE}(tournament_id,channel_id,message_id) VALUES(?,?,?) ON CONFLICT(tournament_id) DO UPDATE SET channel_id=excluded.channel_id,message_id=excluded.message_id", (tid, str(channel.id), str(msg.id)))
                        c.commit()
                    finally:
                        c.close()
            except discord.HTTPException as exc:
                print(f"tournament announcement failed: {exc}", flush=True)

    async def announce_results(self, tid):
        channel = self.channel(
            self.competitive_channel_ref("tournament_results")
            or "tournament-results"
        )
        if not channel:
            return
        c = db()
        try:
            schema(c); t = trow(c, tid)
            if not t:
                return
            names = player_names(c, tid)
            matches = c.execute("SELECT id,round_number,player1_uuid,player2_uuid,player1_wins,player2_wins,winner_uuid FROM tournament_matches WHERE tournament_id=? AND status='complete' ORDER BY completed_at,id", (tid,)).fetchall()
            old = c.execute(f"SELECT announced_matches FROM {ANNOUNCEMENT_TABLE} WHERE tournament_id=?", (tid,)).fetchone()
            start = int(old[0]) if old else 0
            fresh = matches[start:]
            if not fresh:
                return
            c.execute(f"UPDATE {ANNOUNCEMENT_TABLE} SET announced_matches=? WHERE tournament_id=?", (len(matches), tid)); c.commit()
        finally:
            c.close()
        for m in fresh:
            winner = pname(m[6], names)
            loser = pname(m[3] if (m[6] or '').lower() == (m[2] or '').lower() else m[2], names)
            await channel.send(f"🏆 **Tournament #{t[2]:03d} — {t[3]}** — {winner} defeats {loser} **{m[4]}–{m[5]}**")

    async def process_tournaments(self):
        c = db(); ids = []
        try:
            schema(c); season = competitive.ensure_current_season(c); sid = int(season[0]); now = int(time.time()*1000)
            ids = [int(x[0]) for x in c.execute("SELECT id FROM tournaments WHERE season_id=? AND status!='complete' ORDER BY number", (sid,)).fetchall()]
            for tid in ids:
                t = trow(c, tid)
                if t[7] == "registration" and t[6] is not None and now >= int(t[6]):
                    try: tournament.build_bracket(c, tid)
                    except ValueError as exc: print(f"tournament {tid} bracket not ready: {exc}", flush=True)
                t = trow(c, tid)
                if t and t[7] == "bracket":
                    try: tournament.finalize(c, tid)
                    except ValueError: pass
        finally:
            c.close()
        for tid in ids:
            await self.announce_results(tid)
            await self.announce(tid)
        await self.publish_season_archives()
        await self.refresh_competitive_rankings()

    async def tournament_scheduler(self):
        while True:
            try:
                await self.process_tournaments(); await asyncio.sleep(POLL_SECONDS)
            except asyncio.CancelledError: raise
            except Exception as exc:
                print(f"tournament scheduler failed: {exc!r}", flush=True); await asyncio.sleep(POLL_SECONDS)

    async def on_ready(self):
        await super().on_ready()
        await self.refresh_competitive_rankings()
        await self.publish_season_archives()
        if self.tournament_task is None or self.tournament_task.done():
            self.tournament_task = asyncio.create_task(self.tournament_scheduler())

    async def season_command(self, message, args):
        c = db()
        try:
            schema(c)
            if not args or args[0].lower() == "current":
                season = competitive.ensure_current_season(c)
                sid = int(season[0])
                row = c.execute("SELECT season_number,start_ts,end_ts,status FROM seasons WHERE id=?", (sid,)).fetchone()
                await message.channel.send(f"📅 **Season {int(row[0])}** — {row[3].upper()} — {dt(int(datetime.fromisoformat(row[1]).timestamp()*1000))} → {dt(int(datetime.fromisoformat(row[2]).timestamp()*1000))}")
                return
            if args[0].lower() in ("archive", "history"):
                if len(args) > 1:
                    number = int(args[1])
                    row = c.execute("SELECT id FROM seasons WHERE season_number=? AND status='archived'", (number,)).fetchone()
                else:
                    row = c.execute("SELECT id FROM seasons WHERE status='archived' ORDER BY season_number DESC LIMIT 1").fetchone()
                if not row:
                    await message.channel.send("No archived season was found.")
                    return
                embed = season_archive_embed(c, int(row[0]), self.top_n)
            else:
                await message.channel.send("Usage: `!season` • `!season archive [season-number]`")
                return
        except (ValueError, TypeError):
            await message.channel.send("❌ Season number must be a whole number.")
            return
        finally:
            c.close()
        if embed:
            await message.channel.send(embed=embed)

    async def command(self, message, args):
        if not args:
            c = db()
            try:
                competitive.ensure_schema(c); season = competitive.ensure_current_season(c)
                x = c.execute("SELECT id FROM tournaments WHERE season_id=? AND status!='complete' ORDER BY number DESC LIMIT 1", (int(season[0]),)).fetchone()
                embed = bracket_embed(c, int(x[0])) if x else None
            finally: c.close()
            await message.channel.send(embed=embed) if embed else await message.channel.send("There is no active tournament.")
            return
        sub = args[0].lower()
        if sub in ("help", "?"):
            await message.channel.send("`!tournament` • `create <name> <registration-minutes> <start-minutes>` (staff) • `register <uuid> [name]` • `bracket` • `status [uuid]` • `results`"); return
        if sub == "create":
            if not self.staff(message): await message.channel.send("❌ Manage Server permission required."); return
            if len(args) < 4: await message.channel.send("Usage: `!tournament create <name> <registration-minutes> <start-minutes>`"); return
            try: reg, start = int(args[-2]), int(args[-1])
            except ValueError: await message.channel.send("❌ Timings must be whole minutes."); return
            name = " ".join(args[1:-2]).strip()[:100]
            if not name or reg < 1 or start < reg: await message.channel.send("❌ Start must be at or after registration close."); return
            c = db()
            try:
                competitive.ensure_schema(c); sid = int(competitive.ensure_current_season(c)[0]); number = int(c.execute("SELECT COALESCE(MAX(number),0)+1 FROM tournaments WHERE season_id=?", (sid,)).fetchone()[0]); now = int(time.time()*1000); tid = tournament.create_tournament(c, sid, number, name, now, now+reg*60000, now+start*60000)
            finally: c.close()
            await message.channel.send(f"🏆 Created **Tournament #{number:03d} — {name}**. Registration closes {dt(now+reg*60000,'R')}."); await self.announce(tid); return
        if sub == "register":
            if len(args) < 2: await message.channel.send("Usage: `!tournament register <uuid> [minecraft-name]`"); return
            uuid, name = args[1].lower().strip(), (" ".join(args[2:]).strip() or message.author.display_name); c = db()
            try:
                competitive.ensure_schema(c); sid = int(competitive.ensure_current_season(c)[0]); x = c.execute("SELECT id FROM tournaments WHERE season_id=? AND status='registration' ORDER BY number DESC LIMIT 1", (sid,)).fetchone()
                if not x: raise ValueError("there is no tournament open for registration")
                tid = int(x[0]); tournament.register(c, tid, uuid, name)
            except ValueError as exc: await message.channel.send(f"❌ {exc}"); return
            finally: c.close()
            await message.channel.send(f"✅ **{name}** registered for Tournament #{tid}."); await self.announce(tid); return
        if sub == "bracket":
            await self.process_tournaments(); c = db()
            try:
                competitive.ensure_schema(c); sid = int(competitive.ensure_current_season(c)[0]); x = c.execute("SELECT id FROM tournaments WHERE season_id=? AND status!='complete' ORDER BY number DESC LIMIT 1", (sid,)).fetchone(); embed = bracket_embed(c, int(x[0])) if x else None
            finally: c.close()
            await message.channel.send(embed=embed) if embed else await message.channel.send("No active tournament."); return
        if sub == "status":
            uuid = args[1].lower() if len(args)>1 else None; c = db()
            try:
                competitive.ensure_schema(c); sid = int(competitive.ensure_current_season(c)[0]); x = c.execute("SELECT id,number FROM tournaments WHERE season_id=? AND status='bracket' ORDER BY number DESC LIMIT 1", (sid,)).fetchone()
                if not x: await message.channel.send("No tournament bracket is currently active."); return
                tid, number = int(x[0]), int(x[1]); ps = player_names(c, tid)
                if uuid:
                    m = tournament.current_match(c, tid, uuid)
                    if not m: await message.channel.send("That player has no active tournament match."); return
                    await message.channel.send(f"🎮 **{pname(uuid,ps)}** — Round {m['round']}: **{pname(m['player1'],ps)}** vs **{pname(m['player2'],ps)}**, score **{m['player1Wins']}–{m['player2Wins']}** ({m['status']}).")
                else:
                    ms = c.execute("SELECT player1_uuid,player2_uuid,player1_wins,player2_wins,status FROM tournament_matches WHERE tournament_id=? AND status IN ('ready','active') ORDER BY round_number,slot", (tid,)).fetchall(); text = "\n".join(f"**{pname(a,ps)}** vs **{pname(b,ps)}** — {wa}–{wb} ({s})" for a,b,wa,wb,s in ms) or "None."
                    await message.channel.send(f"🎮 **Active matches — Tournament #{number:03d}**\n{text}")
            finally: c.close()
            return
        if sub == "results":
            c = db()
            try:
                competitive.ensure_schema(c); sid = int(competitive.ensure_current_season(c)[0]); x = c.execute("SELECT id,number,name FROM tournaments WHERE season_id=? AND status='complete' ORDER BY number DESC LIMIT 1", (sid,)).fetchone()
                if not x: await message.channel.send("No completed tournament this season."); return
                ps = c.execute("SELECT placement,name,points FROM tournament_players WHERE tournament_id=? AND placement IS NOT NULL ORDER BY placement", (int(x[0]),)).fetchall()
            finally: c.close()
            await message.channel.send(f"🏆 **Tournament #{x[1]:03d} — {x[2]} Results**\n" + "\n".join(f"**{p}.** {n} — {pts} points" for p,n,pts in ps)); return
        await message.channel.send("Unknown tournament command. Use `!tournament help`.")

    async def on_message(self, message):
        if message.author == self.user: return
        text = message.content.strip()
        if text.lower() == PREFIX: await self.command(message, []); return
        if text.lower().startswith(PREFIX + " "): await self.command(message, text[len(PREFIX):].strip().split()); return
        if text.lower() == SEASON_PREFIX: await self.season_command(message, []); return
        if text.lower().startswith(SEASON_PREFIX + " "): await self.season_command(message, text[len(SEASON_PREFIX):].strip().split()); return
        await super().on_message(message)


def main():
    cfg = load_config(); TournamentBot(cfg).run(cfg["token"])


if __name__ == "__main__": main()
