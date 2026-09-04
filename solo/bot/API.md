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
- `#server-status` — hosted server status
- `#matchmaking` — tournament/game matchmaking notifications

The five ranking boards are refreshed by the tournament/competitive Discord scheduler and use the same SQLite data as the API. MMR is labelled all-time; the other four boards are labelled with the current season.

## Solo implementation submissions

The existing Solo distribution submission flow remains separate. Solo installations continue writing `solo-runs/*.json` and using `submit.ps1` to post attempts to Discord. Those Discord messages are still ingested by the same bot database.
