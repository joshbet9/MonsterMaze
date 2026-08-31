# Monster Maze � Cross-Version Tracking

This document tracks gameplay mechanics that exist across the Minecraft 1.8 and 1.21 implementations.

The two implementations should aim for equivalent gameplay behaviour where the original Mineplex behaviour is shared, while allowing version-specific technical implementations.

## Status Legend

- `Unknown` � not investigated
- `Investigating` � currently being researched
- `Implemented` � implemented in source
- `Build Verified` � implementation builds successfully
- `Gameplay Verified` � tested in-game
- `Original Verified` � supported by original Mineplex source/evidence
- `Version Specific` � intentionally differs because of Minecraft/version behaviour

---

## Core Gameplay

| Mechanic | 1.8 | 1.21 | Original Evidence | Cross-Version Status | Notes |
|---|---|---|---|---|---|
| Game lifecycle | | | | | |
| Maze generation | | | | | |
| Safe pads | | | | | |
| Wave progression | | | | | |
| Monster spawning | | | | | |
| Monster movement | | | | | |
| Monster targeting | | | | | |
| Monster collision | | | | | |
| Monster knockback | | | | | |
| Player movement | | | | | |
| Jump behaviour | | | | | |
| Speed effects | | | | | |
| Abilities | | | | | |
| Player death | | | | | |
| Scoring | | | | | |
| Victory conditions | | | | | |
| Kit selection | Implemented | Implemented | | Implemented | Same KitType enum both versions; JUMPER default; QOL-only kits downgrade to JUMPER when QOL disabled |
| Kit secondaries (Body Rush / Cryo Blitz) | Build Verified | Build Verified | | Implemented | New in this session; QOL modes only. Body Builder Body Rush (right-click, 5 uses, deflect+immunity); Slowballer Cryo Blitz (Q-drop, freeze 5-block/3s, 60s CD) |

---

## Maps

| Mechanic | 1.8 | 1.21 | Original Evidence | Status | Notes |
|---|---|---|---|---|---|
| Maze layouts | | | | | |
| Maze dimensions | | | | | |
| Maze generation rules | | | | | |
| Spawn locations | | | | | |
| Safe pad locations | | | | | |

---

## Timing

| Mechanic | 1.8 | 1.21 | Original Evidence | Status | Notes |
|---|---|---|---|---|---|
| Lobby countdown | | | | | |
| Game start timing | | | | | |
| Wave timing | | | | | |
| Monster spawn timing | | | | | |
| Safe pad timing | | | | | |
| Game ending timing | | | | | |

---

## Movement / Physics

| Mechanic | 1.8 | 1.21 | Original Evidence | Status | Notes |
|---|---|---|---|---|---|
| Base player speed | | | | | |
| Speed II behaviour | Version Specific | Implemented | | Version Specific | 1.21 Modern only: lock-pinned players get Speed II boost (1.8 "speeding" was a vanilla -10 jump-amp side effect, dropped as unreproducible) |
| Jump height | | | | | |
| Air control | | | | | |
| Gap crossing | | | | | |
| Monster knockback | | | | | |
| Player knockback | Implemented | Implemented | | Implemented | 4 dmg + knockback, 1s CD; Body Builder Body Rush grants immunity + deflecting knock instead |
| Collision behaviour | Implemented | Implemented | | Implemented | Ghost snowmen with collision off; server-driven bump() range < 1.0; frozen mobs (Cryo Blitz) skip bump |

---

## Open Questions

Record unresolved questions here rather than allowing agents to silently make assumptions.

- 
