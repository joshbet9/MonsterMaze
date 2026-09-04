# Monster Maze — Cross-Version Reference

This document tracks the intended player-facing behaviour of the Minecraft
1.8.9 and 1.21.11 Monster Maze implementations. The two versions should
match wherever the original gameplay is shared, while allowing version-specific
technical implementations where Minecraft requires them.

## Status Legend

- `Implemented` — present in the current source.
- `Build Verified` — the implementation has built successfully.
- `Gameplay Verified` — tested in-game.
- `Original Verified` — supported by original Mineplex source or captured evidence.
- `Version Specific` — intentionally different because of the Minecraft version.
- `Open` — still requires investigation or gameplay verification.

---

## Modes

| Platform | Mode | Timer | Starter monsters | Key differences |
|---|---|---:|---:|---|
| 1.8.9 | Original | 60s → 15s | 150 | Original gameplay and 5 Jumper charges |
| 1.8.9 | Speed | 60s → 15s | 150 | Enhanced kits and Safe Pad Jumper restoration while retaining original pacing/spawning |
| 1.8.9 | Modern | 35s → 15s | 225 | Enhanced kits, faster pacing and heavier monster spawning |
| 1.8.9 | Lagless | 35s → 15s | 500 fixed | No per-stage monster batches; monster speed increases every 5 stages |
| 1.21.11 | Original | 60s → 15s | 150 | Original gameplay and 5 Jumper charges |
| 1.21.11 | Modern | 35s → 15s | 225 | Enhanced kits and Modern movement-speed handling |
| 1.21.11 | Classic | 35s → 15s | 225 | Modern tuning without the Modern movement-speed boost |

---

## Kits

All five kits exist on both platforms. Maverick and the secondary abilities
are unavailable in Original mode.

| Kit | Original | Enhanced modes |
|---|---|---|
| Jumper | 5 charged jumps | 3 charged jumps; Safe Pads restore charges and jumps on Safe Pads are free |
| Slowballer | Snowballs slow other players; 16 maximum, regenerating over time | Adds Cryo Blitz: freezes nearby monsters for 3s, 30s cooldown |
| Body Builder | First to a Safe Pad gains 1 heart of maximum health, up to 15 hearts | Adds Body Rush: 2 activations, 10s each; monster contacts are deflected and cost 2s of duration |
| Repulsor | 3 charges; launches nearby monsters away | Same |
| Maverick | Unavailable | Monster hits launch the player toward the next Safe Pad |

---

## Movement and Collision

### 1.8.9

- The recreation uses the legacy Minecraft movement/effect system.
- The original "speeding" behaviour remains active in all 1.8 modes.
- Jumper jump locking and charged-jump handling use the legacy mechanics available
  on the 1.8 client/server stack.

### 1.21.11

- Jumper jump locking uses modern player attributes rather than the legacy
  negative jump-effect approach.
- Modern applies an additional movement-speed adjustment while a player's
  jumping is locked. Classic intentionally omits this adjustment.
- Collision and knockback are implemented with modern server APIs while aiming
  for the same player-facing Monster Maze behaviour.

These are implementation differences, not separate gameplay systems.

---

## Safe Pads

Across both versions:

- Safe Pads are the progression checkpoints.
- The first player to reach a Safe Pad receives the first-player reward.
- Other players receive the normal Safe Pad healing.
- Body Builder gains 1 heart of maximum health when first to a Safe Pad, capped
  at 15 hearts.
- In enhanced modes, Safe Pads restore Jumper charges and jumping while on the
  Safe Pad is free.
- Maverick directs monster-hit knockback toward the next Safe Pad.

---

## Monster Progression

- Original pacing starts at 60 seconds and decreases by 2 seconds per stage,
  stopping at 15 seconds.
- Modern pacing starts at 35 seconds and reaches the 15-second floor over the
  first 10 stages.
- 1.8 Speed keeps Original pacing and spawning while using enhanced gameplay.
- 1.8 Lagless replaces recurring monster batches with a fixed 500-monster pool
  and increases monster speed every 5 stages.
- 1.8 Modern adds 30 monsters per Safe Pad transition.

---

## Scoring and Records

- Personal bests are tracked separately by platform, mode, maze pattern and kit.
- Completed solo attempts are recorded with their platform, mode, pattern, kit,
  stage and timestamp.
- The permanent leaderboard uses the best result; competition history can retain
  every submitted attempt.

---

## Verification

The detailed gameplay source of truth is `docs/mechanics.md`. This document is
for comparing the two implementations at a high level; unresolved parity issues
should be recorded there and investigated against the original Mineplex evidence.
