# 008 — CPU opponent architecture v2

**Status:** Accepted for implementation planning

## Decision

Monster Maze CPU opponents are a production feature of the **1.8 MonsterMazeStandalone server**.

MonsterMazeAI and MonsterMazeEngine are supporting development systems, not runtime dependencies.

The system will use a three-repository model:

- **MonsterMaze** — authoritative live 1.8 game and CPU-player runtime.
- **MonsterMazeAI** — policy development, training, telemetry analysis and player modelling.
- **MonsterMazeEngine** — high-throughput source-faithful simulation, teacher search and evaluation.

We will not introduce a fourth mechanics repository at this stage.

## Rationale

The 1.8 plugin currently owns the authoritative gameplay implementation and contains the Bukkit/NMS integration required by the real server. MonsterMazeEngine already mirrors that gameplay for fast experiments, while MonsterMazeAI contains the current planning and learning work.

Creating another "shared core" before the CPU runtime is working would add another synchronization boundary without solving the immediate problem.

Instead, the projects will share an explicit semantic state/action/profile contract and use differential tests to detect drift.

If, later, the cost of maintaining the mechanics mirror becomes demonstrably larger than extracting a pure Java-8-compatible mechanics component, that extraction can be reconsidered as a separate architectural decision.

## Production CPU design

A CPU competitor consists of:

```
CpuOpponent
  ├── CpuBrain
  │     no Bukkit/NMS
  │     compact direct policy inference
  │
  └── CpuAvatar
        authoritative 1.8 server-side player-like entity
```

The brain selects semantic actions.

The avatar executes them through the same Monster Maze rules used by human players.

The brain never teleports, writes velocity, changes health, consumes a cooldown, or advances game progression directly.

## Human/CPU participant separation

The gameplay layer must eventually distinguish participant kind:

- HUMAN
- CPU

The CPU participant must be visible to gameplay systems that matter for competition inside a match: position, collision, health, kit, Safe Pads, monster interaction and progress.

Human-only systems remain human-only:

- player chat;
- network packet/UI presentation;
- leaderboard submission;
- player account identity;
- Discord/backend personal-best recording.

CPU results must not silently enter human leaderboard/PB pipelines.

## CPU avatar choice

The first implementation target is an NMS-backed player-like avatar isolated behind a `CpuAvatar` interface.

The goal is to minimise game-rule rewrites while ensuring the CPU can participate in the existing Player-oriented mechanics.

The avatar implementation must be replaceable without changing the brain.

Questions such as tab-list visibility, skin presentation, spectator presentation and exact packet suppression belong to the avatar layer.

## Difficulty, attributes and tendencies

The policy receives a profile.

- Difficulty defines a bounded capability envelope.
- Attributes expose independent capability controls.
- Tendencies define behavioural preference.

None of these alter Monster Maze physics.

Do not implement difficulty as arbitrary random failure or mechanic nerfs unless a future design decision explicitly establishes such a behaviour as part of the CPU's intended identity.

## Performance

The 1.8 server main thread remains authoritative.

The production CPU runtime must not perform:

- filesystem access;
- network calls;
- JSON parsing;
- Python/sidecar inference;
- beam search;
- Monte Carlo simulation;
- full-world state copies;
- full monster scans per CPU.

Shared match information is computed once and reduced to compact per-CPU observations.

Policy inference uses primitive numeric data and preloaded model weights.

The first implementation is synchronous and deterministic. Background inference is not introduced until profiling proves it necessary.

## Migration principle

The existing AI planners are not discarded.

They become offline teacher/research systems while a learned direct policy is developed.

This allows the project to retain the large amount of source-faithful planner work already completed without allowing the production runtime to inherit its cost or special-case complexity.

## Acceptance

The architecture is considered successfully adopted when:

1. a 1.8 CPU avatar can participate in a real Monster Maze run;
2. the CPU brain can operate without MonsterMazeAI's current live planner stack;
3. the same exported policy can run in Engine and the 1.8 server;
4. multiple CPUs can run within a measured server budget;
5. difficulty/attributes/tendencies are configuration, not separate bot implementations;
6. CPU gameplay never changes authoritative mechanics;
7. CPU runs remain excluded from human competitive records unless an explicit future feature opts them in.
