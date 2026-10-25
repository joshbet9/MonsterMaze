package me.monstermaze.snake;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Difficulty;
import org.bukkit.DyeColor;
import org.bukkit.Effect;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.WorldType;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.MagmaCube;
import org.bukkit.entity.Player;
import org.bukkit.entity.Sheep;
import org.bukkit.entity.Slime;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityCombustEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Vector;

import java.lang.reflect.Method;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Deque;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

public final class SnakeSoloPlugin extends JavaPlugin implements Listener, CommandExecutor {
    private static final String ARENA_WORLD = "snake_solo";
    private final Random random = new Random();
    private final List<RunRecord> records = new ArrayList<RunRecord>();
    private World arena;
    private Run run;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        loadRecords();
        getServer().getPluginManager().registerEvents(this, this);
        if (getCommand("snake") != null) getCommand("snake").setExecutor(this);
        getLogger().info("Snake Solo prototype loaded. The isolated arena is created when /snake start is used.");
    }

    @Override
    public void onDisable() {
        stopRun("Server is stopping", true, false);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0 || "help".equalsIgnoreCase(args[0])) {
            sendHelp(sender);
            return true;
        }
        String sub = args[0].toLowerCase();
        if ("start".equals(sub)) {
            if (!(sender instanceof Player)) {
                sender.sendMessage("Only a player can start a Snake run.");
                return true;
            }
            Player player = (Player) sender;
            if (run != null) {
                sender.sendMessage(run.player.getUniqueId().equals(player.getUniqueId())
                        ? ChatColor.YELLOW + "Your Snake run is already active."
                        : ChatColor.RED + "The solo Snake arena is already in use.");
                return true;
            }
            startRun(player);
            return true;
        }
        if ("stop".equals(sub)) {
            if (run == null) {
                sender.sendMessage(ChatColor.GRAY + "No Snake run is active.");
            } else if (sender instanceof Player && !((Player) sender).getUniqueId().equals(run.player.getUniqueId())
                    && !sender.hasPermission("snake.admin")) {
                sender.sendMessage(ChatColor.RED + "Only the active player or an operator can stop this run.");
            } else {
                stopRun("Run stopped manually", true, false);
            }
            return true;
        }
        if ("stats".equals(sub)) {
            showRecords(sender);
            return true;
        }
        if ("hazard".equals(sub)) {
            handleHazardCommand(sender, args);
            return true;
        }
        sendHelp(sender);
        return true;
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage(ChatColor.GOLD + "Snake Solo commands");
        sender.sendMessage(ChatColor.YELLOW + "/snake start" + ChatColor.GRAY + " - start a solo run");
        sender.sendMessage(ChatColor.YELLOW + "/snake stop" + ChatColor.GRAY + " - stop and restore your player state");
        sender.sendMessage(ChatColor.YELLOW + "/snake stats" + ChatColor.GRAY + " - show top runs");
        sender.sendMessage(ChatColor.YELLOW + "/snake hazard now|off|status" + ChatColor.GRAY + " - test the late-game hazard");
    }

    private void startRun(Player player) {
        ensureArena();
        if (arena == null) {
            player.sendMessage(ChatColor.RED + "Could not create the Snake arena. Check the server log.");
            return;
        }
        SavedPlayerState saved = new SavedPlayerState(player);
        player.closeInventory();
        player.leaveVehicle();
        Location spawn = new Location(arena, 0.5, 64.0, 0.5, -90.0f, 0.0f);
        player.teleport(spawn);
        player.setGameMode(GameMode.ADVENTURE);
        player.setAllowFlight(false);
        player.setFlying(false);
        player.setFoodLevel(20);
        player.setSaturation(20.0f);
        player.setHealth(player.getMaxHealth());
        player.getInventory().clear();
        player.getInventory().setArmorContents(new ItemStack[4]);

        Sheep head = arena.spawn(spawn, Sheep.class);
        head.setColor(DyeColor.LIME);
        head.setRemoveWhenFarAway(false);
        head.setCustomName(ChatColor.GREEN + "Snake");
        head.setCustomNameVisible(false);
        disableAi(head);

        Run next = new Run(player, saved, head);
        next.tail.add(head);
        next.trail.addFirst(head.getLocation().clone());
        next.appliedYaw = spawn.getYaw();
        next.lastViewYaw = spawn.getYaw();
        next.lastDirection = directionFromYaw(spawn.getYaw());
        run = next;
        head.setPassenger(player);
        givePowerups(player);
        player.setLevel(1);
        player.setExp(1.0f / 180.0f);
        player.sendMessage(ChatColor.GREEN + "" + ChatColor.BOLD + "Snake Solo started!");
        player.sendMessage(ChatColor.GRAY + "Turn with your view. F5 third-person is recommended. Green slimes grow your tail.");
        player.sendMessage(ChatColor.GRAY + "Magma cubes begin after " + getConfig().getInt("magma-start-after-seconds", 120)
                + " seconds. Use " + ChatColor.YELLOW + "/snake hazard now" + ChatColor.GRAY + " to test immediately.");
        next.task = Bukkit.getScheduler().runTaskTimer(this, new Runnable() {
            @Override public void run() { tickRun(); }
        }, 1L, 1L);
    }

    private void ensureArena() {
        String name = getConfig().getString("arena-world", ARENA_WORLD);
        arena = Bukkit.getWorld(name);
        if (arena == null) arena = new WorldCreator(name).type(WorldType.FLAT).generateStructures(false).createWorld();
        if (arena == null) return;
        arena.setTime(6000L);
        arena.setStorm(false);
        arena.setThundering(false);
        arena.setDifficulty(Difficulty.NORMAL);
        arena.setGameRuleValue("doMobSpawning", "false");
        if (!getConfig().getBoolean("arena-built", false)
                || arena.getBlockAt(0, 63, 0).getType() != Material.GRASS) {
            buildArena();
            getConfig().set("arena-built", true);
            saveConfig();
        }
    }

    private void buildArena() {
        int radius = getConfig().getInt("arena-radius", 48);
        for (int x = -radius; x <= radius; x++) {
            for (int z = -radius; z <= radius; z++) {
                arena.getBlockAt(x, 61, z).setType(Material.BEDROCK);
                arena.getBlockAt(x, 62, z).setType(Material.DIRT);
                arena.getBlockAt(x, 63, z).setType(Material.GRASS);
            }
        }
        for (int y = 64; y <= 68; y++) {
            for (int v = -radius; v <= radius; v++) {
                arena.getBlockAt(-radius, y, v).setType(Material.GLASS);
                arena.getBlockAt(radius, y, v).setType(Material.GLASS);
                arena.getBlockAt(v, y, -radius).setType(Material.GLASS);
                arena.getBlockAt(v, y, radius).setType(Material.GLASS);
            }
        }
        arena.setSpawnLocation(0, 64, 0);
        getLogger().info("Built isolated Snake arena in world '" + arena.getName() + "'.");
    }

    private void givePowerups(Player player) {
        PlayerInventory inv = player.getInventory();
        inv.setItem(0, namedItem(Material.FEATHER, 5, ChatColor.YELLOW + "Speed Boost", "Right-click for 2 seconds of speed."));
        inv.setItem(1, namedItem(Material.NETHER_STAR, 2, ChatColor.AQUA + "Invulnerability", "Right-click for 2 seconds of protection."));
        inv.setItem(2, namedItem(Material.COOKIE, 3, ChatColor.GREEN + "Reverse", "Right-click to reverse the snake."));
        inv.setItem(8, null);
        player.updateInventory();
    }

    private ItemStack namedItem(Material material, int amount, String name, String lore) {
        ItemStack item = new ItemStack(material, amount);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            List<String> lines = new ArrayList<String>();
            lines.add(ChatColor.GRAY + lore);
            meta.setLore(lines);
            item.setItemMeta(meta);
        }
        return item;
    }

    private void tickRun() {
        final Run active = run;
        if (active == null || active.finished) return;
        if (!active.player.isOnline()) {
            stopRun("Player disconnected", false, false);
            return;
        }
        if (active.head == null || !active.head.isValid()) {
            stopRun("The snake head was lost", true, true);
            return;
        }

        active.ticks++;
        if (active.player.getVehicle() != active.head) active.head.setPassenger(active.player);
        if (active.currentDebuff != null && active.ticks >= active.debuffEndsAt) {
            active.player.sendMessage(ChatColor.GREEN + "Your " + active.currentDebuff.label + " effect wore off.");
            active.currentDebuff = null;
        }

        moveSnake(active);
        active.trail.addFirst(active.head.getLocation().clone());
        int trailCap = Math.max(100, active.tail.size() * 14);
        while (active.trail.size() > trailCap) active.trail.removeLast();
        updateTail(active);

        if (active.ticks % 20L == 0L) {
            growTail(active, getConfig().getInt("growth-per-second", 1));
            active.player.setLevel(Math.min(100, active.tail.size()));
            active.player.setExp(Math.min(0.999f, active.tail.size() / 180.0f));
        }
        int foodSpawnInterval = Math.max(1, getConfig().getInt("green-slime-spawn-seconds", 1)) * 20;
        if (active.ticks % foodSpawnInterval == 0L) spawnFood(active);

        eatFood(active);
        int elapsedSeconds = active.ticks / 20;
        if (!active.hazardsEnabled && !active.hazardsDisabled
                && elapsedSeconds >= getConfig().getInt("magma-start-after-seconds", 120)) {
            active.hazardsEnabled = true;
            active.nextMagmaTick = active.ticks;
            active.player.sendMessage(ChatColor.GOLD + "" + ChatColor.BOLD + "The late-game magma-cube hazard has begun!");
        }
        if (active.ticks >= active.nextMagmaTick && active.hazardsEnabled) {
            spawnMagmaCube(active);
            active.nextMagmaTick = active.ticks + magmaIntervalTicks(active);
        }

        if (!active.invulnerable()) {
            if (collidesWithTail(active)) {
                stopRun("You hit your own tail", true, true);
                return;
            }
            checkMagmaCollision(active);
        }

        int radius = getConfig().getInt("arena-radius", 48);
        Location loc = active.head.getLocation();
        if (Math.abs(loc.getX()) >= radius - 1.5 || Math.abs(loc.getZ()) >= radius - 1.5 || loc.getY() < 63.0) {
            stopRun("You left the arena", true, true);
            return;
        }
        updateRoulette(active);
    }

    private void moveSnake(Run active) {
        if (active.ticks < active.reverseEndsAt) {
            active.head.setVelocity(new Vector(0, 0, 0));
            return;
        }
        float rawYaw = active.player.getLocation().getYaw();
        float inputYaw;
        if (active.currentDebuff == Debuff.TURN_LAG) {
            // Replay the camera yaw from several ticks ago to create real input latency.
            active.yawHistory.addLast(rawYaw);
            int lagTicks = Math.max(1, getConfig().getInt("turn-lag-delay-ticks", 6));
            if (active.yawHistory.size() > lagTicks) inputYaw = active.yawHistory.removeFirst();
            else inputYaw = active.lastViewYaw;
        } else {
            active.yawHistory.clear();
            inputYaw = rawYaw;
        }
        float inputTurn = wrapDegrees(inputYaw - active.lastViewYaw);
        active.lastViewYaw = inputYaw;
        float turnLimit = 30.0f;
        if (active.currentDebuff == Debuff.WEAKENED_TURN) turnLimit = 12.0f;
        float targetDelta = clamp(wrapDegrees(inputYaw - active.appliedYaw), -turnLimit, turnLimit);
        active.appliedYaw = wrapDegrees(active.appliedYaw + targetDelta);

        float effectiveYaw = active.appliedYaw;
        if (active.currentDebuff == Debuff.OVERSHOOT)
            effectiveYaw = wrapDegrees(effectiveYaw + clamp(inputTurn * 0.65f, -45.0f, 45.0f));
        Vector heading = directionFromYaw(effectiveYaw);
        if (active.currentDebuff == Debuff.DRIFT && active.lastDirection.lengthSquared() > 0.1)
            heading = active.lastDirection.clone().multiply(0.70).add(heading.multiply(0.30)).normalize();
        active.lastDirection = heading.clone();

        double speed = getConfig().getDouble("base-speed", 0.19)
                + Math.min(0.10, active.tail.size() * 0.0006);
        if (active.ticks < active.speedBoostEndsAt) speed *= 1.5;
        Vector velocity = active.head.getVelocity();
        active.head.setFallDistance(0.0f);
        active.head.setVelocity(new Vector(heading.getX() * speed, velocity.getY(), heading.getZ() * speed));
        for (Slime food : active.food) if (food.isValid()) food.setVelocity(new Vector(0, 0, 0));
        for (MagmaCube cube : active.hazards) if (cube.isValid()) cube.setVelocity(new Vector(0, 0, 0));
    }

    private void updateTail(Run active) {
        if (active.tail.size() <= 1) return;
        for (int i = 1; i < active.tail.size(); i++) {
            Location target = sampleTrail(active, i * 1.1);
            if (target == null) continue;
            target.setY(64.0);
            target.setYaw(active.head.getLocation().getYaw());
            target.setPitch(0.0f);
            Sheep segment = active.tail.get(i);
            if (segment.isValid()) {
                segment.teleport(target);
                segment.setVelocity(new Vector(0, 0, 0));
                segment.setFallDistance(0.0f);
            }
        }
    }

    private Location sampleTrail(Run active, double targetDistance) {
        Location previous = null;
        double travelled = 0.0;
        for (Location point : active.trail) {
            if (previous != null) {
                travelled += previous.distance(point);
                if (travelled >= targetDistance) return point.clone();
            }
            previous = point;
        }
        return active.trail.isEmpty() ? active.head.getLocation().clone() : active.trail.peekLast().clone();
    }

    private void growTail(Run active, int amount) {
        for (int i = 0; i < amount; i++) {
            Location loc = sampleTrail(active, (active.tail.size() + 1) * 1.1);
            if (loc == null) loc = active.head.getLocation().clone();
            loc.setY(64.0);
            Sheep segment = arena.spawn(loc, Sheep.class);
            segment.setColor(DyeColor.LIME);
            segment.setRemoveWhenFarAway(false);
            segment.setCustomNameVisible(false);
            disableAi(segment);
            active.tail.add(segment);
        }
        active.maxTailLength = Math.max(active.maxTailLength, active.tail.size());
    }

    private void spawnFood(Run active) {
        if (active.food.size() >= getConfig().getInt("max-food-slimes", 30)) return;
        int radius = getConfig().getInt("arena-radius", 48) - 6;
        for (int attempt = 0; attempt < 25; attempt++) {
            int x = random.nextInt(radius * 2 + 1) - radius;
            int z = random.nextInt(radius * 2 + 1) - radius;
            Location loc = new Location(arena, x + 0.5, 64.0, z + 0.5);
            if (loc.distanceSquared(active.head.getLocation()) < 36.0) continue;
            Slime food = arena.spawn(loc, Slime.class);
            food.setSize(2);
            food.setRemoveWhenFarAway(false);
            disableAi(food);
            active.food.add(food);
            return;
        }
    }

    private void eatFood(Run active) {
        Iterator<Slime> iterator = active.food.iterator();
        while (iterator.hasNext()) {
            Slime food = iterator.next();
            if (food == null || !food.isValid()) {
                if (food != null) food.remove();
                iterator.remove();
                continue;
            }
            if (active.head.getLocation().distanceSquared(food.getLocation()) < 6.25) {
                food.remove();
                iterator.remove();
                active.slimesEaten++;
                growTail(active, getConfig().getInt("green-slime-growth", 2));
                active.player.getWorld().playEffect(active.head.getLocation(), Effect.HAPPY_VILLAGER, 0);
                active.player.sendMessage(ChatColor.GREEN + "Slime eaten! Tail length: " + active.tail.size());
                break;
            }
        }
    }

    private void checkMagmaCollision(Run active) {
        Iterator<MagmaCube> iterator = active.hazards.iterator();
        while (iterator.hasNext()) {
            MagmaCube cube = iterator.next();
            if (cube == null || !cube.isValid()) {
                if (cube != null) cube.remove();
                iterator.remove();
                continue;
            }
            if (active.head.getLocation().distanceSquared(cube.getLocation()) < 5.0) {
                cube.remove();
                iterator.remove();
                beginRoulette(active);
                break;
            }
        }
    }

    private void spawnMagmaCube(Run active) {
        spawnMagmaCube(active, false);
    }

    private void spawnMagmaCube(Run active, boolean placeNearForTesting) {
        int radius = getConfig().getInt("arena-radius", 48) - 7;
        for (int attempt = 0; attempt < 35; attempt++) {
            Location loc;
            if (placeNearForTesting && attempt == 0) {
                Vector forward = active.lastDirection.lengthSquared() > 0.1
                        ? active.lastDirection.clone().normalize()
                        : directionFromYaw(active.appliedYaw);
                loc = active.head.getLocation().clone().add(forward.multiply(6.0));
                loc.setY(64.0);
                double bound = Math.max(2.0, radius - 2.0);
                loc.setX(Math.max(-bound, Math.min(bound, loc.getX())));
                loc.setZ(Math.max(-bound, Math.min(bound, loc.getZ())));
            } else {
                int x = random.nextInt(radius * 2 + 1) - radius;
                int z = random.nextInt(radius * 2 + 1) - radius;
                loc = new Location(arena, x + 0.5, 64.0, z + 0.5);
                if (!placeNearForTesting && loc.distanceSquared(active.head.getLocation()) < 64.0) continue;
            }
            boolean crowded = false;
            for (MagmaCube existing : active.hazards) {
                if (existing.isValid() && existing.getLocation().distanceSquared(loc) < 25.0) {
                    crowded = true;
                    break;
                }
            }
            if (crowded) continue;
            MagmaCube cube = arena.spawn(loc, MagmaCube.class);
            cube.setSize(getConfig().getInt("magma-cube-size", 2));
            cube.setRemoveWhenFarAway(false);
            cube.setCustomName(ChatColor.RED + "Magma Hazard");
            cube.setCustomNameVisible(false);
            disableAi(cube);
            active.hazards.add(cube);
            active.player.sendMessage(placeNearForTesting
                    ? ChatColor.RED + "TEST CUBE spawned about 6 blocks ahead. Steer into it to test the roulette."
                    : ChatColor.RED + "Magma cube spawned! Avoid its head collision.");
            return;
        }
    }

    private int magmaIntervalTicks(Run active) {
        int elapsed = Math.max(0, active.ticks / 20 - getConfig().getInt("magma-start-after-seconds", 120));
        int step = Math.max(1, getConfig().getInt("magma-frequency-step-seconds", 30));
        int drop = Math.max(0, elapsed / step) * getConfig().getInt("magma-interval-decrease-seconds", 2);
        int interval = Math.max(getConfig().getInt("magma-min-interval-seconds", 5),
                getConfig().getInt("magma-first-interval-seconds", 15) - drop);
        return interval * 20;
    }

    private void beginRoulette(Run active) {
        if (active.roulette) return;
        active.roulette = true;
        active.rouletteEndsAt = active.ticks + Math.max(20, getConfig().getInt("debuff-roulette-ticks", 40));
        Debuff[] debuffs = Debuff.values();
        active.pendingDebuff = debuffs[random.nextInt(debuffs.length)];
        active.player.sendMessage(ChatColor.GOLD + "" + ChatColor.BOLD + "MAGMA CUBE HIT! Roulette is choosing a control debuff...");
    }

    private void updateRoulette(Run active) {
        if (!active.roulette) return;
        if (active.ticks % 3L == 0L) {
            Debuff[] values = Debuff.values();
            active.player.getInventory().setItem(8, rouletteItem(values[(int) ((active.ticks / 3L) % values.length)]));
        }
        if (active.ticks < active.rouletteEndsAt) return;
        active.roulette = false;
        active.player.getInventory().setItem(8, null);
        active.currentDebuff = active.pendingDebuff;
        int base = getConfig().getInt("debuff-base-seconds", 4);
        int increment = getConfig().getInt("debuff-hit-increment-seconds", 2);
        int cap = getConfig().getInt("debuff-max-seconds", 12);
        int durationSeconds = Math.min(cap, base + active.lockedDebuffs * increment);
        active.lockedDebuffs++;
        active.debuffEndsAt = active.ticks + durationSeconds * 20L;
        active.player.sendMessage(ChatColor.RED + "" + ChatColor.BOLD + "Debuff locked: " + active.currentDebuff.label
                + ChatColor.YELLOW + " for " + durationSeconds + " seconds.");
        active.player.updateInventory();
    }

    private ItemStack rouletteItem(Debuff debuff) {
        Material icon;
        switch (debuff) {
            case TURN_LAG: icon = Material.WATCH; break;
            case DRIFT: icon = Material.FEATHER; break;
            case OVERSHOOT: icon = Material.BLAZE_POWDER; break;
            default: icon = Material.SLIME_BALL; break;
        }
        return namedItem(icon, 1, ChatColor.GOLD + "" + ChatColor.BOLD + "Roulette: " + debuff.label,
                "Temporary control debuff");
    }

    private boolean collidesWithTail(Run active) {
        Location headLoc = active.head.getLocation();
        for (int i = 3; i < active.tail.size(); i++) {
            Sheep segment = active.tail.get(i);
            if (segment != null && segment.isValid() && headLoc.distanceSquared(segment.getLocation()) < 1.44) return true;
        }
        return false;
    }

    private void handleHazardCommand(CommandSender sender, String[] args) {
        if (run == null) {
            sender.sendMessage(ChatColor.GRAY + "Start a run with /snake start first.");
            return;
        }
        String action = args.length > 1 ? args[1].toLowerCase() : "status";
        if ("now".equals(action)) {
            run.hazardsDisabled = false;
            run.hazardsEnabled = true;
            if (run.hazards.isEmpty()) spawnMagmaCube(run, true);
            run.nextMagmaTick = run.ticks + magmaIntervalTicks(run);
            sender.sendMessage(ChatColor.GOLD + "Hazard test enabled. First cube placed ahead for quick collision testing.");
        } else if ("off".equals(action)) {
            run.hazardsDisabled = true;
            run.hazardsEnabled = false;
            for (MagmaCube cube : new ArrayList<MagmaCube>(run.hazards)) if (cube != null) cube.remove();
            run.hazards.clear();
            sender.sendMessage(ChatColor.GRAY + "Magma-cube hazard disabled for this run.");
        } else if ("status".equals(action)) {
            String state = run.hazardsDisabled ? "disabled" : run.hazardsEnabled ? "enabled" : "waiting";
            sender.sendMessage(ChatColor.GRAY + "Magma hazard: " + state + " (elapsed " + (run.ticks / 20) + "s).");
        } else {
            sender.sendMessage(ChatColor.YELLOW + "Usage: /snake hazard <now|off|status>");
        }
    }

    private void usePowerup(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        if (run == null || !run.player.getUniqueId().equals(player.getUniqueId())) return;
        Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) return;
        if (player.getInventory().getHeldItemSlot() == 8 && run.roulette) {
            event.setCancelled(true);
            return;
        }
        ItemStack held = player.getItemInHand();
        if (held == null) return;
        if (held.getType() == Material.FEATHER) {
            event.setCancelled(true);
            run.speedBoostEndsAt = run.ticks + 40L;
            consumeHeldItem(player);
            player.sendMessage(ChatColor.YELLOW + "Speed boost for 2 seconds!");
        } else if (held.getType() == Material.NETHER_STAR) {
            event.setCancelled(true);
            run.invulnerableEndsAt = run.ticks + 40L;
            consumeHeldItem(player);
            player.sendMessage(ChatColor.AQUA + "Invulnerable for 2 seconds!");
        } else if (held.getType() == Material.COOKIE) {
            event.setCancelled(true);
            reverseSnake(run);
            consumeHeldItem(player);
            player.sendMessage(ChatColor.GREEN + "Snake reversed. Movement resumes in 2 seconds.");
        } else if (arena != null && player.getWorld().equals(arena) && event.getClickedBlock() != null) {
            event.setCancelled(true);
        }
    }

    private void reverseSnake(Run active) {
        active.player.leaveVehicle();
        active.head.eject();
        Collections.reverse(active.tail);
        active.head = active.tail.get(0);
        active.head.setPassenger(active.player);
        active.reverseEndsAt = active.ticks + 40L;
        active.trail.clear();
        for (Sheep segment : active.tail) active.trail.addLast(segment.getLocation().clone());
        active.appliedYaw = active.player.getLocation().getYaw();
        active.lastViewYaw = active.appliedYaw;
        active.lastDirection = directionFromYaw(active.appliedYaw);
    }

    private void consumeHeldItem(Player player) {
        ItemStack held = player.getItemInHand();
        if (held == null) return;
        if (held.getAmount() <= 1) player.setItemInHand(null);
        else held.setAmount(held.getAmount() - 1);
        player.updateInventory();
    }

    private Vector directionFromYaw(float yaw) {
        double radians = Math.toRadians(yaw);
        return new Vector(-Math.sin(radians), 0.0, Math.cos(radians)).normalize();
    }

    private float wrapDegrees(float angle) {
        angle %= 360.0f;
        if (angle >= 180.0f) angle -= 360.0f;
        if (angle < -180.0f) angle += 360.0f;
        return angle;
    }

    private float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    private void disableAi(Entity entity) {
        try {
            Object handle = entity.getClass().getMethod("getHandle").invoke(entity);
            Class<?> type = handle.getClass();
            while (type != null) {
                try {
                    Method setter = type.getDeclaredMethod("k", boolean.class);
                    setter.setAccessible(true);
                    setter.invoke(handle, Boolean.TRUE);
                    return;
                } catch (NoSuchMethodException ignored) {
                    type = type.getSuperclass();
                }
            }
        } catch (Throwable failure) {
            getLogger().fine("Could not disable vanilla AI for " + entity.getType() + ": " + failure.getClass().getSimpleName());
        }
    }

    private void stopRun(String reason, boolean restorePlayer, boolean recordScore) {
        Run active = run;
        if (active == null || active.finished) return;
        active.finished = true;
        if (active.task != null) active.task.cancel();
        if (recordScore && active.player.isOnline()) {
            int seconds = active.ticks / 20;
            long score = calculateScore(seconds, active.slimesEaten, active.maxTailLength);
            addRecord(new RunRecord(active.player.getName(), seconds, active.slimesEaten, active.maxTailLength, score));
            active.player.sendMessage(ChatColor.GOLD + "" + ChatColor.BOLD + "Snake ended: " + reason);
            active.player.sendMessage(ChatColor.YELLOW + "Survival " + seconds + "s | slimes " + active.slimesEaten
                    + " | max tail " + active.maxTailLength + " | score " + score);
            active.player.sendMessage(ChatColor.GRAY + "Use /snake start to run again or /snake stats for your top-ten table.");
        } else if (active.player.isOnline() && restorePlayer) {
            active.player.sendMessage(ChatColor.GRAY + reason + ".");
        }
        run = null;
        cleanupEntities(active);
        if (restorePlayer && active.player.isOnline()) restorePlayerState(active.player, active.saved);
    }

    private void cleanupEntities(Run active) {
        if (active.player != null && active.player.isOnline()) active.player.leaveVehicle();
        for (Sheep segment : new ArrayList<Sheep>(active.tail)) if (segment != null && segment.isValid()) segment.remove();
        for (Slime food : new ArrayList<Slime>(active.food)) if (food != null && food.isValid()) food.remove();
        for (MagmaCube cube : new ArrayList<MagmaCube>(active.hazards)) if (cube != null && cube.isValid()) cube.remove();
        active.tail.clear();
        active.food.clear();
        active.hazards.clear();
    }

    private void restorePlayerState(Player player, SavedPlayerState saved) {
        try {
            player.leaveVehicle();
            for (PotionEffect effect : new ArrayList<PotionEffect>(player.getActivePotionEffects())) {
                player.removePotionEffect(effect.getType());
            }
            player.setGameMode(saved.gameMode);
            player.getInventory().clear();
            player.getInventory().setContents(copyItems(saved.contents));
            player.getInventory().setArmorContents(copyItems(saved.armor));
            player.setLevel(saved.level);
            player.setExp(saved.exp);
            player.setFoodLevel(saved.foodLevel);
            player.setSaturation(saved.saturation);
            player.setMaxHealth(saved.maxHealth);
            player.setHealth(Math.max(1.0, Math.min(saved.health, player.getMaxHealth())));
            for (PotionEffect effect : saved.potions) player.addPotionEffect(effect, true);
            player.setAllowFlight(saved.allowFlight);
            if (saved.allowFlight) player.setFlying(saved.flying);
            player.teleport(saved.location);
            player.updateInventory();
        } catch (Throwable failure) {
            getLogger().warning("Could not fully restore Snake player state: " + failure.getClass().getSimpleName());
        }
    }

    private ItemStack[] copyItems(ItemStack[] items) {
        ItemStack[] copy = items == null ? new ItemStack[0] : items.clone();
        for (int i = 0; i < copy.length; i++) if (copy[i] != null) copy[i] = copy[i].clone();
        return copy;
    }

    private long calculateScore(int seconds, int slimes, int maxTail) {
        return seconds * 100L + slimes * 500L + maxTail * 10L;
    }

    private void loadRecords() {
        records.clear();
        for (Map<?, ?> values : getConfig().getMapList("records")) {
            Object name = values.get("name");
            if (name == null) continue;
            records.add(new RunRecord(String.valueOf(name), number(values.get("seconds"), 0),
                    number(values.get("slimes"), 0), number(values.get("maxTail"), 1),
                    longNumber(values.get("score"), 0L)));
        }
        sortRecords();
    }

    private int number(Object value, int fallback) {
        return value instanceof Number ? ((Number) value).intValue() : fallback;
    }

    private long longNumber(Object value, long fallback) {
        return value instanceof Number ? ((Number) value).longValue() : fallback;
    }

    private void addRecord(RunRecord record) {
        records.add(record);
        sortRecords();
        while (records.size() > 10) records.remove(records.size() - 1);
        List<Map<String, Object>> stored = new ArrayList<Map<String, Object>>();
        for (RunRecord value : records) {
            Map<String, Object> row = new LinkedHashMap<String, Object>();
            row.put("name", value.name);
            row.put("seconds", value.seconds);
            row.put("slimes", value.slimes);
            row.put("maxTail", value.maxTail);
            row.put("score", value.score);
            stored.add(row);
        }
        getConfig().set("records", stored);
        saveConfig();
    }

    private void sortRecords() {
        Collections.sort(records, new Comparator<RunRecord>() {
            @Override public int compare(RunRecord left, RunRecord right) {
                int score = Long.compare(right.score, left.score);
                return score != 0 ? score : Integer.compare(right.seconds, left.seconds);
            }
        });
    }

    private void showRecords(CommandSender sender) {
        sender.sendMessage(ChatColor.GOLD + "" + ChatColor.BOLD + "Snake Solo — Top Runs");
        if (records.isEmpty()) {
            sender.sendMessage(ChatColor.GRAY + "No completed runs have been recorded yet.");
            return;
        }
        int rank = 1;
        for (RunRecord record : records) {
            sender.sendMessage(ChatColor.YELLOW + "" + rank++ + ". " + record.name + ChatColor.GRAY
                    + " | " + record.seconds + "s | slimes " + record.slimes + " | max tail "
                    + record.maxTail + ChatColor.GOLD + " | " + record.score + " pts");
        }
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) { usePowerup(event); }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        if (run != null && run.player.getUniqueId().equals(event.getPlayer().getUniqueId())) {
            // PlayerQuitEvent fires before the connection is fully removed. Restore the
            // pre-run state now so the server never saves the intentionally cleared Snake inventory.
            stopRun("Player disconnected", true, false);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void cancelArenaDamage(EntityDamageEvent event) {
        if (arena != null && event.getEntity().getWorld().equals(arena)) event.setCancelled(true);
    }

    @EventHandler
    public void cancelArenaFire(EntityCombustEvent event) {
        if (arena != null && event.getEntity().getWorld().equals(arena)) event.setCancelled(true);
    }

    @EventHandler
    public void preventBuild(BlockBreakEvent event) {
        if (arena != null && event.getBlock().getWorld().equals(arena)) event.setCancelled(true);
    }

    @EventHandler
    public void preventPlace(BlockPlaceEvent event) {
        if (arena != null && event.getBlock().getWorld().equals(arena)) event.setCancelled(true);
    }

    private static final class Run {
        final Player player;
        final SavedPlayerState saved;
        final List<Sheep> tail = new ArrayList<Sheep>();
        final List<Slime> food = new ArrayList<Slime>();
        final List<MagmaCube> hazards = new ArrayList<MagmaCube>();
        final Deque<Location> trail = new ArrayDeque<Location>();
        Sheep head;
        BukkitTask task;
        int ticks;
        int slimesEaten;
        int maxTailLength = 1;
        int lockedDebuffs;
        long speedBoostEndsAt = -1L;
        long invulnerableEndsAt = -1L;
        long reverseEndsAt = -1L;
        long nextMagmaTick = -1L;
        long rouletteEndsAt = -1L;
        long debuffEndsAt = -1L;
        float appliedYaw;
        float lastViewYaw;
        Vector lastDirection = new Vector(0, 0, 1);
        final Deque<Float> yawHistory = new ArrayDeque<Float>();
        boolean hazardsEnabled;
        boolean hazardsDisabled;
        boolean roulette;
        boolean finished;
        Debuff currentDebuff;
        Debuff pendingDebuff;

        Run(Player player, SavedPlayerState saved, Sheep head) {
            this.player = player;
            this.saved = saved;
            this.head = head;
        }

        boolean invulnerable() { return ticks < invulnerableEndsAt; }
    }

    private enum Debuff {
        TURN_LAG("Turn Lag"),
        DRIFT("Drift"),
        OVERSHOOT("Overshoot"),
        WEAKENED_TURN("Weakened Sharp Turning");

        final String label;
        Debuff(String label) { this.label = label; }
    }

    private static final class SavedPlayerState {
        final Location location;
        final GameMode gameMode;
        final ItemStack[] contents;
        final ItemStack[] armor;
        final int level;
        final float exp;
        final int foodLevel;
        final float saturation;
        final double health;
        final double maxHealth;
        final boolean allowFlight;
        final boolean flying;
        final List<PotionEffect> potions;

        SavedPlayerState(Player player) {
            location = player.getLocation().clone();
            gameMode = player.getGameMode();
            contents = cloneItems(player.getInventory().getContents());
            armor = cloneItems(player.getInventory().getArmorContents());
            level = player.getLevel();
            exp = player.getExp();
            foodLevel = player.getFoodLevel();
            saturation = player.getSaturation();
            health = player.getHealth();
            maxHealth = player.getMaxHealth();
            allowFlight = player.getAllowFlight();
            flying = player.isFlying();
            potions = new ArrayList<PotionEffect>(player.getActivePotionEffects());
        }

        private static ItemStack[] cloneItems(ItemStack[] items) {
            ItemStack[] copy = items == null ? new ItemStack[0] : items.clone();
            for (int i = 0; i < copy.length; i++) if (copy[i] != null) copy[i] = copy[i].clone();
            return copy;
        }
    }

    private static final class RunRecord {
        final String name;
        final int seconds;
        final int slimes;
        final int maxTail;
        final long score;

        RunRecord(String name, int seconds, int slimes, int maxTail, long score) {
            this.name = name;
            this.seconds = seconds;
            this.slimes = slimes;
            this.maxTail = maxTail;
            this.score = score;
        }
    }
}
