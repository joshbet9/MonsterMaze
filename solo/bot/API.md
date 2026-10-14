# Monster Maze server API

The Discord bot is the authoritative source for public-server leaderboard and competition data.
The API runs alongside `monster_bot.py` and uses the same `leaderboard.db` as the Discord bot.

## Security

Set a strong shared secret in the bot environment:

```text
MM_API_TOKEN=change-this-to-a-long-random-secret
```

The public game servers send `Authorization: Bearer <token>` on every authenticated request.
Do not put the token in Git.

The API listens to `0.0.0.0` on port `8090` by default. Put it behind HTTPS before exposing it to public game servers.

## Endpoints

## JNP Mod (public, client-reported) endpoints

The 1.8.9 JNP Mod can optionally share completed Monster Maze attempts and read leaderboard data. These routes are public and deliberately **do not use `MM_API_TOKEN`**; the mod must never contain the server's shared API secret.

- `GET /api/v1/mod/leaderboard?source=community&context=MPS&mode=all&kind=overall&filter=all` returns client-reported attempts. `context` is `MPS`, `MIN`/(`ARCADE` alias), or `ALL`; `mode` can be `all`, `original`, `modern`, `speed`, `classic`, or `unknown`; `kind` is `overall`, `pattern`, `kit`, or `combination`.
- `GET /api/v1/mod/leaderboard?source=server&platform=1.8&mode=original&kind=overall&filter=all` reads the existing authoritative server-plugin PB table. The source is kept separate from client reports. For 1.8, modes are `original`, `speed`, and `modern`; Maverick is a supported kit filter. Use `kind=combination&pattern=2&kit=Maverick` to combine both filters.
- `GET /api/v1/mod/player?source=community&uuid=<uuid>` or `?source=server&uuid=<uuid>` returns a player's rows for the selected source. Optional context/mode/pattern/kit parameters scope a profile to the visible filters.
- `POST /api/v1/mod/runs` accepts a client-reported Monster Maze result. It has payload-size, username/UUID/context/stage validation and basic IP/player rate limits. It accepts `MPS` or `MIN` contexts (with `ARCADE`/ `PUBLIC` aliases normalized to `MIN`) and stores results in `community_mod_runs`, never in authoritative `runs` or `submissions`. The required `submissionId` must be a UUID. Repeating a successfully stored ID returns an accepted duplicate acknowledgement before rate limiting, so a durable client outbox can retry after a lost response without creating a second row.

Monster Maze client submissions include optional `startedAt` Unix epoch milliseconds: the time the client first detected the active game, distinct from `timeMs`, which remains the player's duration until elimination/round end. New submissions store this value in `community_mod_runs.started_at` and return it as `startedAt` in Community leaderboard/profile rows. Existing database rows are migrated with `started_at=0` (not recorded/unknown); their start time cannot be inferred from the previous upload timestamp. The admin CLI shows both `STARTED (UTC)` and `SUBMITTED (UTC)` so same-player records from one round can be compared without treating upload time as game start. Older client builds may omit `startedAt` and remain accepted for backward compatibility.

When a new client submission has a start timestamp, the API checks for the same player UUID and context within 30 seconds of an already stored start time. A match is acknowledged as accepted/duplicate rather than inserting a second row; an otherwise unknown kit or pattern may be filled from the second observer's stronger evidence. This allows different players' mods to upload observations from the same round without multiplying each participant's entry. It is a timestamp-based heuristic, not an authoritative Mineplex game ID; inspect near-boundary cases manually.

- `GET /api/v1/mod/game?uuid=<player UUID>&startedAt=<epoch milliseconds>&context=MPS` returns all stored Monster Maze participant rows with start timestamps within ±30 seconds of the selected record, for a Game Info view. Context may be `MPS` or `MIN` (displayed as ARCADE in the client). The endpoint returns unverified community observations only.

- `POST /api/v1/mod/halloween-havoc/runs` accepts one client-reported Halloween Havoc (`HH`) or Halloween Havoc Challenger (`HHC`) attempt, including its completed objective splits. It validates UUIDs, context, status, timings, quartet members, split ordering and request size; rate-limits accepted uploads per player; and returns explicit `accepted: true` acknowledgement. The upload is idempotent: retrying the same `submissionId` for the same player returns a duplicate acknowledgement before rate limiting without writing duplicate run/split rows. Records live in `community_hh_runs` and `community_hh_splits`, separate from authoritative Monster Maze data.
- `GET /api/v1/mod/halloween-havoc/leaderboard?context=HH&kind=runs` returns the best successfully completed full run per team. Use `context=HHC` for Challenger. `kind=splits` returns each team's best section split and **includes splits from failed/partial attempts**; `objective=<encoded objective>` filters to one objective. `kind=group&teamSignature=<signature>` returns a team's best completed full run and best-known split per objective. Optional `serverType=STANDARD|MPS|ALL` separates those contexts. All returned rows are client-reported and marked `verified: false`.
 
**Trust boundary:** client-reported stages are not authoritative. Modified clients can fabricate scoreboards or submissions. Do not use community rows for MMR, tournaments, rewards, or the server's official PBs. They are an opt-in community comparison only. Existing authenticated server-plugin endpoints retain their current security model.

Before deploying, put the API behind HTTPS and test the new endpoints on the Oracle VM using its existing API service/SQLite database. A leaderboard base URL can be configured in the mod; no credential is required in the client.


### Health

`GET /health`

No authentication. Returns a simple readiness response.

### Current weekly challenge

`GET /api/v1/challenge/{platform}`

`platform` is `1.8` or `1.21`.

Returns the bot's current Monday-Sunday Brisbane-time competition combination for that platform.

### Current weekly challenge standings

`GET /api/v1/challenge/{platform}/leaderboard`

Returns the top 10 standings from the same `competition_rows()` query used by the Discord competition display. This is weekly competition data, not lifetime leaderboard/PB data.

### Submit a completed run

`POST /api/v1/runs`

The server stores every attempt in `submissions` and updates the lifetime PB in `runs` when appropriate. Reusing the same `submissionId` is idempotent.

### Submit a completed multiplayer game

`POST /api/v1/matches`

This is the authoritative multiplayer result endpoint. A server sends one immutable match containing every participant's placement and server-tick elimination time.

```json
{
  "matchId": "uuid",
  "platform": "1.8",
  "mode": "modern",
  "pattern": 1,
  "kit": "mixed",
  "startedAt": 1750000000000,
  "endedAt": 1750000123456,
  "players": [
    {"uuid":"...","name":"Alice","placement":1,"eliminationTick":-1},
    {"uuid":"...","name":"Bob","placement":2,"eliminationTick":1832},
    {"uuid":"...","name":"Carol","placement":3,"eliminationTick":1832}
  ]
}
```

Placement is authoritative server game state. Equal elimination ticks represent a tie; no HTTP arrival order or client timestamp is used to break ties. The backend calculates seasonal ELO from the placements using pairwise ELO with K=32.

If a completed 1v1 result exactly matches an active `ready`/`active` tournament bracket match for the current season, the backend automatically associates it with that tournament match. The next BO3 game number is derived from the current series wins, so the existing server match client does not need tournament-specific payload fields.

On the hosted Minecraft server, the tournament coordinator polls the backend and automatically starts a ready bracket match when both assigned players are online and they are the only non-spectating players online. This deliberately reuses the normal `GameManager` and `CompetitiveMatchTracker` game/result pipeline. If an opponent is offline, the assigned player remains in the lobby and can use `/mm tournament match` to see the waiting match; an unrelated player cannot join the tournament game.

### Current season

`GET /api/v1/season/current`

Returns the current 13-week season and all players with their raw seasonal ELO, weekly points, tournament points, normalized components, and MMCL score.

### Historical seasons

`GET /api/v1/seasons`

Returns every season, newest first, including its ID, season number, dates, status, and finalization timestamp.

`GET /api/v1/seasons/{id}`

Returns the complete stored historical snapshot for a season. Archived season values are read from the stored `season_players` rows and are not recalculated when this endpoint is requested.

`GET /api/v1/seasons/{id}/leaderboard`

Returns the historical MMCL leaderboard for that season (top 25).

`GET /api/v1/seasons/{id}/leaderboard/{kind}`

Returns a historical leaderboard for `mmcl`, `elo`, `weekly`, or `tournament` (top 25). These values are the final stored seasonal values for archived seasons.

`GET /api/v1/seasons/{id}/tournaments`

Returns all tournaments belonging to that season, including registration/start timestamps, status, bracket size, and the top four placements where available.

`GET /api/v1/seasons/{id}/player/{uuid}`

Returns that player's stored stats for the specified season, or `player: null` if they did not participate in it.

`GET /api/v1/player/{uuid}/seasons`

Returns up to 100 seasons of that player's competitive history, newest first. This includes raw ELO/Weekly/Tournament values, all normalized components, MMCL, season status, and finalization time.

Season rollover is automatic when the 13-week current season reaches its end. The old season is marked `archived`, its final values remain in `season_players`, and a new current season is created. MMR remains permanent/all-time and is not reset by season rollover.

### Seasonal leaderboards

- `GET /api/v1/mmcl/leaderboard`
- `GET /api/v1/elo/leaderboard`
- `GET /api/v1/weekly/leaderboard`
- `GET /api/v1/tournament/leaderboard`
- `GET /api/v1/mmr/leaderboard`

MMR is permanent/all-time. ELO, Weekly, Tournament, and MMCL are seasonal.

### Player competitive stats

- `GET /api/v1/mmcl/player/{uuid}`
- `GET /api/v1/mmr/player/{uuid}`

The MMCL player response contains seasonal ELO, Weekly points, Tournament points, all three normalized components, and the final weighted MMCL value.

MMCL is calculated as:

```text
ELO component        × 40%
Weekly component     × 30%
Tournament component × 30%
```

Each component is normalized against the current season leader for that component. MMR is not an MMCL component.

### Tournament state

`GET /api/v1/tournament/current`

Returns the current non-completed tournament for the current season, including registrations and generated bracket matches. It returns `tournament: null` when there is no current tournament.

`GET /api/v1/tournament/{id}`

Returns a specific tournament and its bracket.

`GET /api/v1/tournament/player/{uuid}`

Returns the player's currently playable tournament match, or `match: null` if the player has no active match. When a match exists, the response includes the assigned player UUIDs, their stored Minecraft names, current series wins, best-of value, round/slot, and match status.

`GET /api/v1/tournament/leaderboard`

Returns the current season's accumulated tournament points leaderboard.

Tournament brackets are dynamically sized to the registrations using the next power-of-two bracket size, with byes. Tournament matches are best-of-3; each individual game remains a separate multiplayer ELO event. Tournament game numbers must be recorded sequentially, and a tournament result must be a 1v1 placement of first versus second.

## Discord competitive channels

The bot can maintain five persistent ranking boards using the `competitive_channels` configuration:

```json
"competitive_channels": {
  "mmcl": "mmcl-rankings",
  "mmr": "mmr-rankings",
  "elo": "elo-rankings",
  "weekly": "weekly-rankings",
  "tournament": "tournament-rankings"
}
```

Recommended structure:

- `#competitions` — weekly competitions and tournament announcements
- `#mmcl-rankings` — headline MMCL Score ranking, including its three normalized components
- `#mmr-rankings` — permanent/all-time MMR ranking
- `#elo-rankings` — current-season ELO ranking
- `#weekly-rankings` — current-season weekly score ranking
- `#tournament-rankings` — current-season tournament points ranking
- `#tournament-registration` — registration instructions/automation
- `#tournament-chat` — tournament discussion
- `#tournament-results` — completed tournament results
- `#season-archive` — pinned final season summaries and historical champions
- `#server-status` — hosted server status
- `#matchmaking` — tournament/game matchmaking notifications

The five ranking boards are refreshed by the tournament/competitive Discord scheduler and use the same SQLite data as the API. MMR is labelled all-time; the other four boards are labelled with the current season.

## Solo implementation submissions

The existing Solo distribution submission flow remains separate. Solo installations continue writing `solo-runs/*.json` and using `submit.ps1` to post attempts to Discord. Those Discord messages are still ingested by the same bot database.
