# Minigame Cosmetic Prototypes (server-side)

An isolated Spigot 1.8.8 plugin for prototyping cosmetics for Runner, Bacon Brawl, Dragon Escape, Evolution and Micro Battles. It is deliberately kept separate from the production Monster Maze and Snake Solo modules. The corresponding game loops are not included in this repository, so the prototype exposes adapter hooks rather than pretending those games are already integrated.

The 1.8 and 1.21 Monster Maze standalone servers also provide a `/games` pre-lobby selector in a separate protected `mm_sandbox` world. Choosing Monster Maze transfers the player to its original lobby. Other choices remain in the sandbox and offer preview-only menus; on Spigot 1.8.8 those menus can invoke this plugin's visual previews. The full Runner, Bacon Brawl, Dragon Escape, Evolution and Micro Battles game loops are not available here, and the selector intentionally does not fake a playable round.

## Build and install for testing

From the repository root:

```powershell
mvn -f cosmetic-prototypes/server/pom.xml package
```

For local preview testing, copy `cosmetic-prototypes/server/target/MinigameCosmeticPrototypes.jar` into a Spigot 1.8.8 test server's `plugins` directory and restart it.

For normal releases, the standard `vX.Y.Z` GitHub Actions release workflow now builds this JAR, stages it into the 1.8 Solo distribution and hosted 1.8 server image, publishes `MonsterMaze-Solo-1.8-Cosmetics.jar`, and includes its checksum/URL in the Solo updater manifest. The updater can therefore replace the plugin without manually copying it. It is intentionally **not** bundled into Paper 1.21 because this prototype targets the legacy Spigot 1.8.8 API/NMS.

## Unlock and equip

- `/cosmeticproto list` — show the prototype choices.
- `/cosmeticproto unlock <online-player> <game> <choice|all>` — operator-only test entitlement.
- `/cosmeticproto equip <game> <choice>` — equip an unlocked choice for yourself.
- `/cosmeticproto off <game>` — unequip for that game.
- `/cosmeticproto status` — show current test entitlements/equipment.
- `/cosmeticproto preview <game> [map-palette]` — preview a visual effect.

Game keys are `runner`, `baconbrawl`, `dragonescape`, `evolution` and `microbattles`.

### Runner

Eight choices (`white`, `orange`, `magenta`, `lightblue`, `yellow`, `lime`, `pink` and `gray`) each define one of eight complementary pairs in Minecraft's 16-colour stained-clay palette. If the chosen base colour is already present in the map palette, its paired inverse is selected. If **both** colours in that pair are present, the fallback randomly chooses a third colour, preferring one absent from the palette. If the map uses every clay colour, it still selects a third colour distinct from the pair. Without an equipped choice, the generic red clay remains.

Preview with `/cosmeticproto preview runner white,black,orange,blue`. Supply a comma-separated map palette using any of the 16 clay-colour names to test the inversion and fallback logic.

### Bacon Brawl

Choices are `crown`, `top_hat`, `chef` and `diamond`. The game adapter registers each player's pig and confirms kills. The pig of the player with the most recorded kills gets that player's equipped accessory. `recordBaconBrawlKill(UUID)` should be called only after the game's kill credit has been confirmed.

### Dragon Escape

All 16 stained-glass colours are available as beacon filter choices. The adapter can call `applyDragonEscapeBeaconColour(Block, UUID)` for the appropriate beacon, then `restoreDragonEscapeBeaconColour(Block)` when its cosmetic should end. The test preview creates a temporary beacon/glass sample in clear space and restores the temporary blocks afterwards.

### Evolution

The `dragon-egg` choice triggers a portal-style particle burst through `onPlayerEvolved(UUID, Location)` when the game adapter confirms an evolution.

### Micro Battles

Eight paired themes are included: `aqua`, `ember`, `forest`, `royal`, `sunset`, `solar`, `frost` and `void`. Each theme defines both the middle stained-glass colour and the close-border particle type. The server sends `Player.sendBlockChange` and individual 1.8.8 particle packets to that player's connection, so one player's theme does not modify the actual world block or change another player's view. The particle packet is resolved reflectively from the running Spigot 1.8.8 server, allowing this plugin to compile against the published Spigot API.

Use `/cosmeticproto unlock <player> microbattles all`, `/cosmeticproto equip microbattles aqua` and `/cosmeticproto preview microbattles`. The preview aims at a glass block within six blocks and temporarily displays the glass and particle set only to the player using the command.

During actual gameplay, the Micro Battles game adapter must call `registerMicroBattlesArena(Player, Block, List<Location>)` for each player, supplying the middle-glass block and nearby border sample points for that player's current map. Call `clearMicroBattlesArena(Player)` at round end and `refreshMicroBattlesGlass(Player)` if the equip changes mid-round.

## Integration boundary

The visual prototypes, unlock/equipment storage and adapter hooks are implemented here. They are **not yet wired into the original game event flows**, because the Runner, Bacon Brawl, Dragon Escape, Evolution and Micro Battles server implementations are not present in this repository. The preview commands verify the available sample effects; the relevant game code must call the hooks for them to operate automatically in a live round. Unlocks are operator-granted test entitlements, not a live shop or economy implementation.