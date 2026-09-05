# Release Process

Monster Maze releases are generated from an immutable Git tag. The goal is to make a release reproducible and remove manual pull/build/package/upload/deploy steps.

## Official release

Create and push a semantic version tag from `main`:

```text
git tag v1.0.8
git push origin v1.0.8
```

The GitHub Actions release workflow then:

1. checks out the exact tagged commit;
2. builds and tests the 1.8 implementation;
3. builds and tests the 1.21 implementation;
4. obtains the required server/runtime dependencies;
5. builds the complete fresh-install Solo 1.8 package;
6. builds the complete fresh-install Solo 1.21 package;
7. creates release assets for generated/external updater binaries;
8. generates updater manifests and SHA-256 checksums;
9. validates package structure, versions and manifest hashes;
10. publishes the packages and manifests to the GitHub Release for that tag;
11. promotes the exact Docker image to the Fly registry and rolls it through the existing MM18/MM21 Fly Machines;
12. deploys the same immutable release to the local Hyper-V integration servers.

The release assets are:

- `MonsterMaze-Solo.zip` — fresh-install 1.8 Solo distribution;
- `1.21-MonsterMaze-Solo.zip` — fresh-install 1.21 Solo distribution;
- `solo-1.8-version.json` — 1.8 updater manifest;
- `solo-1.21-version.json` — 1.21 updater manifest;
- `MonsterMaze-Solo-1.8-plugin.jar` — tested 1.8 plugin binary used by the updater;
- `MonsterMaze-Solo-1.21-plugin.jar` — tested 1.21 plugin binary used by the updater;
- `MonsterMaze-Solo-1.21-Paper.jar` — pinned Paper runtime used by the 1.21 Solo package;
- `MonsterMaze-Solo-1.21-ProtocolLib.jar` — pinned ProtocolLib runtime used by the 1.21 Solo package;
- `MonsterMaze-Server-1.8.zip` — canonical hosted MM18 server artifact;
- `MonsterMaze-Server-1.21.zip` — canonical hosted MM21 server artifact;
- `DOCKER-IMAGE.txt` — published GHCR image tag and digest;
- `SHA256SUMS.txt` — release asset checksums.

Generated binaries and downloaded third-party runtime dependencies are release assets rather than repository source files. Updater manifest entries for those files point to the immutable asset belonging to the same release tag. Source-controlled scripts and map files point to the immutable tagged repository contents.

## Deployment promotion

The release is the promotion boundary. `main` is continuously validated, but production and Hyper-V are only promoted from an immutable release tag.

```text
main
  |
  v
Validate
  |
  v
vX.Y.Z release
  |
  +----> GitHub Release assets
  |
  +----> Fly production
  |        +-- MM18
  |        +-- MM21
  |
  +----> Hyper-V integration
           +-- MM18
           +-- MM21
```

### Fly production

The release image is first built and published to GHCR. The deployment job then copies that exact image to `registry.fly.io/monstermaze:<tag>` and runs `fly image update` against the existing `monstermaze` app. This updates all existing Fly Machines while preserving their Machine-specific command/configuration, which is important because MM18 and MM21 use the same image with different entrypoint arguments. The deployment is therefore a promotion of the already-tested image, not a second application build.

The Fly deployment requires a repository/environment secret named `FLY_API_TOKEN`. It should be a deploy token scoped to the `monstermaze` app, not a full personal token.

### Hyper-V integration

Hyper-V is a release promotion target for integration testing, not a player distribution and not a production authority. The deployment job connects to the Hyper-V Linux VM over SSH and runs the release-pinned `ops/hyperv/deploy-release.sh` script.

The script:

- downloads `MonsterMaze-Server-1.8.zip` and `MonsterMaze-Server-1.21.zip` from the exact release;
- verifies them against `SHA256SUMS.txt` before installation;
- preserves environment-owned worlds, logs, `server.properties`, and plugin configuration;
- guarantees `solo-mode:false` for both servers;
- migrates the two instances to explicit systemd services if necessary;
- starts both services and verifies they are active.

The Hyper-V deployment requires these repository/environment secrets:

- `HYPERV_SSH_HOST`
- `HYPERV_SSH_USER`
- `HYPERV_SSH_PORT` (optional; defaults to `22`)
- `HYPERV_SSH_PRIVATE_KEY`
- `HYPERV_SSH_KNOWN_HOSTS`

The SSH account must have passwordless `sudo` for the deployment operations. SSH host-key verification is strict; the known host key is stored as a GitHub secret rather than using an insecure `StrictHostKeyChecking=no` bypass.

## Configuration ownership

The release contains a safe, fresh-install Solo configuration. It does not contain production Hyper-V or Fly credentials.

Existing environments retain their own configuration:

- Solo installations preserve player/runtime configuration during updates;
- Hyper-V uses the canonical server build with environment-owned state and `soloMode=false`;
- Fly MM18/MM21 use the canonical hosted image with `soloMode=false`.

There is one application build per Minecraft version, not a separate gameplay build for each endpoint.

## Solo distribution and updating

The Solo packages are player-facing Windows distributions. Both launchers automatically run their bundled updater before starting the server. The 1.8 updater uses `releases/latest/download/solo-1.8-version.json`; the 1.21 updater uses the corresponding `solo-1.21-version.json`. The updater compares SHA-256 values and downloads only changed files.

The Solo templates are explicitly validated as `solo-mode:true`. The updater protects player-owned configuration such as `server/plugins/MonsterMazeStandalone/config.yml` and `server/server.properties`, so a release update does not silently convert an existing Solo installation into a hosted configuration.

The normal player flow is therefore:

```text
GitHub Release
      |
      +----> MonsterMaze-Solo.zip
      |          |
      |          v
      |       Player install
      |          |
      |          v
      |       launcher/play.bat
      |          |
      |          v
      |       updater checks latest release
      |
      +----> 1.21-MonsterMaze-Solo.zip
                 |
                 v
              Player install
                 |
                 v
              launcher/play.bat
                 |
                 v
              updater checks latest release
```

A fresh install is downloaded from the GitHub Release. Subsequent releases do not require players to manually download the full package: launching the game performs the update check first.

## Updater source integrity

Release manifests contain SHA-256 hashes and immutable URLs. Source-controlled files resolve to the exact tagged repository contents, while generated/external binary files resolve to release assets attached to that exact tag. The updater verifies the SHA-256 before replacing any file.

## Recovery

Published releases are immutable deployment records. Never rewrite a published tag or release to repair a bad build. Fix the source, create a new version tag, and publish a new release.

Before a structural release-pipeline change, keep a recoverable Git reference to the known-good `main` state.
