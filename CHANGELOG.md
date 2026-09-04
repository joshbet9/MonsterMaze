# Monster Maze Changelog

This file records the user-facing and operational milestones of the Monster Maze recreation project. Detailed technical changes are grouped into coherent Git commits rather than individual debugging steps.

## v1.0.6 — 2026-09-04

### Backend and competitive systems
- Added authenticated backend integration for Minecraft 1.8 and 1.21.
- Made backend leaderboards and personal-best data authoritative across both platforms.
- Added reliable Solo run submission, retry/queue handling and post-submission refreshes.
- Added competitive match tracking and tournament-aware run submission.
- Added tournament bracket handling, elimination tracking, sequencing and regression coverage.
- Added seasonal history, rollover handling and archived season standings.
- Added weekly challenge and competitive leaderboard API integration.

### Hosting and deployment
- Added separate Fly Machines for the public 1.8 and 1.21 servers.
- Added the Minecraft wake gateway and readiness handling.
- Added the private Ubuntu/Hyper-V Solo VM deployment, backup and release updater tooling.
- Added release-controlled server packages and SHA-256 validation.
- Hardened 1.21 ZIP packaging and updater-compatible archive validation.

### Solo and recording
- Made run recording explicitly configurable rather than coupled to Solo Mode.
- Added cross-version recording metadata and PB recovery/export tooling.
- Preserved player data, worlds, run history, server settings and private webhook configuration during updates.

## v1.0.5 — 2026-09-03

- Established the known-good cross-platform Solo recording and submission baseline.
- Finalized the player-facing kit reworks and related presentation changes.
- Added complete-attempt recording for both 1.8 and 1.21, including elimination and quit handling.
- Added Discord submission and weekly competition processing.
- Added the 1.21 Solo distribution and platform-specific leaderboard configuration.

## Earlier project milestones

### Monster Maze gameplay foundation
- Built the shared Monster Maze game lifecycle for Minecraft 1.8 and 1.21.
- Added maze generation, safe pads, monsters, kits, lobby, commands and scoreboard systems.
- Established Solo gameplay and personal-best recording foundations.

### Authentic maps and parity
- Added recovered Mineplex reference maps and reconstruction data.
- Promoted verified map-test gameplay into the authoritative 1.8 implementation.
- Added Mineplex-style mob movement, ghost presentation and gameplay parity work.
- Added release-controlled authentic map definitions and per-map presentation/material handling.

### Solo distribution and community integration
- Added automatic Solo updates with release manifests and SHA-256 verification.
- Added the hierarchical Discord leaderboard system for overall, pattern and kit records.
- Added configuration templates and protected private webhook configuration.
- Added cross-platform 1.21 packaging with Java 21 support.

### VM operations
- Added the Linux Solo submitter service for both Minecraft versions.
- Added automated backup and release-based server updating while preserving runtime state.
- Added deployment/bootstrap documentation for reproducing the private testing environment.

## Versioning notes

- `v1.0.x` releases refer to the player-facing Solo distribution and its supporting server/deployment tooling.
- Minecraft 1.8 and 1.21 releases are published separately when their packaged server distributions differ.
- Backend credentials are deployment secrets and are never stored in this repository.
