# Minigame Cosmetic Prototypes (server-side)

An isolated Spigot 1.8.8 plugin for prototyping server-authoritative cosmetics. It is intentionally kept separate from Monster Maze and Snake Solo because this repository does not contain the Runner, Bacon Brawl, Dragon Escape or Evolution game implementations.

## Build and install for testing

From the repository root:

\`\`\`powershell
mvn -f cosmetic-prototypes/server/pom.xml package
\`\`\`

Copy \`cosmetic-prototypes/server/target/MinigameCosmeticPrototypes.jar\` into a **test server's** \`plugins\` directory and restart it. Do not add it to the production Solo packer yet.

## Unlock and equip

- \`/cosmeticproto list\` — show the prototype choices.
- \`/cosmeticproto unlock <online-player> <game> <choice|all>\` — operator-only test entitlement.
- \`/cosmeticproto equip <game> <choice>\` — equip an unlocked choice for yourself.
- \`/cosmeticproto off <game>\` — unequip for that game.
- \`/cosmeticproto status\` — show current test entitlements/equipment.
- \`/cosmeticproto preview <runner|baconbrawl|dragonescape|evolution> [map-palette]\` — locally trigger a visible prototype sample.

Game keys accept \`runner\`, \`baconbrawl\`, \`dragonescape\`, and \`evolution\`. Runner's eight choices are \`white\`, \`orange\`, \`magenta\`, \`lightblue\`, \`yellow\`, \`lime\`, \`pink\`, and \`gray\`. Each is one of eight complementary pairs of Minecraft stained-clay colours. If the selected base clay colour is present in the current map palette, the server API returns its paired inverse instead. If both colours in that pair are present, it randomly chooses a third clay colour, preferring one not already in the map palette; if all 16 colours are used, it still chooses a colour distinct from the pair. Without an equipped choice, the source-compatible generic red clay is returned.

Dragon Escape choices use all 16 stained-glass colours. The sample creates a temporary beacon and glass filter ahead of the player, allowing the beam tint to be inspected. Bacon Brawl provides \`crown\`, \`top_hat\`, \`chef\`, and \`diamond\` pig accessories; the API selects the accessory for the registered pig owned by the player with the most recorded kills. Evolution provides the \`dragon-egg\` teleport/portal particle burst when the game adapter calls the evolution hook.

## Integration boundary

The visuals and entitlement storage are implemented here, with public hooks for a future game adapter:

- \`getRunnerClayData(UUID, Set<Byte>)\`
- \`registerBaconBrawlPig(UUID, Pig)\` and \`recordBaconBrawlKill(UUID)\`
- \`applyDragonEscapeBeaconColour(Block, UUID)\` and \`restoreDragonEscapeBeaconColour(Block)\`
- \`onPlayerEvolved(UUID, Location)\`

These hooks are not yet wired into the original minigame event flows because those game source projects are not present in this repository. The preview commands verify standalone visuals; gameplay integration must call the hooks from the corresponding server game code. In Micro Battles, the arena adapter must register each player's active middle-glass block and border points separately so packets can be sent according to that player's selected theme. Unlocks are operator-granted prototype entitlements, not a live shop or economy implementation.