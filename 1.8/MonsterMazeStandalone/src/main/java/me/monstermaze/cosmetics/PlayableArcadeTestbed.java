package me.monstermaze.cosmetics;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.DyeColor;
import org.bukkit.Effect;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.WorldType;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Pig;
import org.bukkit.entity.Player;
import org.bukkit.entity.Sheep;
import org.bukkit.entity.Slime;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

/**
 * Playable, isolated 1.8.8 test adaptations for the arcade cosmetic prototypes.
 * These deliberately use generated test arenas so they do not require the
 * original Mineplex Redis/MySQL/API stack. The Mineplex Arcade game classes are
 * coupled to that stack and are not copied wholesale into this standalone plugin.
 */
public final class PlayableArcadeTestbed implements Listener, CommandExecutor {
    public static final String WORLD_NAME = "arcade_cosmetic_test";
    private static final int FLOOR_Y = 70;
    private static final int HUB_Y = 63;
    private static final int SNAKE_RADIUS = 19;

    private final MinigameCosmeticPrototypes cosmetics;
    private final Random random = new Random();
    private final Map<UUID, SavedPlayer> joinedStates = new LinkedHashMap<UUID, SavedPlayer>();
    private final Map<UUID, SavedPlayer> roundStates = new LinkedHashMap<UUID, SavedPlayer>();
    private final Set<UUID> participants = new LinkedHashSet<UUID>();
    private final Set<UUID> alive = new LinkedHashSet<UUID>();
    private final Set<UUID> eliminated = new HashSet<UUID>();
    private final Map<UUID, Location> spawnLocations = new HashMap<UUID, Location>();
    private final Map<UUID, Integer> laneZ = new HashMap<UUID, Integer>();
    private final Map<UUID, Integer> runnerLastClearedX = new HashMap<UUID, Integer>();
    private final Map<UUID, Integer> evolutionKills = new HashMap<UUID, Integer>();
    private final Map<UUID, Integer> baconKills = new HashMap<UUID, Integer>();
    private final Map<UUID, Pig> baconPigs = new HashMap<UUID, Pig>();
    private final Map<UUID, List<Sheep>> snakeTails = new HashMap<UUID, List<Sheep>>();
    private final Map<UUID, List<Location>> snakeHistory = new HashMap<UUID, List<Location>>();
    private final Map<UUID, Integer> snakeTailTarget = new HashMap<UUID, Integer>();
    private final Map<UUID, DyeColor> snakeBaseColour = new HashMap<UUID, DyeColor>();
    private final Map<UUID, String> snakeAbility = new HashMap<UUID, String>();
    private final Map<UUID, Long> snakeAbilityUntil = new HashMap<UUID, Long>();
    private final Set<Slime> snakeFood = new LinkedHashSet<Slime>();
    private final List<Block> dragonBeacons = new ArrayList<Block>();
    private final Map<UUID, Integer> evolutionLevel = new HashMap<UUID, Integer>();

    private World world;
    private Mode mode;
    private long ticks;
    private boolean endScheduled;
    private int taskId = -1;
    private Block microCenter;
    private List<Location> microBorderPoints = Collections.emptyList();

    public PlayableArcadeTestbed(MinigameCosmeticPrototypes cosmetics) {
        this.cosmetics = cosmetics;
        this.taskId = Bukkit.getScheduler().scheduleSyncRepeatingTask(cosmetics.getHostPlugin(), new Runnable() {
            @Override public void run() { tick(); }
        }, 2L, 2L);
    }

    public void shutdown() {
        stopGame(false, "Test server stopping.");
        if (taskId >= 0) Bukkit.getScheduler().cancelTask(taskId);
        for (Map.Entry<UUID, SavedPlayer> entry : new ArrayList<Map.Entry<UUID, SavedPlayer>>(joinedStates.entrySet())) {
            Player player = Bukkit.getPlayer(entry.getKey());
            if (player != null && player.isOnline()) entry.getValue().restore(player, true);
        }
        joinedStates.clear();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0 || "help".equalsIgnoreCase(args[0])) {
            help(sender);
            return true;
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        if ("join".equals(sub)) {
            if (!(sender instanceof Player)) {
                sender.sendMessage("Only an in-game player can join the arcade test world.");
                return true;
            }
            join((Player) sender);
            return true;
        }
        if ("leave".equals(sub)) {
            if (!(sender instanceof Player)) {
                sender.sendMessage("Only an in-game player can leave the arcade test world.");
                return true;
            }
            leave((Player) sender);
            return true;
        }
        if ("list".equals(sub)) {
            sender.sendMessage(ChatColor.GOLD + "Playable test modes: runner, baconbrawl, dragonescape, microbattles, evolution, snake");
            return true;
        }
        if ("status".equals(sub)) {
            sender.sendMessage(ChatColor.YELLOW + "World: " + (world == null ? "not created" : world.getName())
                    + "; mode: " + (mode == null ? "none" : mode.key)
                    + "; participants: " + participants.size() + "; alive: " + alive.size());
            return true;
        }
        if ("unlock".equals(sub)) {
            if (!(sender instanceof Player)) {
                sender.sendMessage("Run /arcadetest unlock in-game to unlock all test cosmetics for yourself.");
                return true;
            }
            Player player = (Player) sender;
            World testWorld = ensureWorld();
            if (testWorld == null) return true;
            if (player.getWorld() != testWorld) {
                sender.sendMessage(ChatColor.RED + "Join the isolated test world first with /arcadetest join.");
                return true;
            }
            cosmetics.unlockAllFor(player.getUniqueId());
            sender.sendMessage(ChatColor.GREEN + "Unlocked all prototype cosmetics for testing. Use /cosmeticproto equip <game> <choice>.");
            return true;
        }
        if ("stop".equals(sub)) {
            if (mode == null) {
                sender.sendMessage(ChatColor.YELLOW + "No test round is running.");
            } else {
                stopGame(true, "The test round was stopped.");
            }
            return true;
        }
        if ("start".equals(sub)) {
            if (args.length < 2) {
                sender.sendMessage(ChatColor.YELLOW + "Usage: /arcadetest start <runner|baconbrawl|dragonescape|microbattles|evolution|snake> [runner-map-palette]");
                return true;
            }
            Mode selected = Mode.parse(args[1]);
            if (selected == null) {
                sender.sendMessage(ChatColor.RED + "Unknown game. Use /arcadetest list.");
                return true;
            }
            if (sender instanceof Player) {
                Player player = (Player) sender;
                World testWorld = ensureWorld();
                if (testWorld == null) return true;
                if (player.getWorld() != testWorld) join(player);
            }
            if (mode != null) stopGame(true, "The previous test round was replaced.");
            String runnerPalette = args.length > 2 ? args[2] : null;
            startGame(selected, sender, runnerPalette);
            return true;
        }
        help(sender);
        return true;
    }

    private void help(CommandSender sender) {
        sender.sendMessage(ChatColor.GOLD + "Playable Arcade Cosmetic Testbed");
        sender.sendMessage(ChatColor.YELLOW + "/arcadetest join" + ChatColor.GRAY + " - enter the isolated 1.8 test world");
        sender.sendMessage(ChatColor.YELLOW + "/arcadetest start <game> [runner-map-palette]" + ChatColor.GRAY + " - launch a playable test round");
        sender.sendMessage(ChatColor.YELLOW + "/arcadetest stop" + ChatColor.GRAY + " - end the current round");
        sender.sendMessage(ChatColor.YELLOW + "/arcadetest unlock" + ChatColor.GRAY + " - unlock every test cosmetic for yourself in the test world");
        sender.sendMessage(ChatColor.YELLOW + "/arcadetest leave" + ChatColor.GRAY + " - return to where you were before joining");
        sender.sendMessage(ChatColor.GRAY + "Use /cosmeticproto list, equip, off and status to choose cosmetics.");
    }

    private void join(Player player) {
        World testWorld = ensureWorld();
        if (testWorld == null) {
            player.sendMessage(ChatColor.RED + "Could not create the isolated arcade test world.");
            return;
        }
        if (!joinedStates.containsKey(player.getUniqueId())) {
            joinedStates.put(player.getUniqueId(), new SavedPlayer(player));
        }
        player.teleport(new Location(testWorld, 0.5D, HUB_Y + 1.0D, 0.5D, -90.0F, 0.0F));
        player.setGameMode(GameMode.SURVIVAL);
        player.setFallDistance(0.0F);
        player.sendMessage(ChatColor.GREEN + "Joined the isolated arcade test world.");
        player.sendMessage(ChatColor.GRAY + "Choose a mode with /arcadetest start <game>, or use /games.");
    }

    private void leave(Player player) {
        if (mode != null && participants.contains(player.getUniqueId())) {
            stopGame(true, "The round ended because a participant left.");
        }
        SavedPlayer saved = joinedStates.remove(player.getUniqueId());
        if (saved != null) saved.restore(player, true);
        else {
            player.teleport(new Location(ensureWorld(), 0.5D, HUB_Y + 1.0D, 0.5D));
        }
        player.sendMessage(ChatColor.GREEN + "Left the arcade test world.");
    }

    private World ensureWorld() {
        World loaded = Bukkit.getWorld(WORLD_NAME);
        if (loaded == null) {
            WorldCreator creator = new WorldCreator(WORLD_NAME);
            creator.type(WorldType.FLAT);
            creator.generateStructures(false);
            loaded = creator.createWorld();
        }
        if (loaded == null) return null;
        world = loaded;
        world.setGameRuleValue("doMobSpawning", "false");
        world.setGameRuleValue("doDaylightCycle", "false");
        world.setGameRuleValue("doWeatherCycle", "false");
        world.setGameRuleValue("keepInventory", "true");
        world.setTime(6000L);
        world.setStorm(false);
        world.setThundering(false);
        world.setSpawnLocation(0, HUB_Y + 1, 0);
        for (int x = -10; x <= 10; x++) {
            for (int z = -10; z <= 10; z++) {
                Block floor = world.getBlockAt(x, HUB_Y, z);
                if (floor.getType() != Material.STONE) floor.setType(Material.STONE, false);
            }
        }
        return world;
    }

    private void startGame(Mode selected, CommandSender sender, String runnerPalette) {
        World testWorld = ensureWorld();
        if (testWorld == null) return;

        participants.clear();
        alive.clear();
        eliminated.clear();
        spawnLocations.clear();
        roundStates.clear();
        laneZ.clear();
        runnerLastClearedX.clear();
        evolutionKills.clear();
        baconKills.clear();
        evolutionLevel.clear();
        ticks = 0L;
        endScheduled = false;
        mode = selected;

        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.getWorld() != testWorld) continue;
            if (!joinedStates.containsKey(player.getUniqueId())) joinedStates.put(player.getUniqueId(), new SavedPlayer(player));
            participants.add(player.getUniqueId());
            alive.add(player.getUniqueId());
            roundStates.put(player.getUniqueId(), new SavedPlayer(player));
            player.setGameMode(GameMode.SURVIVAL);
            player.setFallDistance(0.0F);
            player.setHealth(player.getMaxHealth());
            player.setFoodLevel(20);
            player.getInventory().clear();
            player.getInventory().setArmorContents(null);
            for (PotionEffect effect : new ArrayList<PotionEffect>(player.getActivePotionEffects())) player.removePotionEffect(effect.getType());
        }
        if (participants.isEmpty()) {
            mode = null;
            sender.sendMessage(ChatColor.RED + "No players are in the test world. Run /arcadetest join first.");
            return;
        }

        if (selected == Mode.RUNNER) startRunner(runnerPalette);
        else if (selected == Mode.BACONBRAWL) startBaconBrawl();
        else if (selected == Mode.DRAGONESCAPE) startDragonEscape();
        else if (selected == Mode.MICROBATTLES) startMicroBattles();
        else if (selected == Mode.EVOLUTION) startEvolution();
        else if (selected == Mode.SNAKE) startSnake();

        String label = selected.displayName;
        for (UUID id : participants) {
            Player player = Bukkit.getPlayer(id);
            if (player != null) {
                player.sendMessage(ChatColor.GOLD + "Started playable " + label + " test round.");
                player.sendMessage(ChatColor.GRAY + "Cosmetic choices apply to the live round. /arcadetest stop ends it.");
            }
        }
    }

    private Set<Byte> parseRunnerPalette(String paletteText) {
        Map<String, Byte> colourIds = new HashMap<String, Byte>();
        colourIds.put("white", (byte) 0);
        colourIds.put("orange", (byte) 1);
        colourIds.put("magenta", (byte) 2);
        colourIds.put("lightblue", (byte) 3);
        colourIds.put("yellow", (byte) 4);
        colourIds.put("lime", (byte) 5);
        colourIds.put("pink", (byte) 6);
        colourIds.put("gray", (byte) 7);
        colourIds.put("silver", (byte) 8);
        colourIds.put("cyan", (byte) 9);
        colourIds.put("purple", (byte) 10);
        colourIds.put("blue", (byte) 11);
        colourIds.put("brown", (byte) 12);
        colourIds.put("green", (byte) 13);
        colourIds.put("red", (byte) 14);
        colourIds.put("black", (byte) 15);
        Set<Byte> palette = new HashSet<Byte>();
        if (paletteText == null || paletteText.trim().isEmpty()) return palette;
        for (String token : paletteText.split(",")) {
            Byte id = colourIds.get(token.trim().toLowerCase(Locale.ROOT));
            if (id != null) palette.add(id);
        }
        return palette;
    }

    private int baseX() {
        return mode.baseX;
    }

    private int assignLane(UUID playerId, int index, int count) {
        int z = (index - ((count - 1) / 2)) * 7;
        laneZ.put(playerId, Integer.valueOf(z));
        return z;
    }

    private void startRunner(String paletteText) {
        Set<Byte> mapPalette = parseRunnerPalette(paletteText);
        if (paletteText == null || paletteText.trim().isEmpty()) {
            mapPalette = new HashSet<Byte>(Arrays.asList(Byte.valueOf((byte) 14), Byte.valueOf((byte) 11)));
        }
        int index = 0;
        for (UUID id : participants) {
            int z = assignLane(id, index++, participants.size());
            byte data = cosmetics.getRunnerClayData(id, mapPalette);
            int start = baseX() + 1;
            int end = baseX() + 52;
            for (int x = start; x <= end; x++) {
                for (int dz = -1; dz <= 1; dz++) {
                    Block block = world.getBlockAt(x, FLOOR_Y, z + dz);
                    block.setType(Material.STAINED_CLAY, false);
                    block.setData(data, false);
                }
            }
            world.getBlockAt(end, FLOOR_Y, z).setType(Material.EMERALD_BLOCK, false);
            spawnLocations.put(id, new Location(world, start + 0.5D, FLOOR_Y + 1.0D, z + 0.5D, -90.0F, 0.0F));
            runnerLastClearedX.put(id, Integer.valueOf(start - 1));
            Player player = Bukkit.getPlayer(id);
            teleportForRound(player, spawnLocations.get(id));
            player.sendMessage(ChatColor.YELLOW + "Runner: keep moving; the clay behind you disappears. Reach the emerald finish.");
            player.sendMessage(ChatColor.GRAY + "Runner palette: " + (paletteText == null || paletteText.trim().isEmpty() ? "default red/blue" : paletteText)
                    + ". For the random-third-colour fallback, equip lightblue then run /arcadetest start runner lightblue,red.");
        }
    }

    private void startBaconBrawl() {
        int center = baseX();
        buildFloor(center - 18, center + 18, -18, 18, FLOOR_Y - 1, Material.STONE);
        cosmetics.resetBaconBrawlRound();
        int index = 0;
        for (UUID id : participants) {
            int angleSlot = index++;
            double angle = (Math.PI * 2.0D * angleSlot) / Math.max(1, participants.size());
            Location spawn = new Location(world, center + Math.cos(angle) * 10.0D, FLOOR_Y, Math.sin(angle) * 10.0D);
            spawn.setYaw((float) (180.0D - angle * 180.0D / Math.PI));
            spawnLocations.put(id, spawn);
            Player player = Bukkit.getPlayer(id);
            teleportForRound(player, spawn);
            player.getInventory().addItem(new ItemStack(Material.STONE_SWORD, 1));
            Pig pig = (Pig) world.spawnEntity(spawn.clone().add(0.0D, 0.1D, 0.0D), EntityType.PIG);
            pig.setCustomName(ChatColor.GOLD + player.getName());
            pig.setCustomNameVisible(true);
            pig.setRemoveWhenFarAway(false);
            baconPigs.put(id, pig);
            baconKills.put(id, Integer.valueOf(0));
            cosmetics.registerBaconBrawlPig(id, pig);
        }
        for (UUID id : participants) {
            Player player = Bukkit.getPlayer(id);
            if (player != null) player.sendMessage(ChatColor.YELLOW + "Bacon Brawl: fight with your sword. The pig for the kill leader wears their equipped accessory.");
        }
    }

    private void startDragonEscape() {
        int index = 0;
        for (UUID id : participants) {
            int z = assignLane(id, index++, participants.size());
            int start = baseX() + 1;
            int end = baseX() + 82;
            for (int x = start; x <= end; x++) {
                boolean gap = (x >= baseX() + 17 && x <= baseX() + 18)
                        || (x >= baseX() + 34 && x <= baseX() + 36)
                        || (x >= baseX() + 51 && x <= baseX() + 52)
                        || (x >= baseX() + 67 && x <= baseX() + 68);
                for (int dz = -1; dz <= 1; dz++) {
                    Block block = world.getBlockAt(x, FLOOR_Y - 1, z + dz);
                    block.setType(gap ? Material.AIR : Material.STONE, false);
                }
            }
            Block finish = world.getBlockAt(end, FLOOR_Y - 1, z);
            finish.setType(Material.GOLD_BLOCK, false);
            buildBeaconForPlayer(id, baseX() + 10, z + 4);
            Location spawn = new Location(world, start + 0.5D, FLOOR_Y, z + 0.5D, -90.0F, 0.0F);
            spawnLocations.put(id, spawn);
            teleportForRound(Bukkit.getPlayer(id), spawn);
            Player player = Bukkit.getPlayer(id);
            if (player != null) player.sendMessage(ChatColor.YELLOW + "Dragon Escape: jump the gaps and reach the gold finish. Your lane's beacon uses your equipped colour.");
        }
    }

    private void buildBeaconForPlayer(UUID owner, int x, int z) {
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) world.getBlockAt(x + dx, FLOOR_Y - 1, z + dz).setType(Material.IRON_BLOCK, false);
        }
        Block beacon = world.getBlockAt(x, FLOOR_Y, z);
        beacon.setType(Material.BEACON, false);
        world.getBlockAt(x, FLOOR_Y + 1, z).setType(Material.GLASS, false);
        dragonBeacons.add(beacon);
        cosmetics.applyDragonEscapeBeaconColour(beacon, owner);
    }

    private void startMicroBattles() {
        int centerX = baseX();
        buildFloor(centerX - 20, centerX + 20, -20, 20, FLOOR_Y - 1, Material.STONE);
        microCenter = world.getBlockAt(centerX, FLOOR_Y - 1, 0);
        microCenter.setType(Material.STAINED_GLASS, false);
        microCenter.setData((byte) 8, false);
        List<Location> border = new ArrayList<Location>();
        for (int n = -18; n <= 18; n += 3) {
            border.add(new Location(world, centerX + n + 0.5D, FLOOR_Y, -18 + 0.5D));
            border.add(new Location(world, centerX + n + 0.5D, FLOOR_Y, 18 + 0.5D));
            border.add(new Location(world, centerX - 18 + 0.5D, FLOOR_Y, n + 0.5D));
            border.add(new Location(world, centerX + 18 + 0.5D, FLOOR_Y, n + 0.5D));
        }
        microBorderPoints = border;
        int index = 0;
        for (UUID id : participants) {
            int corner = index++ % 4;
            int x = centerX + (corner < 2 ? -13 : 13);
            int z = (corner % 2 == 0 ? -13 : 13);
            Location spawn = new Location(world, x + 0.5D, FLOOR_Y, z + 0.5D);
            spawnLocations.put(id, spawn);
            Player player = Bukkit.getPlayer(id);
            teleportForRound(player, spawn);
            player.getInventory().addItem(new ItemStack(Material.STONE_SWORD, 1), new ItemStack(Material.BOW, 1),
                    new ItemStack(Material.ARROW, 16), new ItemStack(Material.COBBLESTONE, 32));
            cosmetics.registerMicroBattlesArena(player, microCenter, microBorderPoints);
            if (player != null) player.sendMessage(ChatColor.YELLOW + "Micro Battles: fight from the four corners. Centre glass and border particles are customised separately for each viewer.");
        }
    }

    private void startEvolution() {
        int center = baseX();
        buildFloor(center - 16, center + 16, -16, 16, FLOOR_Y - 1, Material.GRASS);
        int index = 0;
        for (UUID id : participants) {
            double angle = (Math.PI * 2.0D * index++) / Math.max(1, participants.size());
            Location spawn = new Location(world, center + Math.cos(angle) * 9.0D, FLOOR_Y, Math.sin(angle) * 9.0D);
            spawn.setYaw((float) (180.0D - angle * 180.0D / Math.PI));
            spawnLocations.put(id, spawn);
            evolutionKills.put(id, Integer.valueOf(0));
            evolutionLevel.put(id, Integer.valueOf(1));
            teleportForRound(Bukkit.getPlayer(id), spawn);
            giveEvolutionKit(Bukkit.getPlayer(id), 1);
        }
        for (UUID id : participants) {
            Player player = Bukkit.getPlayer(id);
            if (player != null) player.sendMessage(ChatColor.YELLOW + "Evolution: every kill evolves your weapon. Equipped dragon-egg particles trigger on each evolution.");
        }
    }

    private void startSnake() {
        int center = baseX();
        buildFloor(center - SNAKE_RADIUS, center + SNAKE_RADIUS, -SNAKE_RADIUS, SNAKE_RADIUS, FLOOR_Y - 1, Material.GRASS);
        int index = 0;
        for (UUID id : participants) {
            int slot = index++;
            double angle = (Math.PI * 2.0D * slot) / Math.max(1, participants.size());
            Location spawn = new Location(world, center + Math.cos(angle) * 9.0D, FLOOR_Y, Math.sin(angle) * 9.0D);
            spawn.setYaw((float) (180.0D - angle * 180.0D / Math.PI));
            spawnLocations.put(id, spawn);
            Player player = Bukkit.getPlayer(id);
            teleportForRound(player, spawn);
            if (player == null) continue;
            player.getInventory().setItem(0, namedItem(Material.REDSTONE, ChatColor.RED + "Super Snake", "Right-click: temporary invulnerability"));
            player.getInventory().setItem(1, namedItem(Material.FEATHER, ChatColor.YELLOW + "Speed", "Right-click: temporary speed"));
            player.getInventory().setItem(2, namedItem(Material.COOKIE, ChatColor.LIGHT_PURPLE + "Reverse", "Right-click: temporary reverse movement"));
            List<Sheep> tail = new ArrayList<Sheep>();
            List<Location> history = new ArrayList<Location>();
            Vector back = spawn.getDirection().setY(0.0D).normalize().multiply(-0.65D);
            for (int n = 60; n >= 0; n--) history.add(spawn.clone().add(back.clone().multiply(n)));
            snakeBaseColour.put(id, DyeColor.getByDyeData((byte) (slot % 16)));
            Sheep head = (Sheep) world.spawnEntity(spawn.clone(), EntityType.SHEEP);
            head.setColor(snakeBaseColour.get(id));
            head.setRemoveWhenFarAway(false);
            head.setCustomName(ChatColor.GREEN + player.getName() + " head");
            head.setPassenger(player);
            tail.add(head);
            snakeTails.put(id, tail);
            snakeHistory.put(id, history);
            snakeTailTarget.put(id, Integer.valueOf(5));
            for (int n = 1; n <= 4; n++) {
                int trailIndex = Math.max(0, history.size() - 1 - (n * 4));
                Location tailLoc = history.get(trailIndex).clone();
                Sheep sheep = (Sheep) world.spawnEntity(tailLoc, EntityType.SHEEP);
                sheep.setColor(snakeBaseColour.get(id));
                sheep.setRemoveWhenFarAway(false);
                sheep.setCustomName(ChatColor.GREEN + player.getName() + " tail");
                tail.add(sheep);
            }
        }
        spawnSnakeFood();
        for (UUID id : participants) {
            Player player = Bukkit.getPlayer(id);
            if (player != null) player.sendMessage(ChatColor.YELLOW + "Snake: steer by looking where you want to travel. Eat slimes to grow. Redstone = Super Snake, feather = speed, cookie = reverse.");
        }
    }

    private ItemStack namedItem(Material material, String name, String lore) {
        ItemStack item = new ItemStack(material, 1);
        org.bukkit.inventory.meta.ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            meta.setLore(Collections.singletonList(lore));
            item.setItemMeta(meta);
        }
        return item;
    }

    private void giveEvolutionKit(Player player, int level) {
        if (player == null) return;
        player.getInventory().clear();
        Material weapon = level >= 4 ? Material.DIAMOND_SWORD
                : (level == 3 ? Material.IRON_SWORD : (level == 2 ? Material.STONE_SWORD : Material.WOOD_SWORD));
        player.getInventory().addItem(new ItemStack(weapon, 1));
        player.setLevel(level);
    }

    private void teleportForRound(Player player, Location location) {
        if (player == null || location == null) return;
        player.teleport(location);
        player.setGameMode(GameMode.SURVIVAL);
        player.setFallDistance(0.0F);
        player.setHealth(player.getMaxHealth());
        player.setFoodLevel(20);
        player.setVelocity(new Vector(0, 0, 0));
    }

    private void buildFloor(int minX, int maxX, int minZ, int maxZ, int y, Material material) {
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) world.getBlockAt(x, y, z).setType(material, false);
        }
    }

    private void tick() {
        if (mode == null) return;
        ticks += 2L;
        if (mode == Mode.RUNNER) tickRunner();
        else if (mode == Mode.BACONBRAWL) tickBaconBrawl();
        else if (mode == Mode.SNAKE) tickSnake();
        else if (mode == Mode.DRAGONESCAPE) tickDragonEscape();
        if (mode == Mode.SNAKE && ticks % 200L == 0L) spawnSnakeFood();
    }

    private void tickRunner() {
        for (UUID id : new ArrayList<UUID>(alive)) {
            Player player = Bukkit.getPlayer(id);
            if (player == null || !player.isOnline()) continue;
            if (player.getLocation().getY() < FLOOR_Y - 6) {
                eliminate(player, "fell off the Runner lane");
                continue;
            }
            int x = player.getLocation().getBlockX();
            Integer old = runnerLastClearedX.get(id);
            int cleared = old == null ? baseX() : old.intValue();
            while (cleared < x - 1) {
                cleared++;
                Integer z = laneZ.get(id);
                if (z != null) {
                    for (int dz = -1; dz <= 1; dz++) world.getBlockAt(cleared, FLOOR_Y, z.intValue() + dz).setType(Material.AIR, false);
                }
            }
            runnerLastClearedX.put(id, Integer.valueOf(cleared));
            if (x >= baseX() + 52) finishPlayer(player, "reached the Runner finish");
        }
    }

    private void tickBaconBrawl() {
        for (UUID id : participants) {
            Player player = Bukkit.getPlayer(id);
            Pig pig = baconPigs.get(id);
            if (player == null || pig == null || !pig.isValid() || !alive.contains(id)) continue;
            if (ticks % 4L == 0L) pig.teleport(player.getLocation().clone().add(0.0D, 0.15D, 0.0D));
        }
    }

    private void tickDragonEscape() {
        for (UUID id : new ArrayList<UUID>(alive)) {
            Player player = Bukkit.getPlayer(id);
            if (player == null || !player.isOnline()) continue;
            if (player.getLocation().getY() < FLOOR_Y - 7) {
                eliminate(player, "fell from the Dragon Escape course");
                continue;
            }
            if (player.getLocation().getBlockX() >= baseX() + 82) finishPlayer(player, "completed Dragon Escape");
        }
    }

    private void tickSnake() {
        for (UUID id : new ArrayList<UUID>(alive)) {
            Player player = Bukkit.getPlayer(id);
            if (player == null || !player.isOnline()) continue;

            List<Location> history = snakeHistory.get(id);
            List<Sheep> tail = snakeTails.get(id);
            if (history == null || tail == null || tail.isEmpty()) {
                eliminate(player, "lost the Snake tail");
                continue;
            }

            updateSnakeTailColours(id, tail);
            Sheep head = tail.get(0);
            if (head == null || !head.isValid()) {
                eliminate(player, "lost the Snake head");
                continue;
            }

            // In this testbed, the mounted sheep follows the player's look direction.
            // Speed increases the step; Reverse moves backwards for its two-second window.
            boolean reversing = isSnakeReversing(id);
            if (head.isValid()) {
                Vector direction = player.getLocation().getDirection().setY(0.0D);
                if (direction.lengthSquared() < 0.001D) direction = head.getLocation().getDirection().setY(0.0D);
                if (direction.lengthSquared() > 0.001D) {
                    direction.normalize();
                    if (reversing) direction.multiply(-1.0D);
                    double step = 0.48D + Math.min(0.12D, tail.size() * 0.002D);
                    if (isSnakeAbilityActive(id, "speed")) step *= 1.5D;
                    direction.multiply(step);
                    Location next = head.getLocation().clone().add(direction);
                    next.setY(head.getLocation().getY());
                    next.setYaw(player.getLocation().getYaw());
                    next.setPitch(0.0F);
                    head.teleport(next);
                }
            }

            Location at = head.getLocation();
            if (at.getY() < FLOOR_Y - 7 || Math.abs(at.getX() - baseX()) > SNAKE_RADIUS
                    || Math.abs(at.getZ()) > SNAKE_RADIUS) {
                eliminate(player, "left the Snake arena");
                continue;
            }

            Location last = history.get(history.size() - 1);
            if (at.distanceSquared(last) > 0.35D) {
                history.add(at.clone());
                if (history.size() > 320) history.remove(0);
            }

            int target = snakeTailTarget.containsKey(id) ? snakeTailTarget.get(id).intValue() : 5;
            while (tail.size() < target) {
                Location loc = tail.get(tail.size() - 1).getLocation().clone();
                Sheep sheep = (Sheep) world.spawnEntity(loc, EntityType.SHEEP);
                sheep.setRemoveWhenFarAway(false);
                tail.add(sheep);
            }

            for (int i = 1; i < tail.size(); i++) {
                Sheep sheep = tail.get(i);
                if (sheep == null || !sheep.isValid()) continue;
                int trailIndex = Math.max(0, history.size() - 1 - (i * 4));
                Location targetLoc = history.get(trailIndex).clone();
                targetLoc.setYaw(at.getYaw());
                targetLoc.setPitch(0.0F);
                sheep.teleport(targetLoc);
            }

            // Super Snake and Reverse suppress tail-collision elimination during their active window.
            if (isSnakeInvulnerable(id) || isSnakeReversing(id)) continue;

            for (UUID otherId : participants) {
                List<Sheep> otherTail = snakeTails.get(otherId);
                if (otherTail == null) continue;
                int firstSegment = otherId.equals(id) ? 3 : 0;
                for (int i = firstSegment; i < otherTail.size(); i++) {
                    Sheep segment = otherTail.get(i);
                    if (segment != null && segment.isValid()
                            && head.getLocation().distanceSquared(segment.getLocation()) < 1.25D) {
                        world.playEffect(segment.getLocation(), Effect.EXPLOSION_HUGE, 0);
                        eliminate(player, "hit a snake tail");
                        break;
                    }
                }
                if (!alive.contains(id)) break;
            }
        }

        if (ticks % 20L == 0L) {
            for (Slime slime : new ArrayList<Slime>(snakeFood)) {
                if (slime == null || !slime.isValid() || slime.isDead()) {
                    snakeFood.remove(slime);
                    continue;
                }
                for (UUID id : new ArrayList<UUID>(alive)) {
                    Player player = Bukkit.getPlayer(id);
                    List<Sheep> tail = snakeTails.get(id);
                    Location headLocation = tail == null || tail.isEmpty() || !tail.get(0).isValid()
                            ? (player == null ? null : player.getLocation()) : tail.get(0).getLocation();
                    if (player == null || headLocation == null || headLocation.distanceSquared(slime.getLocation()) > 3.0D) continue;
                    Integer target = snakeTailTarget.get(id);
                    snakeTailTarget.put(id, Integer.valueOf((target == null ? 5 : target.intValue()) + 2));
                    slime.remove();
                    snakeFood.remove(slime);
                    player.playSound(player.getLocation(), org.bukkit.Sound.EAT, 1.0F, 1.0F);
                    player.sendMessage(ChatColor.GREEN + "Slime eaten: your Snake tail has grown.");
                    break;
                }
            }
        }
    }

    private void updateSnakeTailColours(UUID id, List<Sheep> tail) {
        String ability = snakeAbility.get(id);
        boolean active = ability != null && snakeAbilityUntil.containsKey(id) && ticks < snakeAbilityUntil.get(id).longValue();
        if (!active && ability != null) {
            snakeAbility.remove(id);
            snakeAbilityUntil.remove(id);
            Player player = Bukkit.getPlayer(id);
            if (player != null) player.removePotionEffect(PotionEffectType.SPEED);
            ability = null;
        }
        DyeColor colour = null;
        if (active) colour = cosmetics.getSnakeAbilityColour(id, ability);
        if (active && "super".equals(ability) && colour == null) {
            // Preserve the original rainbow Super Snake effect for players without a Snake line.
            if (ticks % 4L == 0L) {
                DyeColor[] classic = new DyeColor[] {DyeColor.RED, DyeColor.YELLOW, DyeColor.GREEN, DyeColor.BLUE};
                colour = classic[random.nextInt(classic.length)];
            } else return;
        }
        if (colour == null) colour = snakeBaseColour.get(id);
        if (colour == null) colour = DyeColor.RED;
        for (Sheep sheep : tail) {
            if (sheep != null && sheep.isValid()) sheep.setColor(colour);
        }
    }

    private void spawnSnakeFood() {
        if (mode != Mode.SNAKE || world == null) return;
        Location loc = new Location(world, baseX() + random.nextInt(25) - 12, FLOOR_Y, random.nextInt(25) - 12);
        Slime slime = (Slime) world.spawnEntity(loc, EntityType.SLIME);
        slime.setSize(1);
        slime.setRemoveWhenFarAway(false);
        snakeFood.add(slime);
    }

    private boolean isSnakeInvulnerable(UUID id) {
        return isSnakeAbilityActive(id, "super");
    }

    private boolean isSnakeReversing(UUID id) {
        return isSnakeAbilityActive(id, "reverse");
    }

    private boolean isSnakeAbilityActive(UUID id, String key) {
        String current = snakeAbility.get(id);
        Long until = snakeAbilityUntil.get(id);
        return key.equals(current) && until != null && ticks < until.longValue();
    }

    private void activateSnakeAbility(Player player, String ability) {
        UUID id = player.getUniqueId();
        snakeAbility.put(id, ability);
        snakeAbilityUntil.put(id, Long.valueOf(ticks + 40L));
        if ("speed".equals(ability)) player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 40, 1, false), true);
        player.getWorld().spigot().playEffect(player.getLocation().add(0.0D, 1.0D, 0.0D),
                "super".equals(ability) ? Effect.PORTAL : Effect.POTION_BREAK, 0, 0, 0.35F, 0.5F, 0.35F, 0.05F, 18, 48);
        player.sendMessage(ChatColor.GREEN + "Snake ability activated: " + ability + ". The tail colour updates for every player viewing you.");
    }

    private void finishPlayer(Player player, String reason) {
        UUID id = player.getUniqueId();
        if (!alive.remove(id)) return;
        player.sendMessage(ChatColor.GOLD + "Finished: " + reason + ".");
        for (Player viewer : Bukkit.getOnlinePlayers()) {
            if (viewer.getWorld() == world && !viewer.equals(player)) {
                viewer.sendMessage(ChatColor.YELLOW + player.getName() + " " + reason + ".");
            }
        }
        if (participants.size() > 1 && alive.size() <= 1 && !endScheduled) scheduleEnd("Test round complete.");
    }

    private void eliminate(Player player, String reason) {
        UUID id = player.getUniqueId();
        if (!alive.remove(id)) return;
        eliminated.add(id);
        player.setGameMode(GameMode.SPECTATOR);
        player.teleport(new Location(world, baseX() + 0.5D, FLOOR_Y + 24.0D, laneZ.containsKey(id) ? laneZ.get(id).intValue() : 0));
        player.sendMessage(ChatColor.RED + "Eliminated: " + reason + ".");
        if (participants.size() > 1 && alive.size() <= 1 && !endScheduled) scheduleEnd("Test round complete.");
    }

    private void scheduleEnd(final String message) {
        endScheduled = true;
        new BukkitRunnable() {
            @Override public void run() {
                if (mode != null) stopGame(true, message);
            }
        }.runTaskLater(cosmetics.getHostPlugin(), 100L);
    }

    private void evolve(Player player) {
        UUID id = player.getUniqueId();
        int kills = evolutionKills.containsKey(id) ? evolutionKills.get(id).intValue() + 1 : 1;
        evolutionKills.put(id, Integer.valueOf(kills));
        int level = Math.min(4, 1 + kills);
        Integer previous = evolutionLevel.get(id);
        if (previous == null || level > previous.intValue()) {
            evolutionLevel.put(id, Integer.valueOf(level));
            giveEvolutionKit(player, level);
            cosmetics.onPlayerEvolved(id, player.getLocation().clone().add(0.0D, 1.0D, 0.0D));
            player.sendMessage(ChatColor.LIGHT_PURPLE + "You evolved to level " + level + "!");
        }
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        if (mode != Mode.SNAKE || !participants.contains(player.getUniqueId()) || !alive.contains(player.getUniqueId())) return;
        Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) return;
        ItemStack item = event.getItem();
        if (item == null) return;
        if (item.getType() == Material.REDSTONE) {
            activateSnakeAbility(player, "super");
            event.setCancelled(true);
        } else if (item.getType() == Material.FEATHER) {
            activateSnakeAbility(player, "speed");
            event.setCancelled(true);
        } else if (item.getType() == Material.COOKIE) {
            activateSnakeAbility(player, "reverse");
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onMove(PlayerMoveEvent event) {
        if (mode == null || event.getTo() == null) return;
        Player player = event.getPlayer();
        UUID id = player.getUniqueId();
        if (!participants.contains(id) || !alive.contains(id) || player.getWorld() != world) return;
        if (mode == Mode.RUNNER && event.getTo().getY() < FLOOR_Y - 6) eliminate(player, "fell off the Runner lane");
        else if (mode == Mode.DRAGONESCAPE) {
            if (event.getTo().getY() < FLOOR_Y - 7) eliminate(player, "fell from the Dragon Escape course");
            else if (event.getTo().getX() >= baseX() + 82) finishPlayer(player, "completed Dragon Escape");
        }
    }

    @EventHandler
    public void onDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player)) return;
        Player player = (Player) event.getEntity();
        if (player.getWorld() != world) return;
        UUID id = player.getUniqueId();
        if (mode == null || !participants.contains(id) || !alive.contains(id)) {
            event.setCancelled(true);
            return;
        }
        if (mode == Mode.SNAKE) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player)) return;
        Player victim = (Player) event.getEntity();
        if (victim.getWorld() != world || mode == null) return;
        if (mode == Mode.SNAKE || mode == Mode.RUNNER || mode == Mode.DRAGONESCAPE) {
            event.setCancelled(true);
            return;
        }
        if (mode != Mode.BACONBRAWL && mode != Mode.MICROBATTLES && mode != Mode.EVOLUTION) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        if (player.getWorld() != world || mode == null || !participants.contains(player.getUniqueId())) return;
        event.setKeepInventory(true);
        event.getDrops().clear();
        event.setDroppedExp(0);
        Player killer = player.getKiller();
        if (mode == Mode.BACONBRAWL) {
            if (killer != null && participants.contains(killer.getUniqueId())) {
                cosmetics.recordBaconBrawlKill(killer.getUniqueId());
                baconKills.put(killer.getUniqueId(), Integer.valueOf(
                        baconKills.containsKey(killer.getUniqueId()) ? baconKills.get(killer.getUniqueId()).intValue() + 1 : 1));
                killer.sendMessage(ChatColor.GOLD + "Kill credited. Your Bacon Brawl pig may now lead the round.");
            }
        } else if (mode == Mode.EVOLUTION && killer != null && participants.contains(killer.getUniqueId())) {
            evolve(killer);
        } else if (mode == Mode.MICROBATTLES || mode == Mode.SNAKE) {
            alive.remove(player.getUniqueId());
            eliminated.add(player.getUniqueId());
        }
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        UUID id = player.getUniqueId();
        if (player.getWorld() != world || mode == null || !participants.contains(id)) return;
        if (eliminated.contains(id) || !alive.contains(id)) {
            event.setRespawnLocation(new Location(world, baseX() + 0.5D, FLOOR_Y + 20.0D, 0.5D));
            Bukkit.getScheduler().runTaskLater(cosmetics.getHostPlugin(), new Runnable() {
                @Override public void run() {
                    if (player.isOnline() && player.getWorld() == world) player.setGameMode(GameMode.SPECTATOR);
                }
            }, 1L);
        } else {
            Location spawn = spawnLocations.get(id);
            event.setRespawnLocation(spawn == null ? new Location(world, 0.5D, HUB_Y + 1.0D, 0.5D) : spawn);
            Bukkit.getScheduler().runTaskLater(cosmetics.getHostPlugin(), new Runnable() {
                @Override public void run() {
                    if (!player.isOnline() || mode == null) return;
                    player.setGameMode(GameMode.SURVIVAL);
                    if (mode == Mode.BACONBRAWL) player.getInventory().addItem(new ItemStack(Material.STONE_SWORD, 1));
                    else if (mode == Mode.EVOLUTION) giveEvolutionKit(player, evolutionLevel.containsKey(id) ? evolutionLevel.get(id).intValue() : 1);
                    else if (mode == Mode.MICROBATTLES) player.getInventory().addItem(new ItemStack(Material.STONE_SWORD, 1), new ItemStack(Material.BOW, 1), new ItemStack(Material.ARROW, 16));
                }
            }, 1L);
        }
    }

    private boolean inMicroBattlesBuildArea(Location location) {
        if (mode != Mode.MICROBATTLES || microCenter == null || location == null
                || location.getWorld() != world) return false;
        return Math.abs(location.getBlockX() - microCenter.getX()) <= 20
                && Math.abs(location.getBlockZ() - microCenter.getZ()) <= 20
                && location.getBlockY() >= FLOOR_Y - 1
                && location.getBlockY() <= FLOOR_Y + 8;
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        if (event.getBlock().getWorld() != world) return;
        boolean participant = participants.contains(event.getPlayer().getUniqueId());
        boolean isCosmeticCentre = microCenter != null
                && event.getBlock().getX() == microCenter.getX()
                && event.getBlock().getY() == microCenter.getY()
                && event.getBlock().getZ() == microCenter.getZ();
        if (mode == Mode.MICROBATTLES && participant && inMicroBattlesBuildArea(event.getBlock().getLocation())
                && !isCosmeticCentre) return;
        event.setCancelled(true);
    }

    @EventHandler
    public void onBlockPlace(BlockPlaceEvent event) {
        if (event.getBlock().getWorld() != world) return;
        if (mode == Mode.MICROBATTLES && participants.contains(event.getPlayer().getUniqueId())
                && inMicroBattlesBuildArea(event.getBlockPlaced().getLocation())) {
            Block placed = event.getBlockPlaced();
            if (microCenter == null || placed.getX() != microCenter.getX()
                    || placed.getY() != microCenter.getY() || placed.getZ() != microCenter.getZ()) return;
        }
        event.setCancelled(true);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID id = event.getPlayer().getUniqueId();
        if (participants.contains(id)) {
            removePlayerEntities(id);
            participants.remove(id);
            alive.remove(id);
            eliminated.remove(id);
            if (participants.isEmpty() && mode != null) stopGame(false, "All players left the test round.");
        }
        joinedStates.remove(id);
        roundStates.remove(id);
    }

    private void removePlayerEntities(UUID id) {
        List<Sheep> tail = snakeTails.remove(id);
        if (tail != null) for (Sheep sheep : tail) if (sheep != null && sheep.isValid()) sheep.remove();
        List<Location> history = snakeHistory.remove(id);
        snakeTailTarget.remove(id);
        snakeBaseColour.remove(id);
        snakeAbility.remove(id);
        snakeAbilityUntil.remove(id);
        Pig pig = baconPigs.remove(id);
        if (pig != null && pig.isValid()) pig.remove();
        Player player = Bukkit.getPlayer(id);
        if (player != null) cosmetics.clearMicroBattlesArena(player);
    }

    private void stopGame(boolean notify, String message) {
        Mode previous = mode;
        if (previous == null) return;
        mode = null;
        for (UUID id : new ArrayList<UUID>(participants)) {
            Player player = Bukkit.getPlayer(id);
            removePlayerEntities(id);
            if (player != null && player.isOnline()) {
                cosmetics.clearMicroBattlesArena(player);
                player.removePotionEffect(PotionEffectType.SPEED);
                if (notify) player.sendMessage(ChatColor.YELLOW + message);
                SavedPlayer saved = roundStates.get(id);
                if (saved != null) saved.restore(player, false);
                player.teleport(new Location(ensureWorld(), 0.5D, HUB_Y + 1.0D, 0.5D));
                player.setFallDistance(0.0F);
            }
        }
        for (Block beacon : new ArrayList<Block>(dragonBeacons)) cosmetics.restoreDragonEscapeBeaconColour(beacon);
        dragonBeacons.clear();
        cosmetics.resetBaconBrawlRound();
        for (Slime slime : new ArrayList<Slime>(snakeFood)) {
            if (slime != null && slime.isValid()) slime.remove();
        }
        snakeFood.clear();
        participants.clear();
        alive.clear();
        eliminated.clear();
        roundStates.clear();
        spawnLocations.clear();
        laneZ.clear();
        runnerLastClearedX.clear();
        evolutionKills.clear();
        baconKills.clear();
        evolutionLevel.clear();
        snakeTails.clear();
        snakeHistory.clear();
        snakeTailTarget.clear();
        snakeBaseColour.clear();
        snakeAbility.clear();
        snakeAbilityUntil.clear();
        microCenter = null;
        microBorderPoints = Collections.emptyList();
        endScheduled = false;
    }

    private enum Mode {
        RUNNER("runner", "Runner", 128),
        BACONBRAWL("baconbrawl", "Bacon Brawl", 256),
        DRAGONESCAPE("dragonescape", "Dragon Escape", 384),
        MICROBATTLES("microbattles", "Micro Battles", 512),
        EVOLUTION("evolution", "Evolution", 640),
        SNAKE("snake", "Snake", 768);

        final String key;
        final String displayName;
        final int baseX;

        Mode(String key, String displayName, int baseX) {
            this.key = key;
            this.displayName = displayName;
            this.baseX = baseX;
        }

        static Mode parse(String value) {
            String normalized = value.toLowerCase(Locale.ROOT).replace("-", "").replace("_", "").replace(" ", "");
            for (Mode mode : values()) if (mode.key.replace("-", "").equals(normalized)) return mode;
            if ("micro".equals(normalized) || "microbattle".equals(normalized)) return MICROBATTLES;
            if ("bacon".equals(normalized)) return BACONBRAWL;
            if ("dragon".equals(normalized)) return DRAGONESCAPE;
            return null;
        }
    }

    private static final class SavedPlayer {
        final Location location;
        final ItemStack[] inventory;
        final ItemStack[] armor;
        final GameMode gameMode;
        final int food;
        final float exp;
        final int level;
        final double health;
        final List<PotionEffect> effects;

        SavedPlayer(Player player) {
            location = player.getLocation().clone();
            inventory = cloneItems(player.getInventory().getContents());
            armor = cloneItems(player.getInventory().getArmorContents());
            gameMode = player.getGameMode();
            food = player.getFoodLevel();
            exp = player.getExp();
            level = player.getLevel();
            health = player.getHealth();
            effects = new ArrayList<PotionEffect>(player.getActivePotionEffects());
        }

        void restore(Player player, boolean restoreLocation) {
            player.getInventory().clear();
            player.getInventory().setContents(cloneItems(inventory));
            player.getInventory().setArmorContents(cloneItems(armor));
            for (PotionEffect active : new ArrayList<PotionEffect>(player.getActivePotionEffects())) {
                player.removePotionEffect(active.getType());
            }
            for (PotionEffect effect : effects) player.addPotionEffect(effect, true);
            player.setGameMode(gameMode);
            player.setFoodLevel(food);
            player.setExp(exp);
            player.setLevel(level);
            player.setHealth(Math.min(Math.max(1.0D, health), player.getMaxHealth()));
            player.setFallDistance(0.0F);
            if (restoreLocation && location != null) player.teleport(location);
        }

        private static ItemStack[] cloneItems(ItemStack[] source) {
            if (source == null) return new ItemStack[0];
            ItemStack[] copy = new ItemStack[source.length];
            for (int i = 0; i < source.length; i++) copy[i] = source[i] == null ? null : source[i].clone();
            return copy;
        }
    }
}
