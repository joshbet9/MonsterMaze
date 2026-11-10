#!/usr/bin/env python3
"""Status API machine-state classification tests."""

import importlib
import os
import sys
import unittest
from pathlib import Path
from unittest.mock import patch

sys.path.insert(0, str(Path(__file__).parent))


class StatusApiStateTests(unittest.TestCase):
    def load_status_api(self):
        sys.modules.pop("status_api", None)
        return importlib.import_module("status_api")

    def test_destroyed_machine_is_offline_and_stale_starter_is_cleared(self):
        status = self.load_status_api()
        target = {"machine_id": "retired", "version": "26.3"}
        with patch.object(status, "machine_state", return_value="destroyed"), \\
             patch.object(status, "clear_started_by") as clear, \\
             patch.object(status, "get_started_by", return_value="JN_PlayzMC") as get_starter:
            result = status.one_status("1.21", target)
        self.assertEqual("offline", result["state"])
        self.assertNotIn("started_by", result)
        clear.assert_called_once_with("1.21")
        get_starter.assert_not_called()

    def test_failed_and_stopping_machines_are_offline(self):
        status = self.load_status_api()
        target = {"machine_id": "retired", "version": "26.3"}
        for state in ("failed", "stopping", "destroying", "created", "stopped", "suspended"):
            with self.subTest(state=state), patch.object(status, "machine_state", return_value=state), patch.object(status, "clear_started_by"):
                self.assertEqual("offline", status.one_status("1.21", target)["state"])

    def test_unrecognized_machine_state_is_unknown_not_starting(self):
        status = self.load_status_api()
        target = {"machine_id": "mystery", "version": "26.3"}
        with patch.object(status, "machine_state", return_value="replacing"), patch.object(status, "get_started_by") as get_starter:
            self.assertEqual("unknown", status.one_status("1.21", target)["state"])
        get_starter.assert_not_called()

    def test_started_without_heartbeat_is_starting(self):
        status = self.load_status_api()
        target = {"machine_id": "running", "version": "26.3"}
        with patch.object(status, "machine_state", return_value="started"), patch.object(status, "heartbeat_status", return_value=None), patch.object(status, "get_started_by", return_value=None):
            self.assertEqual("starting", status.one_status("1.21", target)["state"])

    def test_started_with_heartbeat_is_online(self):
        status = self.load_status_api()
        target = {"machine_id": "running", "version": "26.3"}
        with patch.object(status, "machine_state", return_value="started"), patch.object(status, "heartbeat_status", return_value=3):
            result = status.one_status("1.21", target)
        self.assertEqual(("online", 3), (result["state"], result["players"]))


if __name__ == "__main__":
    unittest.main()
