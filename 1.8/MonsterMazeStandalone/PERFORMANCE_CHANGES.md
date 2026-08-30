# Performance Optimization Change Log

Target: fix server TPS / high-ping (500-1000ms) lag on hosted servers during long games and
across multiple games, WITHOUT changing gameplay.

Rule enforced for every item below: **functionality stays identical**. If any change makes the
game behave unreasonably, revert that single item (steps included) and rebuild.

The deployed/edited source lives in `C:\Users\Josh\MonsterMazeStandalone.claude`
(not `C:\Users\Josh\MonsterMazeStandalone`). Build + deploy:

```
$env:JAVA_HOME="C:\Users\Josh\AppData\Local\Programs\Eclipse Adoptium\jdk-8.0.502.7-hotspot"
mvn -o clean package        # in the .claude folder
copy target\MonsterMazeStandalone.jar C:\monstermaze_test\plugins\MonsterMazeStandalone.jar
```

Restart the test server to apply.

---

## 1. Event-driven scoreboard, compass & exp bar (GameManager)

**File:** `src/main/java/me/monstermaze/game/GameManager.java`
**What:**
- `mainTask` now computes `getAlivePlayers()` ONCE per tick and reuses it (was ~4 copies/tick).
- Compass: `updateCompasses()` caches the last pad block target in `lastCompassTarget` and only
  sends `setCompassTarget` when the pad actually moves (once per stage, not 20x/s).
- Exp bar: `updateExpBars(aliveNow)` caches `lastExpPct`/`lastLevel`; only sends
  `setExp`/`setLevel` when the displayed value changes (1Hz, not 20Hz).
- Scoreboard: the per-player `update()` + `apply()` block (which includes a per-player PB lookup)
  now runs every 10 ticks (2x/s) instead of every tick. Kept at 2Hz so stage/phase transitions
  still reflect within 0.5s. First mainTask tick still renders immediately.

**Why safe:** pad/phases/score only change at ~1Hz; PB never changes mid-game.
**Revert:** restore old `mainTask` body + `updateCompasses()` + `updateExpBars()` from the
pre-change file; remove `liveTick`, `lastExpPct`, `lastLevel`, `lastCompassTarget` fields.

## 2. Optimized monster bump (MonsterManager)

**File:** `src/main/java/me/monstermaze/entity/MonsterManager.java`
**What:** `bump()` snapshots monster positions into flat arrays once per tick, then for each
player runs a cheap 2D squared-distance prefilter (`dx^2+dz^2 >= 1.0` skip) before the exact
3D `< 1.0` check. Removed the O(players x monsters) per-pair `Location.distance()` sqrt calls.
Removed unused `bumpInRange()`/`offset()` helpers.

**Why safe:** mathematically equivalent to `sqrt(dx^2+dy^2+dz^2) < 1.0`; same entities, same
order, same range, so the same monster still bumps the same player. No change to knockback/damage.
**Revert:** restore the old `bump()` loop and helpers (range `offset(player,ent) < 1.0`).

## 3. Monster navigation at 10Hz (MonsterManager)

**File:** `src/main/java/me/monstermaze/entity/MonsterManager.java`
**What:** `move()` now runs the waypoint-decision logic every 2nd tick (`moveTick` counter),
i.e. 10Hz instead of 20Hz. `bump()` and `tickLaunched()` still run every tick.

**Why safe:** the vanilla controller target persists between calls, so monsters keep walking at
1.4 speed continuously (movement itself is still 20Hz). A decision point is only "missed" by at
most 1 tick = 0.07 blocks, far inside the 0.4 waypoint tolerance. Purely reduces NMS/pathing work.
**Revert:** remove the `moveTick` gate at the top of `move()` (and the field).

## 4. Per-tick kit packet spam (KitManager)

**File:** `src/main/java/me/monstermaze/kit/KitManager.java`
**What:**
- `tickJumpLock()`: only sends `removePotionEffect(JUMP)` for an allowed Jumper when the effect
  is actually present (`hasPotionEffect`) — a Jumper never holds the block effect, so this killed
  one useless packet per Jumper per tick (was 20/s per Jumper).
- `tickJumperFlight()`: caches the last action-bar text per player in `lastJumpBar` and only
  sends the packet when the "N Jumps Remaining" count changes. Cleared in `applyKit()` and
  `resetPlayerState()` so every new game re-sends once.

**Why safe:** the bar text only changes when a jump is consumed; the block-effect removal only
mattered when the effect existed. Identical visuals.
**Revert:** remove the cache + guard; restore unconditional `removePotionEffect` / `actionBar`.

## 5. Staggered start-of-match monster spawn (MonsterManager)

**File:** `src/main/java/me/monstermaze/entity/MonsterManager.java`
**What:** `start()` no longer spawns all 150/225 monsters in one tick. A `spawnTask` spawns
batches of 25 monsters per tick (~9 ticks total for Modern), filling the maze gradually during
the countdown. `stop()` cancels `spawnTask` too. The shuffle/validity logic was extracted into
`spawnBatch()`/`spawnOne()` (same rules/guard as before, `fillSpawn` kept as a wrapper).

**Why safe:** the batches finish ~9 ticks in, well before LIVE (~70 ticks), so all monsters are
behind the glass before the doors drop. Same spawn positions/density, just spread over ~9 ticks
to avoid one giant client packet burst.
**Revert:** restore `fillSpawn(starter)` call inside `start()`; drop `spawnTask`.

## 6. Villager pin timer (KitSelectorNPCs)

**File:** `src/main/java/me/monstermaze/kit/KitSelectorNPCs.java`
**What:** the permanent select-villager pin task runs every 10 ticks (2Hz) instead of every tick
(20Hz), and now early-returns unless the game is `IDLE`/`STARTING` (the only states NPCs exist).
A pushed villager drifts at most ~0.25 blocks for half a second between pins.

**Why safe:** pinning at 2Hz keeps selectors anchored; the state gate makes the task a no-op
during LIVE.
**Revert:** change `}, 10L, 10L);` back to `}, 1L, 1L);` and drop the state gate.

---

## Sanity checklist after deploy
- Start and play a full Modern (Speed) game: monsters walk normally and still chase corners.
- Confirm the Jumper action bar updates when feathers are consumed, the compass still points at
  the pad/beacon, the exp bar and scoreboard still show pad/phase info.
- Bump behavior (knockback, 4 dmg, 1s cooldown) unchanged.
- Play several back-to-back games; watch `/tps` and ping.
```