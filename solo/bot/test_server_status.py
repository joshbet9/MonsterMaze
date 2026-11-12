#!/usr/bin/env python3
"""Discord server status labels and alert wording tests."""

import importlib
import sys
import types
import unittest


def load_server_status():
    discord_stub = types.SimpleNamespace(HTTPException=Exception, NotFound=type("NotFound", (Exception,), {}))
    bot_stub = types.ModuleType("monster_bot_v2")
    sys.modules["discord"] = discord_stub
    sys.modules["monster_bot_v2"] = bot_stub
    sys.modules.pop("server_status", None)
    return importlib.import_module("server_status")


class ServerStatusLabelTests(unittest.TestCase):
    def test_status_board_uses_testplex_name_for_26_3_server(self):
        status = load_server_status()
        content = status.status_text({"servers": {
            "1.8": {"state": "offline"},
            "1.21": {"state": "online", "players": 2},
        }})
        self.assertIn("Testplex 26.3", content)
        self.assertNotIn("MM21", content)
        self.assertNotIn("MonsterMaze 1.21", content)

    def test_starting_notification_uses_testplex_name(self):
        import asyncio
        from unittest.mock import AsyncMock, patch

        status = load_server_status()
        user = types.SimpleNamespace(mention="@Josh", send=AsyncMock())
        async def call(fn, *_args):
            return await fn()

        bot = types.SimpleNamespace(call=AsyncMock(side_effect=call), fetch_user=AsyncMock(return_value=user))
        with patch.object(status, "STATUS_NOTIFY_USER_ID_DEFAULT", 1):
            asyncio.run(status.notify_state_change(bot, "1.21", "offline", "starting", {"started_by": "Josh"}))
        sent = user.send.await_args.args[0]
        self.assertIn("Testplex 26.3", sent)
        self.assertIn("started by Josh", sent)
        self.assertNotIn("MonsterMaze 1.21", sent)

    def test_unknown_status_is_not_displayed_as_starting(self):
        status = load_server_status()
        content = status.status_text({"servers": {"1.21": {"state": "unknown"}}})
        self.assertIn("Status unavailable", content)
        self.assertNotIn("Starting...", content)

    def test_lifecycle_alert_is_processed_when_status_board_channel_is_missing(self):
        import asyncio
        from unittest.mock import AsyncMock, patch

        status = load_server_status()
        bot = types.SimpleNamespace(channel=lambda _ref: None)
        data = {"servers": {
            "1.8": {"state": "offline"},
            "1.21": {"state": "starting", "started_by": "Josh"},
        }}
        with patch.object(status, "fetch_status", new=AsyncMock(return_value=data)), \
             patch.object(status, "get_previous_states", return_value={"1.8": "offline", "1.21": "offline"}), \
             patch.object(status, "get_previous_starter", return_value=None), \
             patch.object(status, "set_previous_starter") as set_starter, \
             patch.object(status, "set_state") as set_state, \
             patch.object(status, "notify_state_change", new=AsyncMock()) as notify:
            asyncio.run(status.update(bot, {}))

        notify.assert_awaited_once_with(bot, "1.21", "offline", "starting", data["servers"]["1.21"])
        set_starter.assert_any_call("1.21", "Josh")
        set_state.assert_any_call("1.21", "starting")


if __name__ == "__main__":
    unittest.main()
