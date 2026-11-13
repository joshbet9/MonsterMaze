# Testplex Discord announcements and game feedback forums

The existing `monster-bot.service` process handles Testplex Discord publishing so the same bot account is not connected by a second process.

## Channels

The bot defaults to the Testplex guild and channels supplied for this deployment:

- Guild: `1568187449715196035`
- Changelog: `1570610726903611503`
- Server activity: `1570610748282118234`
- Legacy issue index: `1568187896890925167` (Bug Report channel)
- Legacy idea channel: `1568187934140538900`

The bot creates two categories, `Testplex Bug Reports` and `Testplex Ideas`, then creates one forum for each canonical game slug in each category. Selector variants that share a slug (including Monster Maze's legacy mode labels) share the same pair of forums. Each GitHub feedback sub-issue is a forum post/thread in the matching game/type forum. The bot derives the game and report type from the GitHub Issue Form fields and the canonical tracker catalog, not from which tracker parents happen to have a label.

Every report thread mirrors its GitHub description and comments. Human replies in a mapped Discord forum thread are posted as comments on that same GitHub issue. Bot-authored mirrored messages are ignored to prevent loops. Issue edits, state changes, and GitHub comments reconcile immediately through the webhook; an hourly pass recovers missed events. Existing tracker threads remain in the legacy issue index for history.

Each mapped feedback post also carries one Discord forum status tag mirrored from GitHub: `Needs triage`, `Confirmed`, `In progress`, `Implemented`, `Fixed`, or `Won't fix`. The forum channel itself already supplies the game and report type (bug or idea), so those are not duplicated as tags. Status tags are created on demand and update when the issue changes. The first post and later GitHub replies use a compact heading, report/reply text, and separate tracker/GitHub/Discord links instead of a raw issue body with embedded sync markers.

Testers can manage labels in a linked issue thread with `!labels`, `!label <exact GitHub label>`, and `!unlabel <exact GitHub label>`. `!implemented` adds the `implemented` label (creating that repository label the first time if needed) and removes `needs-triage`; it does not close the issue, so a maintainer can verify the fix. The bot posts these controls in both newly created and already-existing Discord-origin report threads during reconciliation. These controls only work in mapped game-feedback threads. The bot needs GitHub Issues read/write permission for label changes and Discord `Manage Channels` to create forum status tags; if Discord tag management fails, GitHub issue intake and label changes continue.

The bot must have `Manage Channels` to provision the forum categories and channels, plus permission to create/send messages in forum threads and manage threads. Testers need permission to post in the forum threads. The fine-grained `TESTPLEX_GITHUB_TOKEN` should be restricted to `joshbet9/testplex` with Repository permissions → Issues set to **Read and write**. The bot uses it to read issue bodies/comments and write comments from Discord. Without it, GitHub's lower unauthenticated public API limit stops a full reconciliation.

## GitHub webhook

Set `TESTPLEX_GITHUB_WEBHOOK_SECRET` in `/etc/monstermaze-api.env` and configure a repository webhook for `joshbet9/testplex` with that same secret. Use the existing HTTPS API hostname with path `/github/webhook`, content type `application/json`, and subscribe to `issues`, `issue_comment`, and `release` events. The API verifies the HMAC signature and repository allowlist before dispatching events to the running bot. Set `TESTPLEX_GITHUB_REPOSITORIES` only if additional repositories should be accepted.

Published stable releases are posted once with the complete GitHub release description split into Discord-sized messages; draft releases and prereleases are ignored. The bot relays the release notes supplied on the GitHub release, so the release description must contain the changes you want announced. Server activity is read from the existing local status API; initial state is recorded silently, then actual starting/online/offline transitions are posted with the starter name when available.

Do not put Discord or GitHub tokens, webhook secrets, or webhook signature values in `config.json`, source control, or chat. Keep them in the Oracle service environment file.
