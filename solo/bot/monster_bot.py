"""Entry point for the Monster Maze SOLO Discord bot and results API."""
import asyncio
import os

import discord
import api_server
import monster_bot_v2 as impl

CURRENT_BOT = None


def refresh_bot(platform):
    bot = CURRENT_BOT
    if bot is None:
        return
    try:
        future = asyncio.run_coroutine_threadsafe(bot.refresh_platform(platform), bot.loop)
        future.result(timeout=10)
        competition_refresh = getattr(bot, "refresh_competitions", None)
        if competition_refresh is not None:
            future = asyncio.run_coroutine_threadsafe(competition_refresh(), bot.loop)
            future.result(timeout=10)
    except Exception as exc:
        print(f"[api] bot refresh failed: {exc}", flush=True)


def post_feed(run):
    bot = CURRENT_BOT
    if bot is None:
        return
    try:
        future = asyncio.run_coroutine_threadsafe(_post_feed_async(bot, run), bot.loop)
        future.result(timeout=10)
    except Exception as exc:
        print(f"[api] Discord feed post failed: {exc}", flush=True)


async def _post_feed_async(bot, run):
    minecraft = "1.8.9" if run["platform"] == "1.8" else "1.21.11"
    kit = impl.kit_label(run["kit"])
    time_ms = int(run.get("time_ms", 0))
    mins = time_ms // 60000
    secs = round((time_ms % 60000) / 1000)
    if secs == 60:
        mins += 1
        secs = 0
    elapsed = f"{mins}m {secs}s"
    embed = discord.Embed(
        title=f"{run['name']} - Solo Run (stage {run['stage']})",
        color=0x33AA66,
    )
    embed.add_field(name="Minecraft", value=minecraft, inline=True)
    embed.add_field(name="Mode", value=str(run["mode"]), inline=True)
    embed.add_field(name="Pattern", value=f"Maze {int(run['pattern']) + 1}", inline=True)
    embed.add_field(name="Kit", value=kit, inline=True)
    embed.add_field(name="Stage", value=str(run["stage"]), inline=True)
    embed.add_field(name="Time", value=elapsed, inline=True)
    embed.set_footer(text=(
        f"platform {run['platform']} | uuid {run['uuid']} | "
        f"submittedAt {run['submitted_at']} | submission {run['submission_id']} | "
        f"configHash {run.get('config_hash', '')}"
    ))
    for channel in bot.feeds():
        await bot.call(
            lambda channel=channel: channel.send(content="New solo run submitted!", embed=embed),
            f"api {run['platform']} run feed",
        )


class APIMonsterBot(impl.MonsterBot):
    def __init__(self, cfg):
        super().__init__(cfg)
        global CURRENT_BOT
        CURRENT_BOT = self


def configure_api():
    api_server.configure(
        db_fn=impl.db,
        insert_submission=impl.insert_submission,
        upsert_run=impl.upsert_run,
        create_competition=lambda platform: impl.create_competition(platform, impl.load_config()),
        board_rows=impl.board_rows,
        competition_rows=impl.competition_rows,
        refresh_bot=refresh_bot,
        post_feed=post_feed,
    )


def main():
    configure_api()
    api_server.start_server(
        host=os.getenv("MM_API_HOST", "0.0.0.0"),
        port=int(os.getenv("MM_API_PORT", "8090")),
    )
    impl.MonsterBot = APIMonsterBot
    impl.main()


if __name__ == "__main__":
    main()
