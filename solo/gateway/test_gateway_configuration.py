#!/usr/bin/env python3
"""Configuration tests for the Oracle Minecraft wake/status gateway."""
import importlib
import os
import sys
import unittest
from pathlib import Path
from unittest.mock import AsyncMock, patch

sys.path.insert(0, str(Path(__file__).parent))


class GatewayTargetConfigurationTests(unittest.TestCase):
    def load_gateway(self, module_name):
        sys.modules.pop(module_name, None)
        return importlib.import_module(module_name)

    def test_testplex_only_route_uses_protocol_777(self):
        env = {
            "MM18_ENABLED": "false",
            "MM21_MACHINE_ID": "test-machine",
            "MM21_PROTOCOLS": "777",
            "MM21_VERSION": "26.3",
        }
        with patch.dict(os.environ, env, clear=False):
            gateway = self.load_gateway("monstermaze_gateway")
            self.assertEqual({"1.21"}, set(gateway.TARGETS))
            self.assertIsNone(gateway.target_for_protocol(47))
            self.assertEqual("test-machine", gateway.target_for_protocol(777).machine_id)
            self.assertEqual("26.3", gateway.target_for_protocol(777).version_name)

    def test_status_target_matches_testplex_route(self):
        env = {
            "MM18_ENABLED": "false",
            "MM21_MACHINE_ID": "test-machine",
            "MM21_VERSION": "26.3",
        }
        with patch.dict(os.environ, env, clear=False):
            status = self.load_gateway("status_api")
            self.assertEqual({"1.21"}, set(status.TARGETS))
            self.assertEqual("test-machine", status.TARGETS["1.21"]["machine_id"])
            self.assertEqual("26.3", status.TARGETS["1.21"]["version"])

    def test_default_keeps_legacy_target_enabled(self):
        with patch.dict(os.environ, {}, clear=True):
            gateway = self.load_gateway("monstermaze_gateway")
            status = self.load_gateway("status_api")
            self.assertIn("1.8", gateway.TARGETS)
            self.assertIn("1.8", status.TARGETS)
            self.assertEqual(47, gateway.TARGETS["1.8"].protocols[0])
            self.assertEqual("1.21.11", status.TARGETS["1.21"]["version"])

    def test_target_summary_handles_disabled_legacy_route(self):
        env = {
            "MM18_ENABLED": "false",
            "MM21_PROTOCOLS": "777",
            "MM21_VERSION": "26.3",
            "FLY_BACKEND_HOST": "monstermaze.fly.dev",
        }
        with patch.dict(os.environ, env, clear=False):
            gateway = self.load_gateway("monstermaze_gateway")
            self.assertEqual(
                "1.21 protocols=(777,) -> monstermaze.fly.dev:25566",
                gateway.describe_targets(),
            )

    def test_status_handshake_never_enters_login_wake_path(self):
        import asyncio
        import monstermaze_gateway as gateway

        target = gateway.Target("1.21", (777,), 25566, "test-machine", "26.3")
        client = type("Client", (), {"write": lambda self, data: None, "drain": lambda self: asyncio.sleep(0), "is_closing": lambda self: False, "close": lambda self: None, "wait_closed": lambda self: asyncio.sleep(0), "get_extra_info": lambda self, name: ("test-peer", 1234)})()
        with patch.object(gateway, "read_packet", new=AsyncMock(return_value=(b"handshake", b"payload"))), \
             patch.object(gateway, "parse_handshake", return_value=(777, "test", 25565, 1)), \
             patch.object(gateway, "target_for_protocol", return_value=target), \
             patch.object(gateway, "handle_status", new=AsyncMock()) as handle_status, \
             patch.object(gateway, "handle_login", new=AsyncMock()) as handle_login, \
             patch.object(gateway, "start_machine", new=AsyncMock()) as start_machine:
            asyncio.run(gateway.handle_client(None, client))
        handle_status.assert_awaited_once()
        handle_login.assert_not_awaited()
        start_machine.assert_not_awaited()

    def test_login_handshake_enters_identity_checked_login_path(self):
        import asyncio
        import monstermaze_gateway as gateway

        target = gateway.Target("1.21", (777,), 25566, "test-machine", "26.3")
        client = type("Client", (), {"write": lambda self, data: None, "drain": lambda self: asyncio.sleep(0), "is_closing": lambda self: False, "close": lambda self: None, "wait_closed": lambda self: asyncio.sleep(0), "get_extra_info": lambda self, name: ("test-peer", 1234)})()
        with patch.object(gateway, "read_packet", new=AsyncMock(return_value=(b"handshake", b"payload"))), \
             patch.object(gateway, "parse_handshake", return_value=(777, "test", 25565, 2)), \
             patch.object(gateway, "target_for_protocol", return_value=target), \
             patch.object(gateway, "handle_status", new=AsyncMock()) as handle_status, \
             patch.object(gateway, "handle_login", new=AsyncMock()) as handle_login:
            asyncio.run(gateway.handle_client(None, client))
        handle_login.assert_awaited_once()
        handle_status.assert_not_awaited()


if __name__ == "__main__":
    unittest.main()
