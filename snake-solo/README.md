# Snake Solo — playable 1.8.8 prototype

This is a separate Spigot 1.8.8 plugin for testing Snake by yourself. It builds and owns an isolated `snake_solo` world and does not modify Monster Maze worlds or maps. To play it in your existing Monster Maze Solo setup, copy `SnakeSoloStandalone.jar` into `solo/server/plugins/` (beside `MonsterMazeStandalone.jar`), then start the usual Solo launcher. The prototype is deliberately not added to the production Solo packer by default.

## Start and play

Install `SnakeSoloStandalone.jar` into the 1.8.8 server's `plugins` folder and restart the server. In-game, run:

- `/snake start` — start a solo run. Turn by changing your view; third-person (F5) is recommended.
- `/snake stop` — stop and restore your original location, game mode, inventory and player state.
- `/snake stats` — show the local top-ten run table.
- `/snake hazard now` — spawn a magma-cube hazard immediately for rapid testing.
- `/snake hazard off` — remove cubes and disable further hazards this run.
- `/snake hazard status` — show whether the late-game hazard is waiting, enabled or disabled.
- `/snake cosmetic list` / `/snake cosmetic status` — browse available and unlocked snake-type sets.
- `/snake cosmetic equip <type>` — equip a type set; `/snake cosmetic off` removes it.
- `/snake cosmetic unlock <player> [type|all]` — operator-only prototype unlock for an online player.

Hotbar power-ups: feather (speed), nether star (brief invulnerability), and cookie (reverse). The roulette appears in hotbar slot 9, as requested in the forum proposal.

## Snake Type Sets prototype

The cosmetic is a per-player choice of snake-type set. The prototype includes **Cobra**, **Python**, **Viper**, **Coral Snake**, **Sea Snake**, and **Rattlesnake**. Every set defines four different repeating colour patterns: its base look, Speed, Super Snake/invulnerability, and Reverse. For example, Cobra uses a green/lime body at rest, a yellow/lime speed pattern, a green/lime/black Super Snake pattern, and black/green bands when reversing; the other types use their own palettes and patterns for each state. The effect is selected by the active ability, not one universal colour per ability. Colours are applied to the actual sheep entities so everyone watching the player sees the same result.

When no set is equipped—or the player hasn't unlocked the chosen set—the original default lime snake and four-colour Super Snake rainbow remain. Operators can unlock all prototype sets with `/snake cosmetic unlock <player>`, or a specific one with `/snake cosmetic unlock <player> cobra`. Players browse with `/snake cosmetic list` and equip with `/snake cosmetic equip cobra`; use `/snake cosmetic off` to return to the default behaviour.

## Gameplay in this prototype

- The sheep tail grows by one segment per second.
- Normal green slimes remain food; eating one adds two segments.
- The snake speeds up as its tail grows.
- Self-collision (after the first three segments), leaving the arena, or falling below the floor ends the run.
- Magma cubes are a separate late-game hazard. By default they start after two minutes, then spawn more frequently over time.
- Hitting a cube starts a short roulette in slot 9 which locks in one of: Turn Lag, Drift, Overshoot or Weakened Sharp Turning. Each hit makes the next locked-in debuff last longer, up to the configured cap.
- `/snake hazard now` skips the wait and places the first cube about six blocks ahead of you, so you can test the collision/roulette without searching the arena or surviving two minutes first. It does not change the normal green slime behaviour.

The multiplayer `three players remain` trigger is intentionally skipped in this *solo* test because a one-player run would satisfy it from the first tick and ruin the normal early game. Timing and debuff durations are configurable in `plugins/SnakeSoloStandalone/config.yml`.

## Source reference

Gameplay reference: Mineplex Reborn's Snake implementation:
https://github.com/timing1337/mineplex-reborn/blob/stable/Plugins/Nautilus.Game.Arcade/src/nautilus/game/arcade/game/games/snake/Snake.java

This prototype reimplements the relevant mechanics independently; the source game class depends on Mineplex's Arcade/Core APIs.
