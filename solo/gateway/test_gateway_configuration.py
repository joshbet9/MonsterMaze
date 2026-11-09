#!/usr/bin/env python3
"""Configuration tests for the Oracle Minecraft wake/status gateway."""
import importlib
import os
import sys
import unittest
from pathlib import Path
from unittest.mock import patch

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


if __name__ == "__main__":
    unittest.main()
