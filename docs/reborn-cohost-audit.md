> **Superseded integration note (2026-10-26):** This audit's original recommendation to install the standalone plugin beside Mineplex Reborn is no longer the current plan. The chosen implementation is now a **native Reborn Arcade port** in [`joshbet9/testplex`](https://github.com/joshbet9/testplex/tree/feat/testbed-bootstrap). The new `MonsterMazeSpeed` and `MonsterMazeModern` game types live in `Arcade.jar`, reuse Reborn's existing game lifecycle/kits/stats/map infrastructure, and keep original `MonsterMaze` available. Do **not** deploy `MonsterMazeStandalone.jar` to the Reborn server. Use the native branch's [in-game test plan](https://github.com/joshbet9/testplex/blob/feat/testbed-bootstrap/docs/IN-GAME-TEST-PLAN.md) as the authoritative test procedure; use this document below only as historical comparison context.

# Mineplex Reborn vs. Monster Maze Modern — co-host integration audit

**Scope:** the legacy Mineplex Reborn Arcade implementation (recovered source snapshot in `docs/references/monstermaze-source.md`) compared with the maintained standalone implementation in `1.8/MonsterMazeStandalone/`. The target host is the Java 8 / Minecraft 1.8.8 Reborn test server.

This is a behaviour-first comparison, not a source-file copy plan. The Reborn implementation remains the original Arcade game. Monster Maze Modern is a separate plugin and separate world so the two versions can be tested side by side without replacing the original `GameType.MonsterMaze`.

## Integration decision

| Area | Decision |
|---|---|
| Existing Reborn Monster Maze | Leave `Nautilus.Game.Arcade`'s `MonsterMaze`, `Maze`, kits, stat trackers, and map registration intact. |
| New game | Load the standalone 1.8.8 code as a second plugin with plugin identity `MonsterMazeModern`. |
| Access | Use `/mmmodernmenu` to enter the standalone selector, then choose **Monster Maze Modern**. Admin/game controls use `/mmmodern`; pause uses `/mmmpause`. |
| Host lobby and respawns | In co-hosted mode, the standalone plugin does not route every server join/respawn to its own selector. It only handles respawns that belong to its own Monster Maze world. |
| Reborn cosmetic modules | The standalone cosmetic prototype listeners are disabled in co-hosted mode. Cosmetic testing stays on the real Mineplex Reborn Arcade modules so effects are not double-applied. |
| Version choice | Only `1.8/MonsterMazeStandalone/` belongs in the Reborn 1.8.8 server. The Paper 1.21 implementation is a separate codebase and must not be deployed to that server. |

## Confirmed comparison

### Original layout data

The three 99 × 99 maze-layout arrays in the recovered Reborn source (`Maze1.java`, `Maze2.java`, `Maze3.java`) were compared element-by-element with the digit-string layouts in standalone `MazeLayouts.java`.

| Pattern | Original cells | Standalone cells | Result |
|---|---:|---:|---|
| Maze 1 | 9,801 | 9,801 | Exact cell-for-cell match |
| Maze 2 | 9,801 | 9,801 | Exact cell-for-cell match |
| Maze 3 | 9,801 | 9,801 | Exact cell-for-cell match |

The standalone stores these rows as digit strings to avoid the JVM method-size limit of a huge static integer-array initializer. This representation change does not alter the layouts.

### Gameplay and systems

| System | Reborn source baseline | Standalone Monster Maze Modern | Port/integration status |
|---|---|---|---|
| Game lifecycle | Arcade `SoloGame` / `GameType.MonsterMaze`, integrated with Reborn match lifecycle | Own `GameManager`, lifecycle states, scheduler tasks, world/lobby, and selector | Kept separate by design; attempting to register the standalone manager as a native `GameType` would require a larger architectural rewrite. |
| Maze patterns | Three 99 × 99 patterns loaded through `MMM azes`/pattern classes | Three layouts in `MazeLayouts`; can force pattern 1–3 or random | Layout contents exactly match; generation and world handling remain separate. |
| Safe Pad geometry | Original `SafePad.isOn` uses a bounding box with unequal positive/negative X/Z extents (`+2.999/-2` on X and `+2/-2.999` on Z) | Standalone uses symmetric offsets of ±2.5 blocks on X/Z | Existing standalone fix removes the off-centre acceptance asymmetry. Keep this fix. |
| Safe Pad restoration | Original snapshots each temporarily replaced block and restores it when the pad is destroyed | Standalone captures block snapshots and restores in reverse order | Retained. Do not rebuild a pad's surrounding blocks as part of the optional beacon-repair feature. |
| Safe Pad objective beacon | Original builds/turns off the central beacon as stages advance | New optional standalone repair restores only a missing central beacon; disabled by default | Added for the co-host build. It must not reset clay, stairs, base blocks, or old-pad decay. |
| Safe Pad surface | Original default stained clay uses dye data 5 (green) and follows the established decay colours | New optional surface tint supports 1.8 dye data 0–15; default retains green | Added behind a separate disabled-by-default setting. |
| Base kits | Original has Jumper, Slowballer, Body Builder, Repulsor | Standalone retains these concepts and adds mode-aware descriptions and behavior | Retained in the independent implementation; don't replace Reborn's kit classes. |
| Jumper | Original has five charged jumps and legacy jump handling | Original mode retains five; enhanced modes use three, with Safe Pad restoration and free jumps on pad | New mode-specific behaviour remains in the standalone game. |
| Slowballer | Snowballs slow other players for 2 seconds; up to 16 snowballs regenerate | Retains snowballs and adds Cryo Blitz in enhanced modes (freeze nearby monsters for 3 seconds, 30-second cooldown) | Extended mechanic, deliberately not injected into the original game. |
| Body Builder | First player to a pad gains a max-health heart, capped at 15 hearts | Retains pad reward and adds Body Rush in enhanced modes (10-second activation; monster hits deflected; hit shortens duration) | Extended mechanic, deliberately separate. |
| Repulsor | Three charges launch nearby monsters away | Standalone describes 6-block range and removes repulsed monsters after launch | Needs in-game parity verification for exact edge cases; do not assume the source's behavior is identical without running tests. |
| Maverick | Not part of the original four-kit baseline | Fifth kit in enhanced modes; redirects monster knockback toward the next Safe Pad | Standalone-only extension. |
| Monster movement | Original `Maze` and `MazeMobWaypoint` own waypoint movement, bump/collision and stage behavior | Standalone has its own `MonsterManager`, waypoint state, bump cooldowns, launch/freeze states, and staged spawning | Separate implementation; visual and collision parity needs runtime tests. |
| Mob types | Original resolves a map's configured entity type and uses Mineplex ghost-disguise/entity handling | Standalone map manager accepts a mob override, but its 1.8 manager currently warns and falls back to a ghost snowman when the requested type is not implemented | **Known gap:** configurable non-snowman mob types are not fully carried through in standalone 1.8. This must not be described as complete. |
| Monster totals | Original fills 150 starter monsters | Standalone Original/Speed begin at 150; Modern begins at 225; Modern adds 30 per stage transition | Intentional balance extension. Validate spawn timing and TPS on the test server. |
| Original stat trackers | Reborn registers Ninja (never hit), Hard Mode (never use an ability), Speed (first to a pad), Pilot (land on a pad directly after a monster bump), and ToughCompetition (survive past pad 10) | Standalone `StatTracker` tracks these equivalents locally and has a 2-second Pilot window to prevent a stale hit from counting later | Behaviour is represented, but these standalone messages/PB data are not automatically writes to Reborn's global Arcade statistics service. Native stat-service integration is a separate task if global stats are required. |
| Personal bests / leaderboards | Original relies on the Reborn stat system | Standalone stores PBs by mode, pattern, player, and kit; includes scoreboard and leaderboard UI | Standalone feature set remains intact. |
| Competitive systems | Not part of the original in-game mechanics classes | Backend run recording, MMR/ELO, weekly challenges, tournaments, seasonal data, and leaderboards | Standalone-specific services; runtime backend configuration and credentials must be checked independently. |
| Mode tuning | Original class uses the original game rules and timings | Standalone offers Original, Speed, Modern; Original retains 60s-to-15s pacing, Modern starts at 35s and reaches 15s by stage 10 | Separate selectable modes; don't change the original Arcade game. |
| Pause | No equivalent standalone `/pause` workflow in the source baseline | Standalone adds a pause/resume flow for safe testing and excludes paused time from run timing | Standalone-only extension; use `/mmmpause` on the co-host build and run the pause test plan. |
| Maze/map world management | Uses Reborn world data and map infrastructure | Own map manager, map palettes, named map worlds, lobby, async generation and cleanup | Must stay in the standalone plugin's configured worlds. Never run `/mmmodern setcenter` in the Reborn public hub. |
| Join/respawn flow | Owned by Reborn's host/lobby systems | Normal standalone install routes players to its own selector | Co-host guard added: preserve Reborn join flow; limit standalone respawn handling to its own world. |
| Cosmetic prototype handlers | Not part of original Monster Maze mechanics; Reborn already hosts the real Arcade modules | Standalone repository also has separate playable cosmetic prototypes | Disabled in co-hosted mode to avoid overlapping handlers; test the six cosmetics using Reborn's native Arcade implementation. |

## Outstanding parity checks

This table separates confirmed code facts from runtime claims. The following require actual 1.8.8 server/in-game checks before calling the combined build ready:

1. Start-up with both `Arcade.jar` and `MonsterMazeModern.jar`; verify there is no plugin command collision, class/linkage error, or change to the host's join/respawn behavior.
2. Enter **Monster Maze Modern** through `/mmmodernmenu`; start and stop multiple rounds, then enter Reborn's original **Monster Maze** from its normal Arcade selector.
3. Check waypoint pathing, monster collision/knockback, each kit's charged/secondary abilities, pad timing/decay, round-end cleanup, and Original/Speed/Modern spawn totals with two clients.
4. Verify Solo PB recording and backend calls using the test configuration; do not infer backend submission is working from a successful plugin startup.
5. If global Reborn stat integrations are required, map the standalone local awards to the Reborn stat API instead of assuming plugin-side messages update Reborn's central stats.
6. Implement or explicitly disable non-snowman mob overrides in standalone 1.8. The current warning/fallback is a known limitation.
7. Re-test with Safe Pad persistence both off (default) and on. The optional feature should restore only the central objective beacon, never surrounding clay/base blocks or a decaying old pad.

## Test invocation

- `/mmmodernmenu` — enter the standalone game selector.
- `/mmmodern start` — start the standalone round (permission rules still apply).
- `/mmmodern status` — inspect the standalone game state.
- `/mmmpause` — pause/resume the standalone round when permitted.
- Native Reborn **Monster Maze** remains accessed through the existing Arcade selector and uses its original classes.

Do not mark gameplay parity as verified merely because both plugin JARs build.
