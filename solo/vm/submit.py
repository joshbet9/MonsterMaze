#!/usr/bin/env python3
"""Monster Maze Linux Solo submitter.

Watches both VM server solo-runs folders and posts every completed run to Discord.
Webhook configuration is deliberately kept outside Git in ~/submit-config-18.ps1
and ~/submit-config-21.ps1.
"""
from __future__ import annotations

import json
import re
import shutil
import time
from pathlib import Path
from typing import Dict, Optional, Tuple
from urllib.error import HTTPError, URLError
from urllib.request import Request, urlopen

HOME = Path.home()
SERVER_DIRS = {
    "1.8": HOME / "servers" / "1.8" / "plugins" / "MonsterMazeStandalone" / "solo-runs",
    "1.21": HOME / "servers" / "1.21" / "plugins" / "MonsterMazeStandalone" / "solo-runs",
}
CONFIG_FILES = {
    "1.8": HOME / "submit-config-18.ps1",
    "1.21": HOME / "submit-config-21.ps1",
}
ARCHIVE_DIR = HOME / "submitter" / "submitted"
POLL_SECONDS = 2
MAX_RETRIES = 8


def parse_webhook_config(path: Path) -> Tuple[Dict[str, str], str]:
    """Read the small PowerShell webhook config without executing it."""
    if not path.exists():
        return {}, ""
    text = path.read_text(encoding="utf-8", errors="replace")
    webhooks: Dict[str, str] = {}

    # Supports common forms such as:
    # $WEBHOOKS = @{ speed = "https://..." }
    # $WEBHOOKS["speed"] = "https://..."
    for match in re.finditer(r"(?:\$WEBHOOKS\s*=\s*@\{|\$WEBHOOKS\[\s*['\"]([^'\"]+)['\"]\s*\]\s*=)\s*([^\n}]*)", text, re.I):
        mode = match.group(1)
        rhs = match.group(2)
        if mode:
            url_match = re.search(r"['\"](https?://[^'\"]+)['\"]", rhs)
            if url_match:
                webhooks[mode.lower()] = url_match.group(1)

    # Hashtable entries inside $WEBHOOKS = @{ ... }.
    if "$WEBHOOKS" in text:
        block = re.search(r"\$WEBHOOKS\s*=\s*@\{(.*?)\}", text, re.I | re.S)
        if block:
            for match in re.finditer(r"['\"]?([^='\"\s]+)['\"]?\s*=\s*['\"](https?://[^'\"]+)['\"]", block.group(1)):
                webhooks[match.group(1).lower()] = match.group(2)

    default_match = re.search(r"\$DEFAULT_WEBHOOK\s*=\s*['\"](https?://[^'\"]+)['\"]", text, re.I)
    default = default_match.group(1) if default_match else ""
    return webhooks, default


def discord_post(webhook: str, body: bytes) -> None:
    delay = 2.0
    for attempt in range(MAX_RETRIES):
        req = Request(webhook, data=body, method="POST", headers={"Content-Type": "application/json", "User-Agent": "MonsterMaze-Solo-Submitter"})
        try:
            with urlopen(req, timeout=30):
                return
        except HTTPError as exc:
            retry_after = None
            header = exc.headers.get("Retry-After") if exc.headers else None
            if header:
                try:
                    retry_after = float(header)
                except ValueError:
                    retry_after = None
            payload = b""
            try:
                payload = exc.read()
            except Exception:
                pass
            if payload:
                try:
                    retry_after = float(json.loads(payload.decode("utf-8")).get("retry_after", 0)) or retry_after
                except Exception:
                    pass
            if exc.code == 429 or 500 <= exc.code < 600:
                wait = max(delay, retry_after or 0.0)
                print(f"Discord HTTP {exc.code}; retrying in {wait:.1f}s")
                time.sleep(wait)
                delay = min(delay * 2, 60)
                continue
            raise
        except (URLError, TimeoutError, OSError) as exc:
            if attempt == MAX_RETRIES - 1:
                raise
            print(f"Discord/network error: {exc}; retrying in {delay:.1f}s")
            time.sleep(delay)
            delay = min(delay * 2, 60)
    raise RuntimeError("Discord submission retry limit reached")


def format_time(time_ms: int) -> str:
    total_seconds = max(0, int(round(time_ms / 1000.0)))
    return f"{total_seconds // 60}m {total_seconds % 60}s"


def process_file(platform: str, src: Path, webhooks: Dict[str, str], default_webhook: str) -> bool:
    try:
        run = json.loads(src.read_text(encoding="utf-8"))
    except Exception as exc:
        print(f"Skipping {src.name}: bad JSON ({exc})")
        return False

    record_platform = str(run.get("platform") or ("1.8" if platform == "1.8" else ""))
    if record_platform != platform:
        return False

    mode = str(run.get("mode") or "Unknown")
    webhook = webhooks.get(mode.lower()) or default_webhook
    if not webhook:
        print(f"No webhook configured for {platform}/{mode}; keeping {src.name}")
        return False

    kit = str(run.get("kit") or "None")
    pattern = run.get("pattern")
    try:
        pattern_text = f"Maze {int(pattern) + 1}" if pattern is not None else "Unknown"
    except (TypeError, ValueError):
        pattern_text = "Unknown"

    minecraft = "1.8.9" if platform == "1.8" else "1.21.11"
    footer = (
        f"platform {platform} | uuid {run.get('uuid', '')} | "
        f"submittedAt {run.get('submittedAt', '')} | submission {src.stem} | "
        f"configHash {run.get('configHash', '')}"
    )
    embed = {
        "title": f"{run.get('name', 'Unknown')} - Solo Run (stage {run.get('stage', '?')})",
        "color": 0x33AA66,
        "fields": [
            {"name": "Minecraft", "value": minecraft, "inline": True},
            {"name": "Mode", "value": mode, "inline": True},
            {"name": "Pattern", "value": pattern_text, "inline": True},
            {"name": "Kit", "value": kit, "inline": True},
            {"name": "Stage", "value": str(run.get("stage", "?")), "inline": True},
            {"name": "Time", "value": format_time(int(run.get("timeMs", 0))), "inline": True},
        ],
        "footer": {"text": footer},
    }
    body = json.dumps({"content": "New solo run submitted!", "embeds": [embed]}, separators=(",", ":")).encode("utf-8")

    discord_post(webhook, body)
    dest_dir = ARCHIVE_DIR / platform
    dest_dir.mkdir(parents=True, exist_ok=True)
    shutil.move(str(src), str(dest_dir / src.name))
    print(f"Posted and archived {platform}: {src.name}")
    return True


def main() -> None:
    configs = {platform: parse_webhook_config(path) for platform, path in CONFIG_FILES.items()}
    for platform, path in CONFIG_FILES.items():
        if path.exists():
            print(f"Loaded webhook config for {platform}: {path}")
        else:
            print(f"WARNING: webhook config missing for {platform}: {path}")

    for folder in SERVER_DIRS.values():
        folder.mkdir(parents=True, exist_ok=True)
    for platform in SERVER_DIRS:
        (ARCHIVE_DIR / platform).mkdir(parents=True, exist_ok=True)

    while True:
        for platform, folder in SERVER_DIRS.items():
            webhooks, default_webhook = configs[platform]
            for src in sorted(folder.glob("*.json")):
                try:
                    process_file(platform, src, webhooks, default_webhook)
                except Exception as exc:
                    print(f"Failed to submit {platform}/{src.name}: {exc}")
        time.sleep(POLL_SECONDS)


if __name__ == "__main__":
    main()
