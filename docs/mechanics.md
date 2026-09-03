# Monster Maze � Mechanics Reference

This document records known Monster Maze gameplay behaviour independently of the implementation.

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
true: Modern and Classic). In Original mode no secondary item is granted and the abilities cannot
activate.

- **Body Builder � Body Rush** (inventory item slot 0, one per game):
  - Material: apple. Right-click activates a persistent buff.
  - While active, every mob CONTACT deflects the mob away (same knock-away launch as Repulsor: the
    mob flies off and is removed once it lands/times out) with NO player knockback and NO damage
    (full bump immunity).
  - Has `BODY_RUSH_MAX_USES = 5`; each deflected contact consumes one. Lore shows "Uses remaining:
    N". When the counter hits 0 the buff ends and the item disappears (normal bumps resume).
  - Implementing in `KitManager` (`onBodyRush`, `isBodyRushActive`, `consumeBodyRushUse`,
    `bodyRushItem`) and intercepting the contact in `MonsterManager.bump()` before the normal
    knock/damage.

- **Slowballer � Cryo Blitz** (Q-drop trigger, no item consumed):
  - MID-GAME pressing Q (drop key) fires Cryo Blitz instead of the global drop-cancel swallowing it.
  - Freezes every monster within `CRYO_RADIUS = 5` blocks for `CRYO_FREEZE_MS = 3000` ms
    (frozen mobs neither move nor bump), on a `CRYO_COOLDOWN_MS = 60000` ms cooldown.
  - Cooldown is surfaced as lore on the Slowball slot-0 snowballs ("Cryo Blitz ready in Ns").
  - Implementing in `KitManager` (`onCryoBlitz`, `slowballItem`, `cooldownLore`) and via the
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
