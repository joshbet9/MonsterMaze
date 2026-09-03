# Monster Maze — Mechanics Reference

This document records known Monster Maze gameplay behaviour independently from the implementation.

The purpose is to prevent important discoveries from becoming trapped inside an AI conversation.

Each mechanic should ideally record:

- What the original Mineplex game did
- Evidence supporting the behaviour
- How 1.8 implements it
- How 1.21 implements it
- Whether both versions have been tested

---

## Movement

### Speed

Status: Unknown

Notes:

### Jumping

Status: Unknown

Notes:

### Gap Crossing

Status: Unknown

Notes:

---

## Monster Behaviour

### Movement

Status: Unknown

Notes:

### Targeting

Status: Unknown

Notes:

### Collision

Status: Unknown

Notes:

### Knockback

Status: Unknown

Notes:

---

## Waves

### Wave Progression

Status: Unknown

Notes:

### Spawn Timing

Status: Unknown

Notes:

---

## Abilities

### Kit secondaries (QOL / non-Original modes only)

Status: Implemented (1.8 + 1.21), Gameplay Verified pending

Notes:

The kit secondary abilities only function in non-Original modes (i.e. when `game.qolEnabled()` is
true: Modern/Speed/Lagless on 1.8 and Modern/Classic on 1.21). In Original mode no secondary item
is granted and the abilities cannot activate.

- **Body Builder — Body Rush** (inventory item slot 0, two activations):
  - Material: apple. Two apples provide two activations; each activation consumes one apple.
  - Each activation lasts 10 seconds.
  - While active, every real mob CONTACT deflects the mob away (same knock-away launch as Repulsor:
    the mob flies off and is removed once it lands/times out) with NO player knockback and NO damage
    (full bump immunity).
  - Each real mob contact removes exactly 2 seconds from the remaining Body Rush time.
  - Body Rush bypasses the normal 1-second player bump cooldown while active.
  - The action bar shows the live `BODY RUSH X.Xs` timer and briefly shows `-2.0s` after a contact.
  - Implemented in `KitManager` (`onBodyRush`, `isBodyRushActive`, `reduceBodyRushTime`,
    `bodyRushItem`) and by intercepting the contact in `MonsterManager.bump()` before the normal
    knock/damage.

- **Slowballer — Cryo Blitz** (Q-drop trigger, no item consumed):
  - MID-GAME pressing Q (drop key) fires Cryo Blitz instead of the global drop-cancel swallowing it.
  - Freezes every monster within `CRYO_RADIUS = 6` blocks for `CRYO_FREEZE_MS = 3000` ms
    (frozen mobs neither move nor bump), on a `CRYO_COOLDOWN_MS = 30000` ms cooldown.
  - Cooldown is surfaced as lore on the Slowballer slot-0 snowballs ("Cryo Blitz ready in Ns").
  - Implemented in `KitManager` (`onCryoBlitz`, `slowballItem`, `cooldownLore`) and via the
    `MonsterManager.frozen` map (`freeze()`, `tickFrozen()`), skipped in `move()` and `bump()`.

---

## Scoring

### 

Status: Unknown

Notes:

---

## Other Mechanics

### 

Status: Unknown

Notes:
