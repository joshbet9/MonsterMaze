#!/usr/bin/env python3
"""Small shared state store for recent gateway-started server attribution."""

import json
import os
import time
from pathlib import Path

STATE_FILE = Path(os.getenv("MM_STARTER_STATE_FILE", "/home/ubuntu/monstermaze/data/last_starters.json"))
ATTRIBUTION_TTL_SECONDS = max(0.0, float(os.getenv("MM_STARTER_ATTRIBUTION_TTL_SECONDS", "600")))


def _load() -> dict:
    try:
        with STATE_FILE.open("r", encoding="utf-8") as handle:
            data = json.load(handle)
        return data if isinstance(data, dict) else {}
    except (FileNotFoundError, OSError, json.JSONDecodeError):
        return {}


def _save(data: dict) -> None:
    STATE_FILE.parent.mkdir(parents=True, exist_ok=True)
    temp_file = STATE_FILE.with_name(STATE_FILE.name + ".tmp")
    with temp_file.open("w", encoding="utf-8") as handle:
        json.dump(data, handle, separators=(",", ":"))
        handle.write("\n")
    os.replace(temp_file, STATE_FILE)


def record_started_by(server: str, username: str) -> None:
    data = _load()
    data[server] = {"username": username, "started_at": time.time()}
    _save(data)


def get_started_by(server: str):
    record = _load().get(server)
    # Old string-only records have no reliable age and are intentionally ignored.
    if not isinstance(record, dict):
        return None
    username = record.get("username")
    started_at = record.get("started_at")
    if not isinstance(username, str) or not username or not isinstance(started_at, (int, float)):
        return None
    if ATTRIBUTION_TTL_SECONDS == 0 or time.time() - started_at > ATTRIBUTION_TTL_SECONDS:
        clear_started_by(server)
        return None
    return username


def clear_started_by(server: str) -> None:
    data = _load()
    if server in data:
        del data[server]
        _save(data)
