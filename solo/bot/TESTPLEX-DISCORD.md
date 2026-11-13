# Testplex Discord announcements and issue index

The existing `monster-bot.service` process handles Testplex Discord publishing so the same bot account is not connected by a second process.

## Channels

The bot defaults to the Testplex guild and channels supplied for this deployment:

- Guild: `1568187449715196035`
- Changelog: `1570610726903611503`
- Server activity: `1570610748282118234`
- Game tracker issue index: `1568187896890925167` (Bug Report channel)
- Bug reports: `1568187896890925167`
- Idea submissions: `1568187934140538900`

These values can be overridden in `config.json` or with `TESTPLEX_DISCORD_GUILD_ID`, `TESTPLEX_CHANGELOG_CHANNEL_ID`, `TESTPLEX_ACTIVITY_CHANNEL_ID`, and `TESTPLEX_ISSUE_INDEX_CHANNEL_ID`. The bot checks that announcement channels belong to the configured guild. The bot must be invited to that guild and have permission to send messages, create public threads, send messages in threads, and manage threads.

The issue index creates one persistent thread per GitHub issue labeled `game-tracker`. Each native GitHub sub-issue appears as a linked message showing its current state and labels. Issue webhooks trigger prompt reconciliation; an hourly pass recovers missed events. The bot requires GitHub Issues read access for `joshbet9/testplex`; set `TESTPLEX_GITHUB_TOKEN` in the Oracle service environment for the authenticated API rate limit. Without it, GitHub's lower unauthenticated public API rate limit applies.

## GitHub webhook

Set `TESTPLEX_GITHUB_WEBHOOK_SECRET` in `/etc/monstermaze-api.env` and configure a repository webhook for `joshbet9/testplex` with that same secret. Use the existing HTTPS API hostname with path `/github/webhook`, content type `application/json`, and subscribe to `issues` and `release` events. The API verifies the HMAC signature and repository allowlist before dispatching events to the running bot. Set `TESTPLEX_GITHUB_REPOSITORIES` only if additional repositories should be accepted.

Published stable releases are posted once. Drafts and prereleases are ignored. Server activity is read from the existing local status API; initial state is recorded silently, then actual starting/online/offline transitions are posted with the starter name when available.

Do not put Discord or GitHub tokens, webhook secrets, or webhook signature values in `config.json`, source control, or chat. Keep them in the Oracle service environment file.
