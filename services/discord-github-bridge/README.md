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

The bridge does **not** store production credentials in the repository. All credentials and IDs are environment variables.

## Planned Discord flow

1. A member creates a thread under the configured Bug Reports or Ideas channel.
2. The bridge reads the thread's starter message and creates an issue with a controlled label set.
3. The bridge replies in the thread with the GitHub issue link.
4. GitHub changes are delivered to the bridge through a webhook.
5. The bridge updates the mapped Discord thread when issue labels/state/comments change.

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

The GitHub token should have only the repository permissions needed to create/update issues. The Discord bot should have access to the two reporting channels and permission to create/send messages in their threads.

## Deployment

The intended first deployment target is the persistent Oracle VM/backend host, not a Fly game Machine. The bridge needs a stable endpoint for GitHub webhooks and persistent mapping state.

A future deployment should put the webhook endpoint behind HTTPS and expose only `/github/webhook` publicly.
