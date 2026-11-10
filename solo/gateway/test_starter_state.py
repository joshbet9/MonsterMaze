#!/usr/bin/env python3
"""Tests for short-lived Discord starter attribution."""

import json
import tempfile
import time
import unittest
from pathlib import Path
from unittest.mock import patch

import starter_state


class StarterStateTests(unittest.TestCase):
    def test_recent_login_attribution_is_returned(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "starter.json"
            with patch.object(starter_state, "STATE_FILE", path), patch.object(starter_state, "ATTRIBUTION_TTL_SECONDS", 600):
                starter_state.record_started_by("1.21", "JN_PlayzMC")
                self.assertEqual("JN_PlayzMC", starter_state.get_started_by("1.21"))

    def test_expired_login_attribution_is_cleared(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "starter.json"
            old = {"1.21": {"username": "JN_PlayzMC", "started_at": time.time() - 601}}
            path.write_text(json.dumps(old), encoding="utf-8")
            with patch.object(starter_state, "STATE_FILE", path), patch.object(starter_state, "ATTRIBUTION_TTL_SECONDS", 600):
                self.assertIsNone(starter_state.get_started_by("1.21"))
            self.assertEqual({}, json.loads(path.read_text(encoding="utf-8")))

    def test_legacy_string_attribution_is_ignored(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "starter.json"
            path.write_text(json.dumps({"1.21": "JN_PlayzMC"}), encoding="utf-8")
            with patch.object(starter_state, "STATE_FILE", path):
                self.assertIsNone(starter_state.get_started_by("1.21"))

    def test_clear_removes_only_selected_server(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "starter.json"
            with patch.object(starter_state, "STATE_FILE", path):
                starter_state.record_started_by("1.8", "Alice")
                starter_state.record_started_by("1.21", "Bob")
                starter_state.clear_started_by("1.21")
            self.assertEqual({"1.8": {"username": "Alice", "started_at": json.loads(path.read_text(encoding="utf-8"))["1.8"]["started_at"]}}, json.loads(path.read_text(encoding="utf-8")))


if __name__ == "__main__":
    unittest.main()
