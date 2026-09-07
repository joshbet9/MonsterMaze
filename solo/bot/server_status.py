"""Persistent Discord #server-status updater."""
import asyncio
import json
import urllib.request

import discord

STATUS_KEY = "server-status"
STATUS_CHANNEL_DEFAULT = "server-status"
STATUS_URL_DEFAULT = "http://127.0.0.1:8765/status"
STATUS_INTERVAL_DEFAULT = 15


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


async def fetch_status(url):
    def request():
        req = urllib.request.Request(url, headers={"User-Agent": "MonsterMaze-DiscordStatus/1"})
        with urllib.request.urlopen(req, timeout=10) as response:
            return json.loads(response.read().decode("utf-8"))
    return await asyncio.to_thread(request)


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

    stored = base_get_board_msg(bot, STATUS_KEY)
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
            base_set_board_msg(bot, STATUS_KEY, channel.id, message.id)
    except discord.HTTPException as exc:
        print(f"[server-status] Discord update failed: {exc}", flush=True)


def base_get_board_msg(bot, key):
    return bot.get_board_msg(key)


def base_set_board_msg(bot, key, channel_id, message_id):
    bot.set_board_msg(key, channel_id, message_id)


async def run(bot, cfg):
    while True:
        try:
            await update(bot, cfg)
        except asyncio.CancelledError:
            raise
        except Exception as exc:
            print(f"[server-status] updater failed: {exc!r}", flush=True)
        await asyncio.sleep(max(10, int(cfg.get("server_status_interval", STATUS_INTERVAL_DEFAULT))))
