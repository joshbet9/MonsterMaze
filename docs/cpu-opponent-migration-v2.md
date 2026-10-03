# Monster Maze CPU Opponent Migration Plan v2

## End state

The 1.8 server can start a match with zero or more CPU competitors configured independently from human players.

Each CPU has:
- display identity;
- kit;
- difficulty;
- capability attributes;
- behavioural tendencies;
- policy/model version;
- deterministic behaviour seed.

The CPU participates in the actual game rules.

## Migration stages

### Stage 0 — architecture

Completed on the CPU architecture branch.

Define:
- brain/body split;
- direct policy contract;
- authoritative observation;
- human/CPU accounting;
- training/production separation.

### Stage 1 — participant boundary

Introduce a participant abstraction at the game-rule boundary.

The boundary covers only what the gameplay engine needs:
- id/type;
- position/velocity/yaw;
- alive/health;
- kit state;
- movement state;
- Safe Pad state;
- damage/knockback;
- progression.

Human UI/backend methods remain outside this abstraction.

Avoid rewriting commands, scoreboards and backend systems at the same time.

### Stage 2 — CPU avatar spike

Implement the NMS-backed player-like CPU avatar.

Acceptance:
- appears as a named competitor;
- occupies normal collision/player space;
- can receive movement intent;
- vanilla 1.8 movement/physics resolve the motion;
- monster collision and kit handling can see it;
- human clients do not need a real network connection for it.

The avatar is replaceable behind the CpuAvatar interface.

### Stage 3 — server observation

Build the compact player-equivalent observation directly from authoritative GameManager/MonsterManager/KitManager state.

Do not use the old client observer.

Do not pass hidden server state to the policy.

### Stage 4 — deterministic policy bridge

Load the direct Java-8-compatible policy artifact and run one CPU with a baseline profile.

Before learning, a deterministic reference policy may be used only to validate the full execution bridge.

### Stage 5 — one-pad acceptance

Run one CPU in the actual server.

Acceptance:
- reaches the first target repeatedly;
- no FirstPadSpeedrunController;
- no sidecar;
- no hard-coded route recovery hacks.

### Stage 6 — full game

Enable:
- all three patterns;
- all modes;
- all kits;
- monster interaction;
- abilities;
- full progression.

### Stage 7 — competition

Enable multiple CPUs and human competitors.

Acceptance:
- pad races work;
- CPU/player collisions are authoritative;
- CPU results do not enter human PB/leaderboard systems;
- server tick budget remains healthy.

### Stage 8 — configuration surface

Add admin/config controls for:
- CPU count;
- profile presets;
- individual attributes;
- tendencies;
- model selection;
- behaviour seed.

### Stage 9 — continuous training

Connect MonsterMazeAI + MonsterMazeEngine training pipeline.

Promoted artifacts can be loaded by the server after explicit deployment/restart/reload.

## Existing code to retire from production

These client-AI components must not become the server CPU implementation:
- FirstPadSpeedrunController;
- StableLiveMovementController;
- BeamSearchPlanner;
- RecedingHorizonController;
- live sidecar protocol.

They may remain in development repositories as teacher/research components while the direct policy is trained.

## Safety rule

Never change game mechanics to make the CPU easier or harder.

All skill differences come from:
- what the policy observes;
- when it makes decisions;
- how accurately it executes;
- which route/risk choices it makes;
- how it uses abilities;
- configured behavioural preferences.

## Definition of done

The migration is complete when a configured CPU can play a real Monster Maze 1.8 match from start to elimination using:

MonsterMaze authoritative game + CpuAvatar + direct CpuPolicy + configured profile.

MonsterMazeAI and MonsterMazeEngine are used only for building and validating the policy.
