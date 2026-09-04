"""Discord tournament management layer for Monster Maze.

Extends the existing MonsterBot without changing its solo leaderboard ingest path.
"""
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


def ensure_discord_schema(c):
    competitive.ensure_schema(c)
    c.execute(f"""CREATE TABLE IF NOT EXISTS {ANNOUNCEMENT_TABLE}(
        tournament_id INTEGER PRIMARY KEY,
        channel_id TEXT,
        message_id TEXT,
        last_match_count INTEGER NOT NULL DEFAULT 0,
        last_status TEXT NOT NULL DEFAULT ''
    )""")
    c.commit()


def tournament_row(c, tid):
    return c.execute("""SELECT id,season_id,number,name,registration_start,registration_end,start_ts,status,bracket_size
        FROM tournaments WHERE id=?""", (tid,)).fetchone()


def player_names(c, tid):
    return dict(c.execute("SELECT lower(uuid),name FROM tournament_players WHERE tournament_id=?", (tid,)).fetchall())


def fmt_ts(ms):
    if ms is None:
        return "TBD"
    return discord.utils.format_dt(datetime.fromtimestamp(int(ms) / 1000, tz=timezone.utc), "F")


def fmt_relative(ms):
    if ms is None:
        return "TBD"
    return discord.utils.format_dt(datetime.fromtimestamp(int(ms) / 1000, tz=timezone.utc), "R")


def short_name(uuid, names):
    if not uuid:
        return "BYE"
    return names.get(uuid.lower(), uuid[:8])


def match_line(match, names):
    p1, p2 = short_name(match[3], names), short_name(match[4], names)
    score = f"{match[6]}–{match[7]}"
    status = match[9]
    icon = {"complete": "✅", "bye": "⏭️", "ready": "🎮", "active": "🎮"}.get(status, "⏳")
    return f"{icon} **{p1}** vs **{p2}** — {score} ({status})"


def bracket_embed(c, tid):
    row = tournament_row(c, tid)
    if not row:
        return None
    names = player_names(c, tid)
    matches = c.execute("""SELECT id,round_number,slot,player1_uuid,player2_uuid,best_of,
        player1_wins,player2_wins,winner_uuid,status FROM tournament_matches
        WHERE tournament_id=? ORDER BY round_number,slot""", (tid,)).fetchall()
    e = discord.Embed(title=f"🏆 Tournament #{row[2]:03d} — {row[3]}", color=0xF1C40F)
    e.add_field(name="Status", value=f"**{row[7].upper()}**\nStarts {fmt_relative(row[6])}", inline=False)
    e.add_field(name="Registration", value=f"Ends {fmt_ts(row[5])}\nStarts {fmt_ts(row[6])}", inline=False)
    if not matches:
        e.add_field(name="Bracket", value="Registration is open. No bracket has been generated yet.", inline=False)
        return e
    rounds = {}
    for m in matches:
        rounds.setdefault(m[1], []).append(m)
    final_round = int(row[8]).bit_length() - 1 if row[8] else max(rounds)
    third_round = final_round + 1
    for r in sorted(rounds):
        if r == third_round and int(row[8] or 0) >= 4:
            label = "🥉 Third Place"
        elif r == final_round:
            label = "🏆 Final"
        elif r == final_round - 1:
            label = "Semifinals" if final_round >= 2 else "Round 1"
        else:
            label = f"Round {r}"
        value = "\n".join(match_line(m, names) for m in rounds[r]) or "—"
        e.add_field(name=label, value=value[:1024], inline=False)
    e.set_footer(text="Best-of-3 • First to 2 wins advances")
    return e


class TournamentBot(MonsterBot):
    def __init__(self, cfg):
        super().__init__(cfg)
        self.tournament_task = None
        self.tournament_lock = asyncio.Lock()

    def admin(self, message):
        return bool(message.guild and message.author.guild_permissions.manage_guild)

    async def send_help(self, channel):
        await channel.send(
            "**Tournament commands**\n"
            "`!tournament` — show the current tournament\n"
            "`!tournament create <name> <registration-minutes> <start-minutes>` — create one (staff)\n"
            "`!tournament register <uuid> [name]` — register for the current tournament\n"
            "`!tournament unregister <uuid>` — remove a player before bracket generation (staff)\n"
            "`!tournament bracket` — refresh/show the current bracket\n"
            "`!tournament status [uuid]` — show a player's current match, or all active matches\n"
            "`!tournament results` — show the latest completed tournament"
        )

    async def announce_or_update(self, tid):
        ch = self.competition_channel()
        if not ch:
            return
        async with self.tournament_lock:
            c = db()
            try:
                ensure_discord_schema(c)
                row = tournament_row(c, tid)
                if not row:
                    return
                e = bracket_embed(c, tid)
                stored = c.execute(f"SELECT channel_id,message_id FROM {ANNOUNCEMENT_TABLE} WHERE tournament_id=?", (tid,)).fetchone()
            finally:
                c.close()
            msg = None
            if stored:
                try:
                    msg = await self.call(lambda: ch.fetch_message(int(stored[1])), "tournament announcement")
                except discord.NotFound:
                    pass
            if msg:
                try:
                    await self.call(lambda: msg.edit(embed=e), "tournament announcement")
                except discord.HTTPException:
                    return
            else:
                try:
                    msg = await self.call(lambda: ch.send(embed=e), "tournament announcement")
                    try:
                        await self.call(lambda: msg.pin(), "tournament announcement pin")
                    except discord.HTTPException:
                        pass
                except discord.HTTPException:
                    return
                c = db()
                try:
                    ensure_discord_schema(c)
                    c.execute(f"INSERT INTO {ANNOUNCEMENT_TABLE}(tournament_id,channel_id,message_id,last_match_count,last_status) VALUES(?,?,?,?,?) ON CONFLICT(tournament_id) DO UPDATE SET channel_id=excluded.channel_id,message_id=excluded.message_id", (tid, str(ch.id), str(msg.id), 0, row[7]))
                    c.commit()
                finally:
                    c.close()

    async def announce_match_results(self, tid):
        ch = self.competition_channel()
        if not ch:
            return
        c = db()
        try:
            ensure_discord_schema(c)
            row = tournament_row(c, tid)
            if not row:
                return
            names = player_names(c, tid)
            matches = c.execute("""SELECT id,round_number,slot,player1_uuid,player2_uuid,best_of,
                player1_wins,player2_wins,winner_uuid,status FROM tournament_matches
                WHERE tournament_id=? AND status='complete' ORDER BY completed_at,id""", (tid,)).fetchall()
            announced = c.execute(f"SELECT last_match_count FROM {ANNOUNCEMENT_TABLE} WHERE tournament_id=?", (tid,)).fetchone()
            count = int(announced[0]) if announced else 0
            fresh = matches[count:]
            if not fresh:
                return
            c.execute(f"UPDATE {ANNOUNCEMENT_TABLE} SET last_match_count=? WHERE tournament_id=?", (len(matches), tid))
            c.commit()
        finally:
            c.close()
        for m in fresh:
            winner = short_name(m[8], names)
            loser_uuid = m[4] if (m[8] or '').lower() == (m[3] or '').lower() else m[3]
            loser = short_name(loser_uuid, names)
            if row[8] and m[1] == int(row[8]).bit_length() - 1 and int(row[8]) >= 4:
                round_name = "Semifinal"
            elif row[8] and m[1] == int(row[8]).bit_length() and int(row[8]) >= 2:
                round_name = "Final"
            elif row[8] and m[1] == int(row[8]).bit_length() + 1 and int(row[8]) >= 4:
                round_name = "Third Place"
            else:
                round_name = f"Round {m[1]}"
            await ch.send(f"🏆 **Tournament #{row[2]:03d} — {row[3]}**\n**{round_name}:** {winner} defeats {loser} **{m[6]}–{m[7]}**")

    async def process_tournaments(self):
        c = db()
        to_announce = []
        try:
            ensure_discord_schema(c)
            season = competitive.ensure_current_season(c)
            sid = int(season[0])
            now = int(time.time() * 1000)
            rows = c.execute("SELECT id FROM tournaments WHERE season_id=? AND status!='complete' ORDER BY number", (sid,)).fetchall()
            for (tid,) in rows:
                row = tournament_row(c, tid)
                if row[7] == "registration" and row[6] is not None and now >= int(row[6]):
                    try:
                        tournament.build_bracket(c, int(tid))
                    except ValueError as exc:
                        print(f"tournament {tid} could not build bracket: {exc}", flush=True)
                row = tournament_row(c, tid)
                if row[7] == "bracket":
                    try:
                        tournament.finalize(c, int(tid))
                    except ValueError:
                        pass
                to_announce.append(int(tid))
            c.commit()
        finally:
            c.close()
        for tid in to_announce:
            await self.announce_match_results(tid)
            await self.announce_or_update(tid)

    async def tournament_scheduler(self):
        while True:
            try:
                await self.process_tournaments()
                await asyncio.sleep(POLL_SECONDS)
            except asyncio.CancelledError:
                raise
            except Exception as exc:
                print(f"tournament discord scheduler failed: {exc!r}", flush=True)
                await asyncio.sleep(POLL_SECONDS)

    async def on_ready(self):
        await super().on_ready()
        if self.tournament_task is None or self.tournament_task.done():
            self.tournament_task = asyncio.create_task(self.tournament_scheduler())

    async def tournament_command(self, message, args):
        if not args:
            c = db()
            try:
                competitive.ensure_schema(c)
                season = competitive.ensure_current_season(c)
                row = c.execute("SELECT id FROM tournaments WHERE season_id=? AND status!='complete' ORDER BY number DESC LIMIT 1", (int(season[0]),)).fetchone()
                if not row:
                    await message.channel.send("There is no active tournament right now.")
                    return
                e = bracket_embed(c, int(row[0]))
            finally:
                c.close()
            await message.channel.send(embed=e)
            return

        sub = args[0].lower()
        if sub in ("help", "?"):
            await self.send_help(message.channel)
            return

        if sub == "create":
            if not self.admin(message):
                await message.channel.send("You need **Manage Server** permission to create tournaments.")
                return
            if len(args) < 4:
                await message.channel.send("Usage: `!tournament create <name> <registration-minutes> <start-minutes>`")
                return
            try:
                reg_minutes = int(args[-2])
                start_minutes = int(args[-1])
            except ValueError:
                await message.channel.send("Registration and start delays must be whole minutes.")
                return
            name = " ".join(args[1:-2]).strip()[:100]
            if not name or reg_minutes < 1 or start_minutes < reg_minutes:
                await message.channel.send("Invalid tournament timing. Start must be at or after registration close.")
                return
            c = db()
            try:
                competitive.ensure_schema(c)
                season = competitive.ensure_current_season(c)
                sid = int(season[0])
                next_number = int(c.execute("SELECT COALESCE(MAX(number),0)+1 FROM tournaments WHERE season_id=?", (sid,)).fetchone()[0])
                now = int(time.time() * 1000)
                tid = tournament.create_tournament(c, sid, next_number, name, now, now + reg_minutes * 60000, now + start_minutes * 60000)
            finally:
                c.close()
            await message.channel.send(f"🏆 Created **Tournament #{next_number:03d} — {name}**. Registration is open for {reg_minutes} minutes; bracket starts {fmt_relative(now + start_minutes * 60000)}.")
            await self.announce_or_update(tid)
            return

        if sub == "register":
            if len(args) < 2:
                await message.channel.send("Usage: `!tournament register <uuid> [minecraft-name]`")
                return
            uuid = args[1].lower().strip()
            name = " ".join(args[2:]).strip() or message.author.display_name
            c = db()
            try:
                competitive.ensure_schema(c)
                season = competitive.ensure_current_season(c)
                row = c.execute("SELECT id FROM tournaments WHERE season_id=? AND status='registration' ORDER BY number DESC LIMIT 1", (int(season[0]),)).fetchone()
                if not row:
                    raise ValueError("there is no tournament open for registration")
                tid = int(row[0])
                tournament.register(c, tid, uuid, name)
            except ValueError as exc:
                await message.channel.send(f"❌ {exc}")
                return
            finally:
                c.close()
            await message.channel.send(f"✅ **{name}** is registered for Tournament #{tid}.")
            await self.announce_or_update(tid)
            return

        if sub == "unregister":
            if not self.admin(message):
                await message.channel.send("Unregister is currently staff-only.")
                return
            if len(args) < 2:
                await message.channel.send("Usage: `!tournament unregister <uuid>`")
                return
            uuid = args[1].lower()
            c = db()
            try:
                competitive.ensure_schema(c)
                season = competitive.ensure_current_season(c)
                row = c.execute("SELECT id FROM tournaments WHERE season_id=? AND status='registration' ORDER BY number DESC LIMIT 1", (int(season[0]),)).fetchone()
                if not row:
                    raise ValueError("no registration is open")
                c.execute("DELETE FROM tournament_players WHERE tournament_id=? AND lower(uuid)=?", (int(row[0]), uuid))
                c.commit()
                tid = int(row[0])
            finally:
                c.close()
            await message.channel.send(f"Removed `{uuid}` from Tournament #{tid}.")
            await self.announce_or_update(tid)
            return

        if sub == "bracket":
            await self.process_tournaments()
            c = db()
            try:
                competitive.ensure_schema(c)
                season = competitive.ensure_current_season(c)
                row = c.execute("SELECT id FROM tournaments WHERE season_id=? AND status!='complete' ORDER BY number DESC LIMIT 1", (int(season[0]),)).fetchone()
                if not row:
                    await message.channel.send("No active tournament.")
                    return
                e = bracket_embed(c, int(row[0]))
            finally:
                c.close()
            await message.channel.send(embed=e)
            return

        if sub == "status":
            uuid = args[1].lower() if len(args) >= 2 else None
            c = db()
            try:
                competitive.ensure_schema(c)
                season = competitive.ensure_current_season(c)
                rows = c.execute("SELECT id,number,name FROM tournaments WHERE season_id=? AND status='bracket' ORDER BY number DESC", (int(season[0]),)).fetchall()
                if not rows:
                    await message.channel.send("No tournament bracket is currently active.")
                    return
                tid = int(rows[0][0])
                if uuid:
                    match = tournament.current_match(c, tid, uuid)
                    if not match:
                        await message.channel.send("That player has no active tournament match.")
                        return
                    names = player_names(c, tid)
                    await message.channel.send(f"🎮 **{short_name(uuid, names)}** is in **Round {match['round']}**: **{short_name(match['player1'], names)}** vs **{short_name(match['player2'], names)}** — **{match['player1Wins']}–{match['player2Wins']}** ({match['status']}).")
                else:
                    matches = c.execute("SELECT id,round_number,slot,player1_uuid,player2_uuid,best_of,player1_wins,player2_wins,winner_uuid,status FROM tournament_matches WHERE tournament_id=? AND status IN ('ready','active') ORDER BY round_number,slot", (tid,)).fetchall()
                    names = player_names(c, tid)
                    text = "\n".join(match_line(m, names) for m in matches) or "No matches are currently ready."
                    await message.channel.send(f"🎮 **Active matches — Tournament #{rows[0][1]:03d}**\n{text}")
            finally:
                c.close()
            return

        if sub == "results":
            c = db()
            try:
                competitive.ensure_schema(c)
                season = competitive.ensure_current_season(c)
                row = c.execute("SELECT id,number,name FROM tournaments WHERE season_id=? AND status='complete' ORDER BY number DESC LIMIT 1", (int(season[0]),)).fetchone()
                if not row:
                    await message.channel.send("No completed tournament this season yet.")
                    return
                players = c.execute("SELECT name,placement,points FROM tournament_players WHERE tournament_id=? AND placement IS NOT NULL ORDER BY placement", (int(row[0]),)).fetchall()
            finally:
                c.close()
            text = "\n".join(f"**{p}.** {n} — {pts} tournament points" for n,p,pts in players)
            await message.channel.send(f"🏆 **Tournament #{row[1]:03d} — {row[2]} Results**\n{text}")
            return

        await self.send_help(message.channel)

    async def on_message(self, message):
        if message.author == self.user:
            return
        content = message.content.strip()
        if content.lower() == PREFIX:
            await self.tournament_command(message, [])
            return
        if content.lower().startswith(PREFIX + " "):
            args = content[len(PREFIX):].strip().split()
            await self.tournament_command(message, args)
            return
        await super().on_message(message)


def main():
    cfg = load_config()
    TournamentBot(cfg).run(cfg["token"])


if __name__ == "__main__":
    main()
