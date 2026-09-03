"""Entry point for the Monster Maze SOLO Discord bot and results API."""
import asyncio
import os

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
        refresh_bot=refresh_bot,
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
