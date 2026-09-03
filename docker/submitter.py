#!/usr/bin/env python3
import json
import os
import shutil
import time
import urllib.error
import urllib.request
from pathlib import Path


def webhook_for(mode: str) -> str:
    mode_key = mode.lower().replace('-', '_')
    specific = os.getenv(f"MM_WEBHOOK_{mode_key.upper()}", "").strip()
    return specific or os.getenv("MM_DISCORD_WEBHOOK", "").strip()


def post_run(run: dict, source: Path, webhook: str) -> None:
    platform = str(run.get("platform") or "1.8")
    minecraft = "1.8.9" if platform == "1.8" else "1.21.11"
    kit = run.get("kit") or "None"
    time_ms = int(run.get("timeMs") or 0)
    mins = time_ms // 60000
    secs = round((time_ms % 60000) / 1000)
    if secs == 60:
        mins += 1
        secs = 0
    elapsed = f"{mins}m {secs}s"
    pattern = int(run.get("pattern") or 0) + 1

    embed = {
        "title": f"{run.get('name', 'Unknown')} - Solo Run (stage {run.get('stage', '?')})",
        "color": 0x33AA66,
        "fields": [
            {"name": "Minecraft", "value": minecraft, "inline": True},
            {"name": "Mode", "value": str(run.get("mode", "Unknown")), "inline": True},
            {"name": "Pattern", "value": f"Maze {pattern}", "inline": True},
            {"name": "Kit", "value": str(kit), "inline": True},
            {"name": "Stage", "value": str(run.get("stage", "?")), "inline": True},
            {"name": "Time", "value": elapsed, "inline": True},
        ],
        "footer": {
            "text": (
                f"platform {platform} | uuid {run.get('uuid', '?')} | "
                f"submittedAt {run.get('submittedAt', '?')} | submission {source.stem} | "
                f"configHash {run.get('configHash', '?')}"
            )
        },
    }
    payload = json.dumps({"content": "New solo run submitted!", "embeds": [embed]}).encode("utf-8")
    request = urllib.request.Request(
        webhook,
        data=payload,
        headers={"Content-Type": "application/json", "User-Agent": "MonsterMaze-Fly-Submitter/1.0"},
        method="POST",
    )
    with urllib.request.urlopen(request, timeout=20) as response:
        if response.status < 200 or response.status >= 300:
            raise RuntimeError(f"Discord webhook returned HTTP {response.status}")


def process_folder(folder: Path) -> None:
    if not folder.exists():
        return
    submitted = folder / "submitted"
    submitted.mkdir(parents=True, exist_ok=True)

    for source in sorted(folder.glob("*.json")):
        try:
            run = json.loads(source.read_text(encoding="utf-8"))
        except Exception as exc:
            print(f"[submitter] skipping bad JSON {source.name}: {exc}", flush=True)
            continue

        platform = str(run.get("platform") or "1.8")
        if platform not in {"1.8", "1.21"}:
            print(f"[submitter] skipping {source.name}: unsupported platform {platform}", flush=True)
            continue

        mode = str(run.get("mode") or "")
        webhook = webhook_for(mode)
        if not webhook:
            # No webhook configured is intentionally non-fatal. Runs stay queued.
            continue

        try:
            post_run(run, source, webhook)
            shutil.move(str(source), str(submitted / source.name))
            print(f"[submitter] posted {source.name}: {platform} / {mode}", flush=True)
        except (urllib.error.URLError, urllib.error.HTTPError, OSError, RuntimeError) as exc:
            print(f"[submitter] failed {source.name}: {exc}", flush=True)


def main() -> None:
    import argparse

    parser = argparse.ArgumentParser()
    parser.add_argument("--root18", required=True)
    parser.add_argument("--root21", required=True)
    parser.add_argument("--interval", type=int, default=10)
    args = parser.parse_args()

    folders = [
        Path(args.root18) / "plugins" / "MonsterMazeStandalone" / "solo-runs",
        Path(args.root21) / "plugins" / "MonsterMazeStandalone" / "solo-runs",
    ]

    if not os.getenv("MM_DISCORD_WEBHOOK", "").strip() and not any(
        os.getenv(name, "").strip() for name in os.environ if name.startswith("MM_WEBHOOK_")
    ):
        print("[submitter] no Discord webhook configured; runs will remain queued", flush=True)

    while True:
        for folder in folders:
            process_folder(folder)
        time.sleep(max(2, args.interval))


if __name__ == "__main__":
    main()
