package me.monstermaze.game;

import me.monstermaze.MonsterMazePlugin;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.WorldType;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * An isolated selector/sandbox world. Monster Maze still uses its original lobby;
 * other games are clearly marked cosmetic previews until their real game modules exist.
 */
public final class SandboxGameSelector implements Listener, CommandExecutor {
    public static final String HUB_WORLD_NAME = "mm_sandbox";
    private static final String SELECTOR_TITLE = ChatColor.DARK_PURPLE + "Game Selector";
    private static final String SANDBOX_PREFIX = ChatColor.DARK_AQUA + "Sandbox: ";
    private static final String COSMETIC_PLUGIN = "MinigameCosmeticPrototypes";

    private final MonsterMazePlugin plugin;
    private final GameManager game;
    private final Map<UUID, String> selectedSandboxGame = new HashMap<UUID, String>();

    public SandboxGameSelector(MonsterMazePlugin plugin, GameManager game) {
        this.plugin = plugin;
        this.game = game;
        Bukkit.getPluginManager().registerEvents(this, plugin);
        if (plugin.getCommand("games") != null) {
            plugin.getCommand("games").setExecutor(this);
        }
        ensureHubWorld();
    }

    public World ensureHubWorld() {
        World world = Bukkit.getWorld(HUB_WORLD_NAME);
        if (world == null) {
            WorldCreator creator = new WorldCreator(HUB_WORLD_NAME);
            creator.type(WorldType.FLAT);
            creator.generator(new VoidChunkGenerator());
            creator.generateStructures(false);
            world = creator.createWorld();
        }
        if (world == null) {
            plugin.getLogger().severe("Could not create sandbox selector world '" + HUB_WORLD_NAME + "'.");
            return null;
        }

        world.setSpawnLocation(0, 64, 0);
        world.setGameRuleValue("doMobSpawning", "false");
        world.setGameRuleValue("doDaylightCycle", "false");
        world.setGameRuleValue("doWeatherCycle", "false");
        world.setGameRuleValue("keepInventory", "true");
        world.setTime(6000L);
        world.setStorm(false);
        world.setThundering(false);

        // A separate protected hub means the existing Monster Maze lobby/map is untouched.
        for (int x = -16; x <= 16; x++) {
            for (int z = -16; z <= 16; z++) {
                Block floor = world.getBlockAt(x, 63, z);
                if (floor.getType() == Material.AIR) {
                    floor.setType(Material.STONE, false);
                }
            }
        }
        Block previewGlass = world.getBlockAt(0, 64, 4);
        if (previewGlass.getType() == Material.AIR) previewGlass.setType(Material.GLASS, false);
        for (int x = -15; x <= 15; x += 30) {
            for (int z = -15; z <= 15; z += 30) {
                Block light = world.getBlockAt(x, 64, z);
                if (light.getType() == Material.AIR) light.setType(Material.GLOWSTONE, false);
            }
        }
        return world;
    }

    public Location getHubSpawn() {
        World world = ensureHubWorld();
        return world == null ? null : new Location(world, 0.5, 64.0, 0.5, -90.0F, 0.0F);
    }

    public void sendToHub(Player player) {
        if (player == null || !player.isOnline()) return;
        Location spawn = getHubSpawn();
        if (spawn == null) {
            player.sendMessage(ChatColor.RED + "The game-selection sandbox could not be loaded.");
            return;
        }
        player.teleport(spawn);
        player.setGameMode(GameMode.SURVIVAL);
        player.getInventory().clear();
        player.getInventory().setArmorContents(null);
        player.setFallDistance(0.0F);
    }

    public void openGameSelector(Player player) {
        if (player == null || !player.isOnline()) return;
        selectedSandboxGame.remove(player.getUniqueId());
        Inventory menu = Bukkit.createInventory(null, 27, SELECTOR_TITLE);
        menu.setItem(10, item(Material.IRON_SWORD, ChatColor.GREEN + "Monster Maze",
                "Open the existing Monster Maze lobby.", "All existing Monster Maze features are kept."));
        menu.setItem(12, item(Material.SLIME_BALL, ChatColor.YELLOW + "Runner",
                "Open its isolated preview sandbox.", "Full Runner gameplay is not included yet."));
        menu.setItem(13, item(Material.GOLD_INGOT, ChatColor.GOLD + "Bacon Brawl",
                "Open its isolated preview sandbox.", "Full Bacon Brawl gameplay is not included yet."));
        menu.setItem(14, item(Material.ENDER_PEARL, ChatColor.AQUA + "Dragon Escape",
                "Open its isolated preview sandbox.", "Full Dragon Escape gameplay is not included yet."));
        menu.setItem(15, item(Material.DRAGON_EGG, ChatColor.LIGHT_PURPLE + "Evolution",
                "Open its isolated preview sandbox.", "Full Evolution gameplay is not included yet."));
        menu.setItem(16, item(Material.GLASS, ChatColor.WHITE + "Micro Battles",
                "Open its isolated preview sandbox.", "Full Micro Battles gameplay is not included yet."));
        menu.setItem(22, item(Material.BOOK, ChatColor.GRAY + "Sandbox",
                "Other game entries are prototype-only.", "They do not start fake or incomplete game rounds."));
        player.openInventory(menu);
    }

    private void openSandboxMenu(Player player, String gameKey) {
        selectedSandboxGame.put(player.getUniqueId(), gameKey);
        Inventory menu = Bukkit.createInventory(null, 27, SANDBOX_PREFIX + displayName(gameKey));
        menu.setItem(11, item(Material.EXPERIENCE_BOTTLE, ChatColor.GREEN + "Preview cosmetic",
                "Show the current visual prototype.", "Temporary effect; the sandbox restores its sample."));
        menu.setItem(13, item(Material.BOOK, ChatColor.YELLOW + "Prototype sandbox",
                "This game has a selector and preview only.", "Its live game loop is not present in this repository."));
        menu.setItem(15, item(Material.ARROW, ChatColor.AQUA + "Back to game selector",
                "Return to the game-selection layer."));
        menu.setItem(22, item(Material.IRON_SWORD, ChatColor.GREEN + "Enter Monster Maze",
                "Return to the original Monster Maze lobby."));
        player.openInventory(menu);
    }

    private ItemStack item(Material material, String name, String... lore) {
        ItemStack stack = new ItemStack(material, 1);
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            meta.setLore(Arrays.asList(lore));
            stack.setItemMeta(meta);
        }
        return stack;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("Run /games in-game to open the game selector.");
            return true;
        }
        Player player = (Player) sender;
        if (game.isRunning()) {
            player.sendMessage(ChatColor.RED + "The game selector is unavailable while a Monster Maze round is starting or live.");
            return true;
        }
        sendToHub(player);
        openGameSelector(player);
        return true;
    }

    @EventHandler
    public void onSelectorClick(InventoryClickEvent event) {
        String title = event.getView().getTitle();
        if (!SELECTOR_TITLE.equals(title) && (title == null || !title.startsWith(SANDBOX_PREFIX))) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player)) return;
        if (event.getRawSlot() < 0 || event.getRawSlot() >= event.getView().getTopInventory().getSize()) return;

        Player player = (Player) event.getWhoClicked();
        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || clicked.getType() == Material.AIR) return;
        int slot = event.getRawSlot();

        if (SELECTOR_TITLE.equals(title)) {
            if (slot == 10) {
                enterMonsterMaze(player);
            } else {
                String key = gameKeyForSlot(slot);
                if (key != null) {
                    sendToHub(player);
                    openSandboxMenu(player, key);
                    player.sendMessage(ChatColor.YELLOW + displayName(key)
                            + " is sandbox/preview-only; no live game round is started.");
                }
            }
            return;
        }

        String key = selectedSandboxGame.get(player.getUniqueId());
        if (key == null) {
            openGameSelector(player);
            return;
        }
        if (slot == 11) {
            runPreview(player, key);
        } else if (slot == 15) {
            openGameSelector(player);
        } else if (slot == 22) {
            enterMonsterMaze(player);
        }
    }

    @EventHandler
    public void onSelectorDrag(InventoryDragEvent event) {
        String title = event.getView().getTitle();
        if (SELECTOR_TITLE.equals(title) || (title != null && title.startsWith(SANDBOX_PREFIX))) {
            event.setCancelled(true);
        }
    }

    private void enterMonsterMaze(Player player) {
        player.closeInventory();
        selectedSandboxGame.remove(player.getUniqueId());
        if (game.isLive()) {
            Location center = game.getCenter();
            if (center != null) {
                player.teleport(center.clone().add(0, 15, 0));
                player.setGameMode(GameMode.SPECTATOR);
                player.sendMessage(ChatColor.YELLOW + "Monster Maze is already live; you joined as a spectator.");
            }
            return;
        }
        if (game.isRunning()) {
            player.sendMessage(ChatColor.RED + "Monster Maze is starting. Wait for this round to finish.");
            sendToHub(player);
            return;
        }
        game.sendToLobby(player);
    }

    private void runPreview(final Player player, final String gameKey) {
        player.closeInventory();
        if (Bukkit.getPluginManager().getPlugin(COSMETIC_PLUGIN) == null) {
            player.sendMessage(ChatColor.RED + "The cosmetic preview plugin is unavailable on this server. These prototype previews currently target Spigot 1.8.8.");
            return;
        }
        Location preview = getHubSpawn();
        if (preview == null) return;
        if ("microbattles".equals(gameKey)) {
            preview.setYaw(0.0F);
            preview.setPitch(15.0F);
        } else {
            preview.setYaw(-90.0F);
            preview.setPitch(0.0F);
        }
        player.teleport(preview);
        Bukkit.getScheduler().runTaskLater(plugin, new Runnable() {
            @Override public void run() {
                if (!player.isOnline() || !HUB_WORLD_NAME.equals(player.getWorld().getName())) return;
                player.performCommand("cosmeticproto preview " + gameKey);
            }
        }, 2L);

        final long returnTicks = "baconbrawl".equals(gameKey) ? 20L * 21L
                : ("dragonescape".equals(gameKey) ? 20L * 16L
                : ("evolution".equals(gameKey) ? 20L * 3L : 20L * 13L));
        Bukkit.getScheduler().runTaskLater(plugin, new Runnable() {
            @Override public void run() {
                if (player.isOnline() && HUB_WORLD_NAME.equals(player.getWorld().getName())) {
                    openSandboxMenu(player, gameKey);
                }
            }
        }, returnTicks);
    }

    private String gameKeyForSlot(int slot) {
        if (slot == 12) return "runner";
        if (slot == 13) return "baconbrawl";
        if (slot == 14) return "dragonescape";
        if (slot == 15) return "evolution";
        if (slot == 16) return "microbattles";
        return null;
    }

    private String displayName(String key) {
        if ("baconbrawl".equals(key)) return "Bacon Brawl";
        if ("dragonescape".equals(key)) return "Dragon Escape";
        if ("microbattles".equals(key)) return "Micro Battles";
        if ("evolution".equals(key)) return "Evolution";
        if ("runner".equals(key)) return "Runner";
        return "Game";
    }

    @EventHandler
    public void onHubFall(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        if (!HUB_WORLD_NAME.equals(player.getWorld().getName())) return;
        if (event.getTo() != null && event.getTo().getY() < 55.0D) {
            sendToHub(player);
            openGameSelector(player);
            player.sendMessage(ChatColor.YELLOW + "Returned you to the sandbox after falling.");
        }
    }

    @EventHandler
    public void onHubBlockBreak(BlockBreakEvent event) {
        if (HUB_WORLD_NAME.equals(event.getBlock().getWorld().getName())) event.setCancelled(true);
    }

    @EventHandler
    public void onHubBlockPlace(BlockPlaceEvent event) {
        if (HUB_WORLD_NAME.equals(event.getBlock().getWorld().getName())) event.setCancelled(true);
    }

    @EventHandler
    public void onHubDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player
                && HUB_WORLD_NAME.equals(event.getEntity().getWorld().getName())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        selectedSandboxGame.remove(event.getPlayer().getUniqueId());
    }
}
