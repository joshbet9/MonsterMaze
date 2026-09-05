# Monster Maze Architecture

This document records the intended system boundaries so future contributors and AI agents do not need to rediscover them from implementation details.

## Game implementations

Monster Maze has two actively maintained Minecraft implementations:

- **1.8.8 / Spigot / Java 8** — `1.8/MonsterMazeStandalone/`
- **1.21.x / Paper / Java 21** — `1.21/MonsterMazeStandalone/`

They share gameplay concepts but are separate implementations. The repository favours behavioural parity over source-level duplication.

## Player distribution

The `solo/` tree contains the player-facing distribution and the tooling that builds and updates it.

Conceptually:

```text
Canonical source
      |
      v
 Maven build
      |
      v
 Packaging / manifest generation
      |
      v
 Player release artifact
      |
      +--> Windows Solo updater
      |
      +--> Linux/VM deployment tooling
```

Generated staging directories are not source-of-truth code.

## Public server path

The public server architecture intentionally separates the two Minecraft versions:

```text
Minecraft client
       |
       v
Monster Maze gateway
       |
       +----> MM18 Fly Machine
       |
       +----> MM21 Fly Machine
```

The gateway determines the Minecraft protocol/version and wakes the appropriate Fly Machine. The two versions run on separate Machines because the project has deliberately tested the shared-Machine alternative and found it unsuitable for the required performance.

Fly Machines are treated as ephemeral compute. Server templates and packaged application assets are supplied from the deployment image rather than treating the Machine filesystem as the authoritative persistent store.

## Persistent backend

Persistent competitive and submission data belongs on the private Oracle VM/backend rather than on the ephemeral Fly Machines.

```text
1.8 / 1.21 server
        |
        v
   Run Recorder
        |
        v
 Authenticated backend API
        |
        v
 Persistent competitive data
        |
        v
     SQLite / backend storage
```

The backend is authoritative for submitted runs, personal-best data and competitive state.

## Competitive layer

The competitive layer is separate from gameplay simulation. It includes:

- MMR and seasonal competitive scoring;
- ELO;
- Weekly Competition data;
- Monthly Tournament data;
- challenge state and leaderboards;
- competitive match tracking;
- historical season archives.

Historical season results should remain snapshots rather than being silently recomputed from changed current formulas.

## Discord/community integration

Discord-facing functionality consumes backend/competitive state and provides player-facing commands, leaderboards and notifications. Discord integration should not become the authoritative store for competitive data.

## Evidence and reverse engineering

Recovered Mineplex material and recorded gameplay evidence live under `references/` and related documentation. These are evidence sources for parity work, not automatically executable source code.

When evidence conflicts with an implementation, investigate the discrepancy rather than silently assuming either side is correct.

## Operational boundaries

| System | Role | Persistence expectation |
| --- | --- | --- |
| GitHub | Source, history, documentation, release source | Authoritative |
| Fly MM18 | Public Minecraft 1.8 compute | Ephemeral |
| Fly MM21 | Public Minecraft 1.21 compute | Ephemeral |
| Oracle VM/backend | Persistent API and competitive state | Persistent |
| Player Solo package | Local player runtime | Recreated/updated from releases |

## Change rule

Before changing an architectural boundary, document the reason and verify that the proposed change does not accidentally move authoritative state into an ephemeral or generated layer.
