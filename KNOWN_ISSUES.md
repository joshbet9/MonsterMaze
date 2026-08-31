# Known Issues

## High-latency players' ping "ballooning" during dense-mob flow (MonsterMaze 1.8)

**Status:** Open / diagnosed. Not fixed in production. Both attempted fixes were rolled back because they broke gameplay.

### Symptom
- On the hosted 1.8 server, only high-latency players balloon (e.g. JN_PlayzMC, baseline ~215-220 ms) spiking to ~450-640 ms mid/game.
- Low-ping players (6-90 ms) stay flat on the identical packet stream, at the same time.
- The balloon tracks mob-flow density: it gets worse as the Modern-mode mob count climbs (no cap, ~984 mobs late-game) and does not affect early-game.
- Reverting to a lighter byte rate (see the PacketThrottler test below) brought the affected player back to ~215-222 ms flat, confirming rate is the lever.

### Root cause (confirmed by testing)
- The entity-move packet volume overwhelms a high-RTT player's drain capacity (roughly the bandwidth-delay product / TCP window). For a player at ~220 ms RTT, the per-second outbound/propagated byte stream from hundreds of moving tracked mobs exceeds what their connection window can drain, so the client's view buffers up and reported ping climbs.
- **Not** server-side buffering: the server `buf=` stayed clean and `w=true` throughout the instrumented runs.
- **Not** shared-uplink saturation: low-ping players on the same server stayed flat.
- **Not** host-specific: cutting the byte rate (PacketThrottler test) fully flattened the high-ping player.
- Mobs between 32-48 blocks are tracked-but-frozen by spigot (`entity-activation-range monsters: 32`); they already emit ~0 move packets, so they are not the source.

### Attempted fixes (both rolled back)

1. **PacketThrottler — coarsen move-packet cadence (`PacketThrottler.java`, keep-every-3 ≈ 7 Hz).**
   - Result: fully fixed the high-ping player (215-222 flat vs 450-640), but visibly broke mob rendering on all clients (stutter/freeze). Reported by users as "lag." Unacceptable; rolled back completely (class deleted, wiring removed).

2. **Near-player mob budget (`reconcileNearby()`, Modern-only; config `near-mob-budget-modern` / `near-mob-radius`).**
   - Kept survivors at 20 Hz smoothness by trimming mob count instead of dropping packets. Original iteration **permanently despawned** mobs beyond any player's radius, which, with safe pads on different sides of the ~99x99 maze, drained the maze over a few rounds (users: "maze was basically empty").
   - A revised iteration tried "parking" surplus (freeze vs despawn) to keep the byte win without deletion, but parking any mob near/within contact range of a player is not acceptable by design rule: *a mob should never be parked/removed if a player can come into contact with it or is within a certain distance of it.* Since the byte flood comes precisely from the mobs that are near/visible to players (mobs beyond tracking already cost nothing), there is no way to reduce a player's move-byte rate without either slowing/dropping packets on a mob they see, or removing that mob — both violate the same constraint.
   - Reverted fully (code, config keys, and the accompanying NetMonitor diagnostic logging removed).

### Technical constraint learned
- A per-player move-packet flood can only be reduced by changing what a player actually sees near them. Trimming is fighting the high-RTT player's bandwidth-delay product.

### Long-term direction (not yet implemented)
- Likely a completely new, more controlled spawn/difficulty structure for Modern mode (and possibly 1.21): less random spawning, bounded total mob count, structured difficulty curve — instead of throttling or trimming an unbounded pool.
- Same class of issue would apply to the 1.21 plugin (different codebase, same packet model); it currently has no instrumentation.

### Diagnostic tooling removed
- `NetMonitor` (packet/instrumentation logging and its plugin wiring) was removed as part of the revert. Re-add it if re-diagnosing; it hooks per-player move bytes and logs `up=/bytes=/mobs=/buf=/ping` lines.
