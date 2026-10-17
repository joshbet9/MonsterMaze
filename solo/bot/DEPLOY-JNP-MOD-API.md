# JNP Mod Oracle API integration and deployment

## Architecture found in the repository

The intended public API host is the existing Oracle-hosted Discord bot/API process, not the separate Solo Hyper-V VM updater and not the legacy `solo/backend/monster_backend.py` service.

The current repository's Oracle deployment script (`ops/oracle/deploy-release.sh`) expects:

- immutable source releases at `/home/ubuntu/monstermaze/releases/vX.Y.Z`;
- the active release symlink at `/home/ubuntu/monstermaze/current`;
- persistent bot configuration and SQLite database at `/home/ubuntu/monstermaze/data/config.json` and `/home/ubuntu/monstermaze/data/leaderboard.db`;
- the bot virtual environment at `/home/ubuntu/monster-bot/venv`;
- `monster-bot.service` and server-side API environment in `/etc/monstermaze-api.env`;
- the API listener on `127.0.0.1:8090` for local deployment health checks.

The `solo/bot/monster_bot.py` entrypoint starts `api_server.py` and the Discord bot together, using the same database connection factory. Therefore the new community table is created in the existing database; do not deploy a second independent API process or point it at another SQLite file.

The separate `solo/vm/` tree installs/updates the local Solo gameplay servers and Discord submitter. It is not the API deployment path.

**Live-host caveat:** this repository inspection establishes the checked-in deployment contract, but does not by itself prove the real VM's active symlink, service status, database contents, firewall, or public HTTPS URL. The deployment workflow's SSH preflight performs some of those checks when it runs.

## Public JNP Mod API

The mod uses only these unauthenticated client-safe endpoints:

- `GET /api/v1/mod/leaderboard`
- `GET /api/v1/mod/player`
- `POST /api/v1/mod/runs`

The routes are intentionally public because a distributed client must not contain `MM_API_TOKEN`. Authenticated server/competitive endpoints continue using that token from the server environment. Public client-submitted rows live in `community_mod_runs` and return `verified: false`; they must never be used for official MMR, tournament outcomes, prizes, or authoritative server PB tables.

Context should be sent as `MPS` or `MIN` (the backend also accepts `ARCADE`/ `PUBLIC` as aliases for `MIN`). For Monster Maze 1.8 Server PBs, the supported mode filter is `original`, `speed`, or `modern`; Maverick is a supported kit. The UI may combine pattern+kit filters using `kind=combination&pattern=N&kit=Maverick`.

Each upload must have a valid UUID `submissionId`. The client derives a stable UUID from the logical run/player pair and persists pending JSON payloads in a local outbox. The outbox is removed only after the API acknowledges the upload. Retries reuse the same ID, so a lost response cannot create a second row. A local outbox is not proof that the server has received a score.

## Public HTTPS origin and live verification

The gateway/DNS notes identify `monstermaze.duckdns.org` as the Oracle wake hostname and state that the same hostname can serve HTTPS through Caddy on port 443. The intended JNP Mod API origin is therefore `https://monstermaze.duckdns.org`. This is a public URL, not a credential. The deployment's internal curl checks do not prove that Caddy is currently proxying external requests correctly; perform the following from Windows PowerShell after the release has been deployed.

```powershell
$Base = "https://monstermaze.duckdns.org"

# Check public HTTPS + API health.
Invoke-RestMethod "$Base/health" | ConvertTo-Json -Depth 5

# HH, HHC and HHI are separate community contexts after the HHI-enabled release.
Invoke-RestMethod "$Base/api/v1/mod/halloween-havoc/leaderboard?context=HH&kind=runs" |
    ConvertTo-Json -Depth 8
Invoke-RestMethod "$Base/api/v1/mod/halloween-havoc/leaderboard?context=HH&kind=splits" |
    ConvertTo-Json -Depth 8
Invoke-RestMethod "$Base/api/v1/mod/halloween-havoc/leaderboard?context=HHI&kind=runs" |
    ConvertTo-Json -Depth 8
Invoke-RestMethod "$Base/api/v1/mod/halloween-havoc/leaderboard?context=HHI&kind=splits" |
    ConvertTo-Json -Depth 8
Invoke-RestMethod "$Base/api/v1/mod/halloween-havoc/leaderboard?context=HHI&serverType=ALL&kind=bks" |
    ConvertTo-Json -Depth 8
```

The health response should contain `ok: true`. The HH route responses should contain `ok: true`, `source: "community"`, `verified: false`, and a `rows` array. Before PR #38 is deployed, a 404 for the HH routes is expected; it does not by itself mean the hostname is wrong.

If the external health check cannot connect, SSH to the Oracle VM and run these read-only checks:

```bash
curl -fsS http://127.0.0.1:8090/health
sudo systemctl is-active caddy
sudo caddy validate --config /etc/caddy/Caddyfile
sudo grep -R -nE 'monstermaze\.duckdns\.org|127\.0\.0\.1:8090|reverse_proxy' /etc/caddy 2>/dev/null
```

Look for a Caddy site for `monstermaze.duckdns.org` that proxies HTTPS requests to the local API listener on port 8090. Do not paste environment files, API tokens, SSH secrets, or private-key material into a ticket/chat. If Caddy's current file is at a different path, use `sudo systemctl cat caddy` to see the service's configured file path without printing the API environment.

## Halloween Havoc, Challenger and Insanity community API

The shared Oracle API has a separate upload route, `POST /api/v1/mod/halloween-havoc/runs`, and read route, `GET /api/v1/mod/halloween-havoc/leaderboard`. The payload includes one HH, HHC or HHI run and its passed objective splits. Run attempts are stored in `community_hh_runs`; split rows are stored in `community_hh_splits`. They remain unverified community data and never enter the Monster Maze authoritative PB/MMR tables. Queries and uploads preserve context isolation between HH, HHC and HHI.

Full-run boards count only `successful: true` / `runStatus: COMPLETED` runs, with the best result per team shown. Objective-split boards intentionally include valid passed splits even when the attempt later failed or was partial. Group profile requests return the team's best completed run and fastest known split for each objective across all its uploads.

The client should delete the matching local run only after the API replies with `ok: true` and `accepted: true`. A timeout or non-acceptance keeps the local run so it can be retried with the same `submissionId`; duplicate retries are acknowledged idempotently. `MM_API_TOKEN` is not sent by the client and must remain server-side.

HH, HHC and HHI use the same eight-checkpoint canonical objective schema. The API derives canonical checkpoint IDs from the server-observed objective labels for each context, while full runs, splits, group profiles and BKS remain isolated by context and server type.

## Oracle-only release flow (JNP Mod API)

For changes that should reach only the Oracle-hosted bot/API, use a dedicated tag such as `oracle-v1.3.10`. The `.github/workflows/deploy-oracle-only.yml` workflow accepts only `oracle-vX.Y.Z` tags (or an explicitly supplied tag via manual dispatch), checks that the tagged commit is already contained in `main`, runs the focused JNP Mod leaderboard API unit tests, verifies the Oracle SSH/service prerequisites, and calls the existing Oracle deployment script.

This tag namespace is intentionally different from the standard `vX.Y.Z` tags used by `.github/workflows/release.yml`. Do not use a normal `vX.Y.Z` tag for an Oracle-only update: the normal release flow also builds and deploys other targets, including Fly production and Hyper-V integration servers. The Oracle-only workflow has no Fly or Hyper-V credentials or deployment steps.

The Oracle-only flow deploys backend source into the existing Oracle release directory, backs up and validates the current SQLite database, switches the active release, restarts `monster-bot.service`, and runs local API smoke checks for HH, HHI and other public community endpoints. It does not build or distribute the JNP Mod client JAR and does not deploy client-side Monster Maze Helper or Halloween Havoc features. The bot/API process is shared, so its restart may briefly interrupt that process, but this workflow changes only the Oracle endpoint.

### Monster Maze leaderboard data separation

The Mod presents distinct leaderboard sections because they query separate datasets:

- **Community Mod runs:** client-recorded and client-uploaded Monster Maze runs are written to the `community_mod_runs` table, returned by `source=community`, and always remain unverified.
- **Server PBs:** authoritative server-recorded results are read from the existing `runs` table, returned by `source=server`, and marked verified by the API.

The API must not copy community submissions into `runs`, treat client-reported stages as authoritative, or use community records for MMR, tournament results, prizes, or official Server PBs. The Oracle-only workflow does not deploy any Halloween Havoc client code or change its local run data/leaderboard behaviour.

## Standard multi-target release flow


1. Review and merge the JNP Mod API feature PR only after the backend unit-test and validation jobs pass.
2. Create/publish a normal `vX.Y.Z` Monster Maze release from `main`. Do not hot-edit `/home/ubuntu/monstermaze/current`; the release pipeline expects immutable tag-based checkouts.
3. The existing `.github/workflows/deploy-oracle.yml` workflow runs after a successful pushed release-tag build. It checks the Oracle SSH/service prerequisites and invokes the deployment script from that tag.
4. The deployment script first creates a consistent SQLite backup using Python's SQLite backup API, runs `PRAGMA quick_check`, stores the snapshot under `/home/ubuntu/monstermaze/backups/`, and keeps the ten newest release backups. It then switches the `current` symlink and restarts `monster-bot.service`. It checks the authenticated API and public route smoke checks for (a) community leaderboard shape/source and (b) server PB route shape/source. If a route fails, the script attempts to restore the previous current symlink and restart the service.
5. Inspect the deployment workflow result and server logs before configuring the mod. The smoke checks prove route availability and basic response shape only; they do not prove a community submission is visible externally, that HTTPS proxying is correct, or that rate limits suit production.
6. Configure the mod with the public HTTPS base URL (origin only, without an API path, query or embedded credentials). HTTP is accepted by the client only for localhost testing.

## Safe pre-deployment checks

Before enabling uploads for players:

- make a timestamped backup of `leaderboard.db` and verify it is readable;
- confirm `/etc/monstermaze-api.env` still contains the server-only `MM_API_TOKEN`;
- confirm the API public route is reachable through the approved HTTPS endpoint;
- submit one known test player record with a fresh valid submission ID and check that it appears under the requested `MIN` or `MPS` context;
- repeat the exact same submission ID to verify the API returns an idempotent duplicate acknowledgement and the database contains one row;
- confirm the client-reported row remains unverified and never appears in the authoritative server PB/MRR/competitive tables;
- verify Server PBs reads only the existing authoritative `runs` table and returns `verified: true`.

Do not put tokens, private keys, webhook URLs, the public deployment host's private details, or database copies in the mod JAR or Git. Do not run a production deployment merely to test the mod; use a staging database/service or a planned release window.
