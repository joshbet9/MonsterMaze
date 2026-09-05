# Release Process

The release process exists to make a player release reproducible and traceable to a specific source state.

## Principles

1. Build from a known Git commit.
2. Build the affected Minecraft implementation from canonical source.
3. Run automated validation before publishing.
4. Generate the player package from the packaging script rather than editing a staged package by hand.
5. Generate the updater manifest from the exact package contents.
6. Verify hashes and required package paths.
7. Publish the release artifact and record the release version in the changelog.
8. Keep the Git tag/release as the immutable reference for what players received.

## Release versus development state

A release may represent a stable subset of `main`; development work after a release does not invalidate the historical release artifact.

Player updaters must not depend on an accidentally stale working-tree manifest. The manifest used by a released package must correspond to the exact files and version that package contains.

## Pre-release checklist

- [ ] Working tree is clean or the release commit is explicitly identified.
- [ ] Relevant 1.8 build passes.
- [ ] Relevant 1.21 build passes when included in the release.
- [ ] Packaging completes without ignored failures.
- [ ] Generated package contains the expected files.
- [ ] Generated manifest hashes match the packaged files.
- [ ] Package archive structure is validated.
- [ ] Changelog is updated.
- [ ] Release notes identify the user-facing changes.
- [ ] No unrelated development output is included.

## Recovery

Never rewrite an already-published release to silently change its contents. If a release is wrong, publish a corrected release with a new version and explain the correction.

This preserves a straightforward chain:

```text
Git commit -> release tag -> package -> manifest -> installed client
```

A future agent should be able to trace a player package backwards through that chain.
