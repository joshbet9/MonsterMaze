# Playable Arcade Cosmetic Testbed (Spigot 1.8.8)

This is a **playable test environment** for the Runner, Bacon Brawl, Dragon Escape, Micro Battles, Evolution and Snake cosmetic ideas. It is a separate 1.8.8 plugin and creates its own `arcade_cosmetic_test` world, so test rounds do not change the Monster Maze map or its round state.

## Why this lives beside Monster Maze

The recovered Mineplex Arcade game classes depend on the original Arcade framework, custom combat/stat/event APIs, map loader and patched server libraries. Moving the whole framework into Monster Maze just to test cosmetics would be a much larger migration and would add the original server's service dependencies.

Instead, this testbed ports the essential playable loop needed to test each cosmetic and Snake's ability visuals, using generated test arenas. It is **not** a complete or map-faithful replacement for the full Mineplex Reborn Arcade games; those exact game classes still belong in the Mineplex Reborn runtime. This provides a convenient local gameplay test without requiring Redis, MySQL or the original API service.

## Build and install

Requires a Spigot 1.8.8 server. From the repository root:

```powershell
mvn -f cosmetic-prototypes/server/pom.xml clean package
```

Copy `cosmetic-prototypes/server/target/MinigameCosmeticPrototypes.jar` into the test server's `plugins` directory and restart. This plugin deliberately targets legacy Spigot 1.8.8; it is not intended to load on Paper 1.21.

The `arcade_cosmetic_test` world is created automatically when a player joins or starts a mode.

## Start a playable round

- `/games` — on the 1.8.8 Monster Maze server selector, choose Runner, Bacon Brawl, Dragon Escape, Micro Battles, Evolution or Snake, then select **Start playable test round**.
- `/arcadetest join` — enter the isolated test world.
- `/arcadetest start <runner|baconbrawl|dragonescape|microbattles|evolution|snake> [runner-map-palette]` — start or replace the current test round. Players already in the test world are included. The optional comma-separated palette is for Runner testing, e.g. `lightblue,red`.
- `/arcadetest stop` — stop a round and return players to the test-world hub.
- `/arcadetest leave` — return to the location and inventory held before entering the test world.
- `/arcadetest unlock` — unlock all cosmetic choices for yourself while in the test world, so a Solo test server does not require OP privileges.
- `/arcadetest status` and `/arcadetest list` — show current round details and valid mode keys.

Cosmetics are persistent per-player test selections. Use:

- `/cosmeticproto list` — list choices.
- `/cosmeticproto equip <game> <choice>` — equip an unlocked choice.
- `/cosmeticproto off <game>` — restore the no-cosmetic behaviour.
- `/cosmeticproto status` — show current unlocks/equipment.
- `/cosmeticproto preview <game> [map-palette]` — keep using a short visual preview when a full round is unnecessary.

## What the playable rounds exercise

### Runner

Players run along separate lanes. Stained-clay colour is selected per player, and the blocks behind them disappear. The eight cosmetic pairs are `white`, `orange`, `magenta`, `lightblue`, `yellow`, `lime`, `pink` and `gray`. If the selected base colour is already in the map palette, the inverse is used. If both pair colours are in the palette, a random third colour distinct from both is used. Without a cosmetic, the generic red clay is retained.

The generated test arena defaults to a simulated red/blue map palette. For a deterministic way to exercise the “both colours in the pair are present” fallback, equip `lightblue` and start `/arcadetest start runner lightblue,red`. Both lightblue (data 3) and red (data 14, its inverse) are declared in that test palette, so the routine randomly chooses a third colour distinct from both. Likewise, equip `orange` and use `/arcadetest start runner orange,blue` to test that pair. The random choice can be seen in the running lane; the short `/cosmeticproto preview runner <palette>` remains available for a quick visual check.

### Bacon Brawl

Players fight in an arena with swords. The test records confirmed player kills and attaches the equipped accessory to the pig representing the current kill leader. Choices: `crown`, `top_hat`, `chef` and `diamond`.

### Dragon Escape

Players race along independent jump-gap lanes to the finish. Each player's lane has its own beacon checkpoint, and the equipped stained-glass colour is applied above that beacon. All 16 stained-glass colours are available.

### Micro Battles

Players fight from corner starts around a central glass block. Each connected viewer receives their own centre-glass colour and matching border-particle packets; the world block is not globally recoloured. Themes: `aqua`, `ember`, `forest`, `royal`, `sunset`, `solar`, `frost` and `void`.

### Evolution

Players fight in an arena and evolve their weapon as they earn kills. The equipped `dragon-egg` cosmetic triggers a portal-style particle burst when evolution occurs.

### Snake

Players steer the mounted sheep around an arena with visible trailing sheep segments, grow their tail by collecting slimes, and are eliminated by hitting tails or leaving the arena. Redstone activates Super Snake, Feather activates Speed, and Cookie temporarily reverses travel direction; each ability lasts two seconds. The ability windows also suppress tail-collision elimination, which makes visual testing easier than the original fully competitive round. The selected Snake line controls the visible head/tail colour for each ability, and sheep entities change colour server-side so all other players see the same cosmetic.

Snake lines:
- `elemental`: Super Snake blue, Speed yellow, Reverse purple.
- `neon`: Super Snake cyan, Speed lime, Reverse magenta.
- `sunset`: Super Snake red, Speed orange, Reverse pink.

When no Snake line is equipped, Super Snake keeps the classic random rainbow effect. Unequipping `snake` therefore preserves the legacy behaviour.

## Example first-time setup

Run these in order on the 1.8.8 test server:

```text
/arcadetest join
/arcadetest unlock
/cosmeticproto equip snake elemental
/arcadetest start snake
```

To test the Runner pair-colour fallback, stop the round, equip the desired Runner colour, and then use a palette override such as `/arcadetest start runner lightblue,red`. All six test modes share one disposable local test world; only one mode runs at a time.

## Scope note

These rounds are designed to let you actually move, jump, fight, evolve, collect, collide and observe the cosmetics in multiplayer. They use generated arenas and simplified gameplay plumbing, rather than copying the Mineplex Reborn `SoloGame` implementations wholesale. In particular, they do not reproduce the original maps, kits, leaderboard/stat backends, full dragon AI, or every anti-cheat and round rule. The testbed is isolated and disposable; it is a development aid for evaluating cosmetic behaviour before integrating those hooks into the real Mineplex Arcade game classes.
