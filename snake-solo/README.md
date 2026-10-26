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
- `/snake cosmetic status` — show whether the Ability Colour Line is unlocked/equipped.
- `/snake cosmetic equip` / `/snake cosmetic off` — equip or remove the line.
- `/snake cosmetic unlock <player>` — operator-only prototype unlock for an online player.

Hotbar power-ups: feather (speed), nether star (brief invulnerability), and cookie (reverse). The roulette appears in hotbar slot 9, as requested in the forum proposal.

## Ability Colour Line prototype

The line is per-player and must be both unlocked and equipped. With it active, the snake and every tail segment turn **yellow for Speed**, **cyan for Super Snake/invulnerability**, and **purple for Reverse**, then return to the base lime colour. If the line is not unlocked or is not equipped, the existing four-colour Super Snake rainbow remains. The colour is set on the actual sheep entities rather than sent as viewer-specific block/entity packets, so every player watching that snake sees the same ability colour. If powers overlap, Super Snake takes visual priority, followed by Speed and then Reverse.

For a quick test, an operator can run `/snake cosmetic unlock <player>`, then the player uses `/snake cosmetic equip`. Use `/snake cosmetic off` to verify the default rainbow fallback.

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
