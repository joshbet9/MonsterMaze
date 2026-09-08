"""Persistent Discord #server-status updater."""
import asyncio
import json
import sqlite3
import urllib.request

import discord
import monster_bot_v2 as base

STATUS_KEY = "server-status"
STATUS_CHANNEL_DEFAULT = "server-status"
STATUS_URL_DEFAULT = "http://127.0.0.1:8765/status"
STATUS_INTERVAL_DEFAULT = 15
STATUS_NOTIFY_USER_ID_DEFAULT = 191446385961336832
STATUS_NOTIFY_STATES = {"offline", "starting", "online"}


def status_text(data):
    rows = []
    for name, label in (("1.8", "MM18"), ("1.21", "MM21")):
        server = data.get("servers", {}).get(name, {})
        state = server.get("state")
        if state == "online":
            players = int(server.get("players", 0) or 0)
            suffix = f"{players} player" if players == 1 else f"{players} players"
            rows.append(f"🟢 {label}     {suffix}")
        elif state == "offline":
            rows.append(f"🔴 {label}     Offline")
        else:
            rows.append(f"🟡 {label}     Starting...")
    return "\n".join(rows)


def status_states(data):
    return {
        name: data.get("servers", {}).get(name, {}).get("state")
        for name in ("1.8", "1.21")
    }


def get_previous_states():
    conn = base.db()
    conn.execute(
        "CREATE TABLE IF NOT EXISTS server_status_state "
        "(server TEXT PRIMARY KEY, state TEXT NOT NULL)"
    )
    rows = conn.execute("SELECT server, state FROM server_status_state").fetchall()
    conn.commit()
    conn.close()
    return dict(rows)


def set_state(server, state):
    conn = base.db()
    conn.execute(
        "CREATE TABLE IF NOT EXISTS server_status_state "
        "(server TEXT PRIMARY KEY, state TEXT NOT NULL)"
    )
    conn.execute(
        "INSERT INTO server_status_state(server, state) VALUES(?, ?) "
        "ON CONFLICT(server) DO UPDATE SET state=excluded.state",
        (server, state),
    )
    conn.commit()
    conn.close()


async def fetch_status(url):
    def request():
        req = urllib.request.Request(url, headers={"User-Agent": "MonsterMaze-DiscordStatus/1"})
        with urllib.request.urlopen(req, timeout=10) as response:
            return json.loads(response.read().decode("utf-8"))
    return await asyncio.to_thread(request)


async def notify_state_change(bot, server, old_state, new_state):
    user_id = STATUS_NOTIFY_USER_ID_DEFAULT
    try:
        user = await bot.call(lambda: bot.fetch_user(user_id), "server status notification user")
        if new_state == "starting":
            text = f"{user.mention} 🟡 MonsterMaze {server} is starting."
        elif new_state == "online":
            text = f"{user.mention} 🟢 MonsterMaze {server} is online."
        elif new_state == "offline":
            text = f"{user.mention} 🔴 MonsterMaze {server} has stopped."
        else:
            return
        await bot.call(lambda: user.send(text), "server status notification")
        print(f"[server-status] notified user {user_id}: {server} {old_state} -> {new_state}", flush=True)
    except discord.HTTPException as exc:
        print(f"[server-status] notification failed for {server}: {exc}", flush=True)


async def update(bot, cfg):
    ref = cfg.get("server_status_channel", STATUS_CHANNEL_DEFAULT)
    channel = bot.channel(ref)
    if channel is None:
        print(f"[server-status] channel not found: {ref}", flush=True)
        return

    url = cfg.get("server_status_url", STATUS_URL_DEFAULT)
    try:
        data = await fetch_status(url)
        content = status_text(data)
    except Exception as exc:
        print(f"[server-status] status API failed: {exc}", flush=True)
        return

    previous_states = get_previous_states()
    current_states = status_states(data)
    for server, new_state in current_states.items():
        old_state = previous_states.get(server)
        if new_state in STATUS_NOTIFY_STATES:
            if old_state is not None and old_state != new_state:
                await notify_state_change(bot, server, old_state, new_state)
            set_state(server, new_state)

    stored = base.get_board_msg(STATUS_KEY)
    message = None
    if stored:
        try:
            message = await bot.call(lambda: channel.fetch_message(int(stored[1])), "server status")
        except discord.NotFound:
            message = None

    try:
        if message:
            if message.content != content:
                await bot.call(lambda: message.edit(content=content), "server status")
        else:
            message = await bot.call(lambda: channel.send(content=content), "server status")
            base.set_board_msg(STATUS_KEY, channel.id, message.id)
    except discord.HTTPException as exc:
        print(f"[server-status] Discord update failed: {exc}", flush=True)


async def run(bot, cfg):
    while True:
        try:
            await update(bot, cfg)
        except asyncio.CancelledError:
            raise
        except Exception as exc:
            print(f"[server-status] updater failed: {exc!r}", flush=True)
        await asyncio.sleep(max(10, int(cfg.get("server_status_interval", STATUS_INTERVAL_DEFAULT))))
