# Monster Maze — SOLO

A local, single-player build of Monster Maze for crowd-sourced leaderboards.

> **For players:** read `HOW_TO_PLAY.txt` in this folder — it's the plain,
> non-technical instructions. This README is for the developer/owner.

## Distributing to players

Run the appropriate packer to assemble a ready-to-play copy. Players unzip it and
double-click `launcher\play.bat`.

> **Webhook note:** the webhook URL ships inside `submitter\config.ps1` once you
> set it. Only hand the zip to people you trust — anyone with it can post to your
> Discord channel. For outsiders, collect the `.json` and post it yourself.
> `submitter\config.ps1` is **gitignored** (a Discord secret); the committed
> `config.ps1.example` is the template you copy to make your real one.

## Auto-updates for players

Each Solo distribution bundles its updater and an `installed.version` marker.
The launcher checks for updates automatically; if GitHub is unreachable it just
starts the game.

The updater hashes each listed file and downloads only changed files. Player data,
configuration, runtime worlds and other local state are protected from release
updates.

## Solo records and platform separation

When a solo PB is achieved, `RunRecorder` writes a JSON record to the local
`solo-runs` directory. The record now contains an explicit `platform` field:

```json
{
  "schema": "1",
  "platform": "1.8",
  "plugin": "1.0.0",
  "name": "Steve",
  "uuid": "…",
  "mode": "Modern",
  "pattern": 2,
  "kit": "Jumper",
  "stage": 24,
  "timeMs": 482000,
  "configHash": "…",
  "submittedAt": 1730000000000
}
```

1.8 records use `platform: "1.8"`; 1.21 records use `platform: "1.21"`.
The submitter displays the exact Minecraft release (1.8.9 or 1.21.11) in the
Discord PB embed. Older records without the field are treated as 1.8 for
backwards compatibility.

There is **no replay, seed pinning, or anti-cheat** — per the trust-based model,
participants record their own video. `configHash` simply lets the leaderboard
spot a divergent configuration.

## Submitting runs

Runs are **PB-only** (mode + pattern + kit). Each PB is written locally, then
`submit.ps1` posts it to the Discord webhook for that run's mode. A run is moved
to `submitted\` only after a successful post, so network failures do not lose a PB.

## Ranked leaderboard bot (`solo/bot/`)

The optional Discord bot watches the PB feed and maintains three tiers of pinned
ranked boards per mode and **per Minecraft platform**:

- **Overall** — top stages across all patterns and kits.
- **Pattern** — one board per Maze 1/2/3.
- **Kit** — one board per pattern × kit (3 × 5 = 15).

The bot's database key is now:

`platform + mode + pattern + kit + uuid`

so a player's 1.8 and 1.21 PBs can never overwrite each other.

### Bot hardening

The bot now:

- rebuilds from the **complete PB feed history** instead of stopping at 500 messages;
- relies on Discord.py pagination for history retrieval;
- retries HTTP 429 and transient 5xx responses using Discord's retry delay when available;
- coalesces bursts of live PBs into one leaderboard refresh per platform/mode;
- does not rebuild again every time Discord reconnects;
- validates platform, UUID and maze pattern before accepting a submission;
- migrates an existing `leaderboard.db` automatically, treating its historical rows
  as 1.8 and preserving existing 1.8 board messages.

### Bot setup

1. Create a bot application at the Discord Developer Portal and enable the
   **Message Content Intent**.
2. Invite it with Send Messages, Embed Links, Read Messages, Read Message History,
   and Manage Messages permissions.
3. Create three channels for each **platform + mode** you want boards for:
   - overall (`#lb-modern-1-8`)
   - patterns (`#lb-modern-1-8-patterns`)
   - kits (`#lb-modern-1-8-kits`)
   and equivalent channels for 1.21.
4. `cd solo/bot && pip install -r requirements.txt`
5. Copy `config.example.json` to `config.json` and set the token, feed channel and
   `channels.<platform>.<mode>.overall|patterns|kits` values.
6. Run `python monster_bot.py`.

The bot's live database and token configuration are gitignored.
