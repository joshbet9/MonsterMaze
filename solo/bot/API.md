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

The API listens on `0.0.0.0` on port `8090` by default. Put it behind HTTPS before exposing it to public game servers.

## Endpoints

### Health

`GET /health`

No authentication. Returns a simple readiness response.

### Current weekly challenge

`GET /api/v1/challenge/{platform}`

`platform` is `1.8` or `1.21`.

Returns the bot's current Monday-Sunday Brisbane-time competition combination for that platform:

```json
{
  "ok": true,
  "platform": "1.8",
  "week": "2026-W36",
  "number": 1,
  "mode": "original",
  "pattern": 1,
  "kit": "Jumper",
  "start": "2026-09-01T00:00:00+00:00",
  "end": "2026-09-08T00:00:00+00:00",
  "status": "current"
}
```

### Submit a completed run

`POST /api/v1/runs`

Example body:

```json
{
  "submissionId": "uuid-1750000000000-7-1-Jumper",
  "platform": "1.8",
  "plugin": "1.0.0",
  "name": "Steve",
  "uuid": "00000000-0000-0000-0000-000000000000",
  "mode": "Original",
  "pattern": 1,
  "kit": "Jumper",
  "stage": 7,
  "timeMs": 482000,
  "configHash": "abc123",
  "submittedAt": 1750000000000
}
```

The server stores every attempt in `submissions` and updates the lifetime PB in `runs` when appropriate. Reusing the same `submissionId` is idempotent.

## Solo implementation submissions

The existing Solo distribution submission flow remains separate. Solo installations continue writing `solo-runs/*.json` and using `submit.ps1` to post attempts to Discord. Those Discord messages are still ingested by the same bot database.
