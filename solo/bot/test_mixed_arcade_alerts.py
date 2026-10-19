"""Tests for persisted per-user Mixed Arcade count alerts."""
import asyncio
import os
import sqlite3
import tempfile
import unittest
from datetime import datetime, timezone
from types import SimpleNamespace
from unittest.mock import patch

import game_counts


class FakeUser:
    def __init__(self, user_id):
        self.id = user_id
        self.messages = []

    async def send(self, content):
        self.messages.append(content)


class FakeBot:
    def __init__(self):
        self.cfg = {"game_counts_channel": "game-counts"}
        self.users = {}
        self.channel_object = SimpleNamespace(id=123456789)

    async def call(self, fn, _label):
        return await fn()

    async def fetch_user(self, user_id):
        return self.users[user_id]

    def channel(self, _ref):
        return self.channel_object


class MixedArcadeAlertTests(unittest.TestCase):
    def setUp(self):
        handle, self.db_path = tempfile.mkstemp(suffix=".sqlite")
        os.close(handle)
        self.db_patch = patch.object(game_counts.base, "db", side_effect=self.connect)
        self.db_patch.start()

    def tearDown(self):
        self.db_patch.stop()
        try:
            os.unlink(self.db_path)
        except OSError:
            pass

    def connect(self):
        return sqlite3.connect(self.db_path)

    def test_alert_subscription_can_be_created_updated_and_removed(self):
        game_counts.set_alert_subscription("1001", 5)
        self.assertEqual(game_counts.get_alert_subscription("1001")["threshold"], 5)
        game_counts.set_alert_subscription("1001", 9)
        updated = game_counts.get_alert_subscription("1001")
        self.assertEqual(updated["threshold"], 9)
        self.assertEqual(updated["state"], "armed")
        self.assertTrue(game_counts.remove_alert_subscription("1001"))
        self.assertIsNone(game_counts.get_alert_subscription("1001"))
        self.assertFalse(game_counts.remove_alert_subscription("1001"))

    def test_user_is_alerted_once_then_rearmed_when_count_drops(self):
        game_counts.set_alert_subscription("1002", 5)
        bot = FakeBot()
        bot.users[1002] = FakeUser(1002)

        asyncio.run(game_counts.notify_alert_subscribers(bot, 5))
        self.assertEqual(len(bot.users[1002].messages), 1)
        self.assertEqual(game_counts.get_alert_subscription("1002")["state"], "triggered")

        asyncio.run(game_counts.notify_alert_subscribers(bot, 8))
        self.assertEqual(len(bot.users[1002].messages), 1)

        asyncio.run(game_counts.notify_alert_subscribers(bot, 2))
        self.assertEqual(game_counts.get_alert_subscription("1002")["state"], "armed")

        asyncio.run(game_counts.notify_alert_subscribers(bot, 5))
        self.assertEqual(len(bot.users[1002].messages), 2)

    def test_latest_count_and_checked_time_survive_reopen(self):
        observed = datetime(2026, 10, 19, 7, 0, tzinfo=timezone.utc)
        game_counts._save_latest_count(7, observed)
        count, checked_at = game_counts.latest_count()
        self.assertEqual(count, 7)
        self.assertEqual(checked_at, int(observed.timestamp()))


if __name__ == "__main__":
    unittest.main()
