# Monster Maze

A recreation of the Mineplex **Monster Maze** minigame, with player-facing Solo distributions for Minecraft 1.8.8 and 1.21.x plus the supporting competitive, backend, and deployment tooling.

## Repository at a glance

| Area | Purpose |
| --- | --- |
| `1.8/MonsterMazeStandalone/` | Minecraft 1.8.8 / Spigot implementation (Java 8) |
| `1.21/MonsterMazeStandalone/` | Minecraft 1.21.x / Paper implementation (Java 21) |
| `solo/` | Player distribution, updater, gateway, VM tooling and packaging |
| `references/` | Recovered Mineplex source/reference material and investigation data |
| `docs/` | Architecture, operational notes and durable design decisions |
| `AGENTS.md` | Rules for AI agents working on the repository |
| `KNOWN_ISSUES.md` | Confirmed known issues and active limitations |
| `CHANGELOG.md` | User-facing and operational release history |

## Important architecture rule

The 1.8 and 1.21 implementations are **separate codebases representing the same game**. Behavioural equivalence is the goal; identical source structure is not.

When a gameplay mechanic changes:

1. Identify the underlying behaviour.
2. Inspect both implementations where relevant.
3. Check recovered Mineplex references and recorded evidence when available.
4. Implement the version-appropriate equivalent.
5. Build the affected implementation(s).
6. Keep intentional cross-version differences documented.

## Source, builds and releases

Source code is authoritative. Maven `target/` directories and player distribution staging directories are generated output and should not be edited as source.

Official releases are automated from a `vX.Y.Z` Git tag. Push the tag from `main` and GitHub Actions builds, tests, packages, validates and publishes the release assets. See `docs/release-process.md` for the exact release flow.

Player releases are built from the canonical source and published as release artifacts. Release manifests, package contents and checksums must describe the exact files shipped to players.

## Development

Read `AGENTS.md` before making changes. It contains the repository's evidence-first investigation rules, cross-version requirements, testing expectations, deployment mappings and Git safety rules.

For architectural context, see `docs/architecture.md`.

For decisions that should not be rediscovered or accidentally reversed, see `docs/decisions/`.

## Git history and recovery

`main` is the canonical branch. Substantial work should use a short-lived branch and a focused commit or small sequence of focused commits. Avoid destructive history rewriting.

Commits should describe one logical change and use the conventional prefixes documented in `CONTRIBUTING.md`.

A future agent should be able to determine from Git history:

- what changed;
- why it changed;
- which Minecraft version(s) were affected;
- what was built or tested;
- whether gameplay verification remains outstanding.

## Project status

Monster Maze is an active recreation project. Some systems are production-oriented and others remain under active development or reverse engineering. Documentation deliberately distinguishes confirmed behaviour from hypotheses and known limitations.
