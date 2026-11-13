# Discord ↔ GitHub Bridge

This service connects the Monster Maze Discord community workflow to GitHub Issues.

## Authority

GitHub Issues are the authoritative engineering record. Discord is the community/reporting interface.

The bridge maintains a persistent mapping between Discord threads and GitHub issue numbers. It supports:

- creating a GitHub Issue from a new report/idea thread;
- adding the Discord thread URL to the issue;
- posting the GitHub issue URL back into Discord;
- synchronising GitHub issue state and labels back to the Discord thread;
- forwarding selected GitHub issue comments to Discord;
- avoiding bot feedback loops with explicit bridge markers.
- posting published Testplex GitHub releases to a changelog channel;
- reporting Testplex server start/online/offline transitions to an activity channel;
- creating one Discord thread per GitHub game-feedback tracker and keeping its native sub-issue list synchronized.

The bridge does **not** store production credentials in the repository. All credentials and IDs are environment variables.

## Planned Discord flow

1. A member creates a thread under the configured Bug Reports or Ideas channel.
2. The bridge reads the thread's starter message and creates an issue with a controlled label set.
3. The bridge replies in the thread with the GitHub issue link.
4. GitHub changes are delivered to the bridge through a webhook.
5. The bridge updates the mapped Discord thread when issue labels/state/comments change.
6. A thread per `game-tracker` issue lists its native sub-issues; webhook events and a 10-minute reconciliation update, close, or remove entries.
7. A `release` webhook from the configured changelog repository publishes release notes. Server activity is read from the Oracle status endpoint; the first poll seeds its state and only later state transitions are announced.

## Environment

Required:

- `DISCORD_TOKEN`
- `DISCORD_GUILD_ID`
- `DISCORD_BUG_CHANNEL_ID`
- `DISCORD_IDEA_CHANNEL_ID`
- `GITHUB_TOKEN`
- `GITHUB_REPOSITORY` (for example `joshbet9/MonsterMaze`)
- `GITHUB_WEBHOOK_SECRET`

Optional:

- `PORT` (default `8080`)
- `DATABASE_PATH` (default `data/bridge.sqlite3`)
- `CHANGELOG_REPOSITORY` and `TRACKER_REPOSITORY` (default `joshbet9/testplex`)
- `WEBHOOK_REPOSITORIES` (comma-separated repository allowlist for signed webhook delivery)
- `DISCORD_DESTINATION_GUILD_ID`, `DISCORD_CHANGELOG_CHANNEL_ID`, `DISCORD_ACTIVITY_CHANNEL_ID`, `DISCORD_ISSUE_INDEX_CHANNEL_ID`
- `SERVER_STATUS_URL`, `SERVER_STATUS_INTERVAL`, `SERVER_ACTIVITY_SERVERS`

The GitHub token should have the minimum issue read/write permissions on the configured repositories. Configure a GitHub webhook with the shared secret for `issues`, `issue_comment`, and `release` events on every repository in `WEBHOOK_REPOSITORIES`. The Discord bot must be invited to the target guild and have permission to send messages, create public threads, send messages in threads, and manage threads in the configured channels. Set the destination guild ID and channel IDs explicitly; the bridge rejects configured announcement channels that belong to another guild.

## Deployment

The intended first deployment target is the persistent Oracle VM/backend host, not a Fly game Machine. The bridge needs a stable endpoint for GitHub webhooks and persistent mapping state.

Put the webhook endpoint behind HTTPS and expose only `/github/webhook` publicly. Keep `DATABASE_PATH` on persistent storage; it stores report mappings, tracker-thread IDs, and activity state.
