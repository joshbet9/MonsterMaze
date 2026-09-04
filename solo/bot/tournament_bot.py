"""Discord tournament management for Monster Maze."""
import asyncio
import time
from datetime import datetime, timezone

import discord
import competitive
import tournament
from monster_bot_v2 import MonsterBot, load_config, db

PREFIX = "!tournament"
POLL_SECONDS = 30
ANNOUNCEMENT_TABLE = "tournament_discord_announcements"


def schema(c):
    competitive.ensure_schema(c)
    c.execute(f"""CREATE TABLE IF NOT EXISTS {ANNOUNCEMENT_TABLE}(
        tournament_id INTEGER PRIMARY KEY, channel_id TEXT, message_id TEXT,
        announced_matches INTEGER NOT NULL DEFAULT 0)""")
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


class TournamentBot(MonsterBot):
    def __init__(self, cfg):
        super().__init__(cfg)
        self.tournament_task = None
        self.tournament_lock = asyncio.Lock()

    @staticmethod
    def staff(message):
        return bool(message.guild and message.author.guild_permissions.manage_guild)

    async def announce(self, tid):
        channel = self.competition_channel()
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
        channel = self.competition_channel()
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

    async def tournament_scheduler(self):
        while True:
            try:
                await self.process_tournaments(); await asyncio.sleep(POLL_SECONDS)
            except asyncio.CancelledError: raise
            except Exception as exc:
                print(f"tournament scheduler failed: {exc!r}", flush=True); await asyncio.sleep(POLL_SECONDS)

    async def on_ready(self):
        await super().on_ready()
        if self.tournament_task is None or self.tournament_task.done():
            self.tournament_task = asyncio.create_task(self.tournament_scheduler())

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
        await super().on_message(message)


def main():
    cfg = load_config(); TournamentBot(cfg).run(cfg["token"])


if __name__ == "__main__": main()
