# Monster Maze — SOLO

A local, single-player build of Monster Maze for crowd-sourced leaderboards.

> **For players:** read `HOW_TO_PLAY.txt` in this folder — it's the plain,
> non-technical instructions. This README is for the developer/owner.

## Distributing to players

Run `pack.bat` (double-click) to assemble `solo-dist.zip` — a ready-to-play copy
that bundles the Java 8 runtime, the spigot server jar, the plugin, the launcher,
and the player instructions. The zip is what you send; a player just unzips it and
double-clicks `launcher\play.bat`. (Set the `$JDK8` / `$SPIGOT` paths in
`pack.ps1` first.)

> **Webhook note:** the webhook URL ships inside `submitter\config.ps1` once you
> set it. Only hand the zip to people you trust — anyone with it can post to your
> Discord channel. For outsiders, collect the `.json` and post it yourself.

## For the developer

- `solo/server/` — a packaged 1.8.9 Spigot server preconfigured for solo play.
  - `plugins/MonsterMazeStandalone/config.yml` sets `solo-mode: true`
    (and the default mode).
  - `server.properties`, `bukkit.yml`, `spigot.yml`, `eula.txt` mirror the
    working 1.8 sandbox (`C:\monstermaze_test`).
- `solo/launcher/` — `play.bat` / `stop.bat` / `config.bat`.
- `solo/submitter/` — routes `solo-runs/*.json` to the per-mode Discord webhooks.
- `solo/backend/` — *optional* stray Python leaderboard service; **not used** with
  the pure-webhook setup and not shipped to players.

### How the solo record works

The plugin (`Me.MonsterMazeStandalone`) has a `solo-mode` config flag. When a solo
run ends, `RunRecorder` writes a JSON record **only if the run set a new personal
best for (mode, pattern, kit)** to `plugins/MonsterMazeStandalone/solo-runs/`:

```json
{
  "schema": "1",
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

There is **no replay, seed pinning, or anti-cheat** — per the trust-based model,
participants record their own video. `configHash` simply lets the leaderboard
filter out runs made on a divergent config (e.g. a modified timer). The maze
layouts are shared from the plugin itself, so no seed is needed.

### Submitting runs

Runs are **PB-only** (mode+pattern+kit). Each PB is written to `solo-runs\`, then
`submit.ps1` posts it to the **Discord webhook for that run's mode** (config in
`submitter\config.ps1`). Discord hosts everything — no server, no public IP.

**Config (`submitter\config.ps1`):**
```powershell
$WEBHOOKS = @{
  "modern"   = "https://discord.com/api/webhooks/.../... "
  "speed"    = "..."
  "lagless"  = "..."
  "original" = "..."
}
$DEFAULT_WEBHOOK = "..."   # e.g. your #solo-runs feed (fallback)
```
- Create one webhook per leaderboard channel and paste the URLs by mode key.
- The submitter routes each PB to the webhook matching the run's mode; a mode
  without its own webhook falls back to `$DEFAULT_WEBHOOK`.

**Setup**
1. Create webhook(s): channel → Integrations → Webhooks → New Webhook → copy URL → paste into `config.ps1`.
2. Run `submitter\submit.bat` (or `submit.ps1` via Scheduled Task) after playing.

A run is archived to `submitted\` only after a successful post, so a network
failure never loses a PB — just re-run.

`configHash` in the embed lets the channel spot a run made on a divergent config
(e.g. a modified timer) — it's just hygiene, not distrust.

### Optional: ranked leaderboard bot (`solo/bot/`)

The webhook flow above gives you a **feed** of PB messages. If you want a real,
auto-updated **ranked board per pattern + kit** in each mode channel, run the
Discord bot in `solo/bot/`:

- It **watches the feed channel(s)** for PB embeds, extracts each run, stores the
  best (mode, pattern, kit, player) in SQLite, and maintains a **pinned standings
  message per (mode, pattern)** — one ranked section per kit — in that mode's
  leaderboard channel.
- It's a *client* that connects **out** to Discord, so it needs **no public IP**
  and no open ports. It just needs to keep running (a free-tier VPS works).

Setup:
1. Create a bot app at https://discord.com/developers/applications → New
   Application → Bot → copy the token. Enable the **Message Content Intent**.
2. Invite it to your server (Send Messages, Embed Links, Read Messages, Read
   Message History, Manage Messages to pin/edit standings).
3. `cd solo/bot && pip install -r requirements.txt`
4. `cp config.example.json config.json`, fill in:
   - `feed_channels`: the channel(s) PBs are posted to (e.g. `#solo-runs`).
   - `mode_channels`: each mode's leaderboard channel ID.
5. `python monster_bot.py` (run under systemd/tmux for always-on).

`.gitignore` excludes `config.json` and `leaderboard.db` (they hold live data).

### Rebuilding the plugin

Work happens in the authoritative 1.8 tree
(`C:\Users\Josh\MonsterMaze\1.8\MonsterMazeStandalone`), then copy the jar:

```
mvn -q package -DskipTests
copy 1.8\MonsterMazeStandalone\target\MonsterMazeStandalone.jar solo\server\plugins\
```

> Note: this file is a distribution template. Except for `config.yml` and the
> launcher scripts, game code lives in the `1.8/` plugin, not here.
