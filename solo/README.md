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

Every completed solo attempt is written as a JSON record to the local `solo-runs`
directory. The record contains an explicit `platform` and `submittedAt` timestamp:

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
Discord run embed. Older records without the field are treated as 1.8 for
backwards compatibility.

There is **no replay, seed pinning, or anti-cheat** — per the trust-based model,
participants record their own video. `configHash` simply lets the leaderboard
spot a divergent configuration.

## Submitting runs

**Every completed solo attempt is submitted**, not just lifetime PBs. The permanent
leaderboards still use each player's best result, while the weekly competitions
use the complete attempt history and select each player's best valid run during
the competition's Monday-Sunday Brisbane-time window.

Each run is written locally first, then `submit.ps1` posts it to the Discord webhook
for that run's mode. A run is moved to `submitted\` only after a successful post, so
network failures do not lose an attempt.

The Discord bot stores two separate concepts:

- **Lifetime PBs** — best-ever result for permanent leaderboards.
- **Solo submissions** — every completed attempt, with its submission timestamp,
  for weekly competition history.

## Weekly competitions

There is one independent weekly competition for Minecraft 1.8.9 and one for
Minecraft 1.21.11. Each Monday a random valid combination of mode + maze pattern
+ kit is locked for the entire week. Recent combinations are avoided where
possible, and invalid kit/mode combinations are excluded.

The `#competitions` channel shows the live challenge and its standings, then keeps
a frozen archived result after the week ends. A player's lifetime PB does not
qualify them for a week unless they actually submitted a run during that week.

## Ranked leaderboard bot (`solo/bot/`)

The optional Discord bot maintains three consolidated lifetime leaderboard channels
per Minecraft platform:

- **Overall** — one section/board for each mode, ranking across all three maze patterns and all kits.
- **Maze Pattern** — one section for each of Maze Patterns 1, 2 and 3 within each mode.
- **Kits** — one section for every pattern × kit within each mode (3 × 5 = 15 per mode).

Maps are **not** part of leaderboard identity. The three maze patterns are.

The Discord layout is:

```text
#solo-runs
#competitions

#1.8-leaderboard-overall
#1.8-leaderboard-mazepattern
#1.8-leaderboard-kits

#1.21-leaderboard-overall
#1.21-leaderboard-mazepattern
#1.21-leaderboard-kits
```

1.8 modes are Original, Modern, Speed and Lagless. 1.21 modes are Original,
Modern and Classic. The five kits are shared by both platforms: Jumper,
Slowballer, Body Builder, Repulsor and Maverick.

The lifetime leaderboard database key is:

`platform + mode + pattern + kit + uuid`

Weekly competition submissions are stored separately and keyed by a unique
submission ID, so repeated attempts never overwrite one another.

### Bot hardening

The bot:

- rebuilds from the **complete solo attempt feed history**;
- keeps lifetime PBs separate from weekly attempt history;
- uses the `submittedAt` timestamp to enforce exact competition week boundaries;
- freezes archived weekly standings so later PBs cannot rewrite completed weeks;
- validates platform, mode, UUID, kit and maze pattern before accepting a submission;
- paginates through complete Discord history;
- retries HTTP 429 and transient 5xx responses;
- migrates an existing `leaderboard.db` automatically, treating its historical rows
  as 1.8.

### Bot setup

1. Create a bot application at the Discord Developer Portal and enable the
   **Message Content Intent**.
2. Invite it to both the old and new Discord servers during migration. It needs
   Send Messages, Embed Links, Read Messages and Read Message History. Manage
   Messages is useful for pinning leaderboard posts.
3. Create the leaderboard channels and `#competitions` shown above.
4. `cd solo/bot && pip install -r requirements.txt`
5. Copy `config.example.json` to `config.json` and set the token, `#solo-runs`
   feed channel, leaderboard channel IDs and competition channel.
6. Run `python monster_bot.py`.

### Migrating the existing #solo-runs

Discord cannot move a channel between servers. The one-time
`migrate_solo_runs.py` utility copies the old `#solo-runs` message history into the
new `#solo-runs` channel. It paginates through the complete history and is resumable,
so it can safely be rerun after an interruption.

Add this to `config.json` temporarily:

```json
"migration": {
  "source_channel": "OLD_SOLO_RUNS_CHANNEL_ID",
  "destination_channel": "NEW_SOLO_RUNS_CHANNEL_ID"
}
```

Then run:

```text
python migrate_solo_runs.py
```

After the copy completes, point `feed_channels` at the new channel and run the
leaderboard bot. Older PB embeds without an explicit platform are treated as 1.8
for backwards compatibility. Historical PB messages are useful for lifetime
leaderboards; only submissions with a timestamp inside a competition's week are
eligible for that week's competition.

The migration utility stores source message IDs in `solo_runs_migration.sqlite3`
so a rerun does not duplicate already-copied messages.

The bot's live database and token configuration are gitignored.
