# Release Process

Monster Maze releases are generated from an immutable Git tag. The goal is to make a release reproducible and remove manual pull/build/package/upload steps.

## Official release

Create and push a semantic version tag from `main`:

```text
git tag v1.0.7
git push origin v1.0.7
```

The GitHub Actions release workflow then:

1. checks out the exact tagged commit;
2. builds and tests the 1.8 implementation;
3. builds and tests the 1.21 implementation;
4. obtains the required server/runtime dependencies;
5. builds the complete fresh-install Solo 1.8 package;
6. builds the complete fresh-install Solo 1.21 package;
7. generates updater manifests and SHA-256 checksums;
8. validates package structure, versions and manifest hashes;
9. publishes the packages and manifests to the GitHub Release for that tag.

The release assets are:

- `MonsterMaze-Solo.zip` — fresh-install 1.8 Solo distribution;
- `1.21-MonsterMaze-Solo.zip` — fresh-install 1.21 Solo distribution;
- `solo-1.8-version.json` — 1.8 updater manifest;
- `solo-1.21-version.json` — 1.21 updater manifest;
- `SHA256SUMS.txt` — release asset checksums.

## Configuration ownership

The release contains a safe, fresh-install Solo configuration. It does not contain production Hyper-V or Fly credentials.

Existing environments retain their own configuration:

- Solo installations preserve player/runtime configuration during updates;
- Hyper-V uses the same canonical server build with its existing production configuration and `soloMode=false`;
- Fly MM18/MM21 use the corresponding canonical server build with their existing production/Fly configuration.

There is one application build per Minecraft version, not a separate gameplay build for each endpoint.

## Updater source integrity

Release manifests contain SHA-256 hashes and immutable source URLs pointing at the exact tagged repository contents. This means an existing Solo installation can update from the published release without depending on mutable `main` files.

## Recovery

Published releases are immutable deployment records. Never rewrite a published tag or release to repair a bad build. Fix the source, create a new version tag, and publish a new release.

Before a structural release-pipeline change, keep a recoverable Git reference to the known-good `main` state.
