# Monster Maze CPU Opponent Architecture v2

## Purpose

The product requirement is for the Minecraft 1.8 Monster Maze plugin/server to run CPU opponents that behave like real competitors and expose configurable difficulty, attributes, and tendencies.

The CPU opponent is a **runtime feature of MonsterMaze**. MonsterMazeAI and MonsterMazeEngine are development systems used to create, train, evaluate, and validate the CPU intelligence.

The server must never depend on the training environment, a Python process, a search planner, or a network sidecar during normal gameplay.

## Repository responsibilities

```
MonsterMaze
  authoritative 1.8 gameplay
  CPU opponent manager
  CPU avatar/body
  live observation adapter
  action execution
  player-facing configuration

MonsterMazeAI
  intelligence development
  training
  policy export
  telemetry analysis
  player modelling
  runtime-compatible policy implementation

MonsterMazeEngine
  fast headless 1.8 mechanics mirror
  large-scale rollout generation
  teacher/search evaluation
  policy benchmarking
  trace comparison
```

MonsterMaze remains the final authority for what actually happens in a live game.

## Brain/body boundary

A CPU opponent is split into two parts:

```
CpuOpponent
   |
   +--> CpuBrain
   |      observation + profile
   |            |
   |            v
   |       policy inference
   |            |
   |            v
   |       semantic action
   |
   +--> CpuAvatar
          authoritative Minecraft 1.8 entity/player
          movement, yaw, jump, ability execution
```

The brain must not hold Bukkit/NMS references.

The avatar must not contain strategic AI.

This lets the same brain be tested against MonsterMazeEngine without running Minecraft, while the real server remains authoritative over physics, collisions, health, pads, mobs, and game progression.

## Runtime control loop

Every live CPU opponent follows:

```
authoritative server state
        |
        v
CpuObservationBuilder
        |
        v
detached compact Observation
        |
        +--> profile vector
        |
        v
CpuBrain
        |
        v
semantic Action
        |
        v
CpuActionProjector
        |
        v
CpuAvatar
        |
        v
Minecraft physics / Monster Maze mechanics
```

The observation is detached from Bukkit objects. No Bukkit object is retained inside the brain between ticks.

### Two decision rates

The policy is hierarchical.

**Tactical/strategic decision:** normally 5 Hz (every 4 ticks), with an immediate refresh on important events such as pad transition, major knockback, ability state change, or death.

**Locomotion decision:** 20 Hz. This is a small model and must remain cheap enough to run synchronously on the server thread.

A tactical decision selects intent such as:

- next pad / route family;
- risk posture;
- monster interaction posture;
- ability intent;
- contest/overtake intent when multiple players are present.

The locomotion policy turns that intent plus the current physical state into per-tick movement.

This replaces expensive live beam-search/receding-horizon planning.

## Action contract

The runtime action is semantic and source-valid:

- forward intent;
- strafe intent;
- yaw delta;
- jump;
- sprint;
- primary/enhanced ability request.

No action is allowed to directly change position, health, velocity, cooldowns, or game progression.

The action projector is deliberately narrow. It only prevents actions that are impossible or invalid in the authoritative state; it does not decide the optimal route.

## Observation contract

The CPU observation contains only information available to a live competitor:

### Self

- position and velocity;
- yaw;
- grounded/jump state;
- health and maximum health;
- current kit;
- jump charges;
- ability resources and cooldowns;
- current/next safe pad;
- game mode/stage/timer.

### Maze

- current physical-floor neighborhood;
- active objective;
- preview/old pad context;
- static layout identifier;
- compact route/edge context.

### Threats

- nearby monsters with position, velocity, state and time-to-contact estimates;
- nearby competing players with relative position, velocity, progress and pad status.

### Profile

- capability attributes;
- behavioural tendencies.

The server does not send raw 99x99 grids or the full monster list into the model for every bot. Shared match state is built once and reduced to each bot's compact local observation.

## Difficulty, attributes and tendencies

The architecture separates **capability** from **personality**.

### Capability attributes

Examples:

- movement precision;
- turn precision;
- reaction latency;
- planning horizon;
- recovery skill;
- monster awareness;
- ability timing;
- route execution consistency.

These are constraints on how well the same underlying intelligence can be executed.

### Tendencies

Examples:

- risk tolerance;
- route preference;
- monster-contact preference;
- ability conservation;
- aggression;
- jump preference;
- direction-change style;
- pad contest preference;
- overtaking preference.

Tendencies change decisions, not game mechanics.

### Difficulty

Difficulty is a preset over capability limits and behavioural constraints. A difficulty preset must never modify the actual Monster Maze physics.

Profiles are represented as numeric vectors so the same learned policy can be conditioned on different configurations.

## Multiple CPU opponents

The manager owns one stateful brain per CPU player, but expensive shared work is computed once per match:

```
Match tick
  |
  +--> snapshot shared maze/mob/player context
  |
  +--> compact threat summaries
  |
  +--> Bot A inference
  +--> Bot B inference
  +--> Bot C inference
  |
  +--> execute all actions
```

CPU opponents must observe human and CPU competitors, because first-to-pad rewards and movement interference are part of the game.

The manager must not run a separate full pathfinder or monster simulation for each bot on every tick.

## Server performance rules

The runtime has the following hard design rules:

1. No Python, socket sidecar, HTTP call, file read, or JSON parsing in the gameplay loop.
2. No Monte Carlo rollout, beam search, A*, or full-world copy in the gameplay loop.
3. Models are loaded once and retained in memory.
4. Feature buffers are reused rather than allocated each tick.
5. Static maze information is precomputed per layout.
6. Nearby monsters/players are supplied through a shared spatial index or cached local query, not repeated full scans.
7. The bot is allowed to reuse its previous locomotion action for one or more ticks if the model result is unchanged.
8. If inference exceeds its measured budget, the server keeps the previous valid action rather than blocking the game loop.
9. All NMS/Bukkit mutation stays on the server thread.

The final CPU budget must be measured with multiple simultaneous bots on the actual 1.8 server workload before being considered complete.

## Failure containment

If observation is incomplete, the brain returns a conservative valid action.

If inference fails, the previous valid action or a controlled stop is used.

If the CPU avatar becomes desynchronised from the brain, the avatar is re-anchored from authoritative server state. The brain never writes corrective coordinates directly.

## Integration points in GameManager

CPU lifecycle follows the existing game lifecycle:

```
startGame
  -> create CPU opponents
  -> assign kits/profiles
  -> spawn/attach CPU avatars

STARTING
  -> allow configured pre-start behaviour only

beginLive
  -> enable CPU decisions

every live tick
  -> build shared match observation
  -> ask manager for actions
  -> execute actions

pad transition
  -> notify all CPU brains

player elimination
  -> remove/reset corresponding CPU brain

forceStop
  -> despawn avatars
  -> release all CPU state
```

The CPU system must not duplicate GameManager's pad, timer, health, monster, kit, or stage logic.

## Avatar implementation

The architecture intentionally does not bind the brain to one fake-player technique.

The first implementation should use a real server-side player-like avatar abstraction capable of:

- receiving movement input;
- having a yaw;
- colliding with monsters;
- standing on Safe Pads;
- receiving kit effects;
- participating in player progression and first-to-pad logic;
- being visible as a named competitor.

The NMS implementation belongs entirely behind `CpuAvatar`.

This is the only area where a future change of fake-player/NPC technique should require substantial implementation work.

## Telemetry

Every CPU tick may optionally record a compact debug record:

- match/run identifier;
- CPU profile identifier;
- stage/pad;
- selected intent;
- action;
- outcome/event;
- inference time;
- model version.

Production telemetry must be sampled or disabled by default.

Training telemetry remains a development concern and should not be written to disk by the public server on every tick.

## Acceptance gates

The server-side implementation is not considered complete merely because a bot can move.

Required gates are:

1. CPU avatar can participate in a real 1.8 Monster Maze match.
2. CPU can complete pad transitions without special first-pad hacks.
3. CPU uses the same kit/mechanics code as human players.
4. CPU can observe and react to monsters.
5. CPU can observe competing players.
6. Multiple CPU opponents can run concurrently inside the measured server budget.
7. Difficulty changes behaviour/capability without changing mechanics.
8. Attributes are independently configurable.
9. Tendencies produce reproducible behavioural differences.
10. A trained policy exported from MonsterMazeAI can execute unchanged in the server runtime.
11. No training-only dependency is required by the server.
12. Regression tests compare CPU outcomes against MonsterMazeEngine and live 1.8 traces.

## Important non-goals

This architecture does not make the existing `StableLiveMovementController`, `BeamSearchPlanner`, or `FirstPadSpeedrunController` the production CPU brain.

Those components remain useful as development/teacher/search tools until their functionality has been replaced or distilled into the learned policy.

The production server should have one small, deterministic inference path rather than a growing collection of exceptional movement controllers.
