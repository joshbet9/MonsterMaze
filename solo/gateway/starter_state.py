#!/usr/bin/env python3
"""Small shared state store for the last gateway-started server IGN."""

import json
import os
from pathlib import Path

STATE_FILE = Path(os.getenv("MM_STARTER_STATE_FILE", "/home/ubuntu/monstermaze/data/last_starters.json"))


def _load() -> dict:
    try:
        with STATE_FILE.open("r", encoding="utf-8") as handle:
            data = json.load(handle)
        return data if isinstance(data, dict) else {}
    except (FileNotFoundError, OSError, json.JSONDecodeError):
        return {}


def record_started_by(server: str, username: str) -> None:
    STATE_FILE.parent.mkdir(parents=True, exist_ok=True)
    data = _load()
    data[server] = username
    temp_file = STATE_FILE.with_name(STATE_FILE.name + ".tmp")
    with temp_file.open("w", encoding="utf-8") as handle:
        json.dump(data, handle, separators=(",", ":"))
        handle.write("\n")
    os.replace(temp_file, STATE_FILE)


def get_started_by(server: str):
    return _load().get(server)
