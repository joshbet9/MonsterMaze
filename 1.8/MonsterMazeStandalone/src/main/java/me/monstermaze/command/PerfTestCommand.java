package me.monstermaze.command;

import me.monstermaze.MonsterMazePlugin;
import me.monstermaze.game.GameManager;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Chunk;
import org.bukkit.GameMode;
import org.bukkit.World;
import org.bukkit.block.BlockState;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.HandlerList;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryPoolMXBean;
import java.lang.management.ThreadMXBean;
import java.lang.reflect.Method;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Temporary, cumulative performance-isolation harness for the 1.8 implementation.
 *
 * The harness is deliberately destructive. It is intended for a disposable diagnostic
 * server and should be reset by restarting the server between full test runs.
 *
 * The important distinction is that the monitor itself is kept alive while workload is
 * removed. This lets us answer: "does the server still tick slowly when this entire class
 * of work is gone?" rather than guessing which gameplay system is responsible.
 */
public final class PerfTestCommand implements CommandExecutor {
    private final MonsterMazePlugin plugin;
    private final GameManager game;

    private BukkitTask heartbeatTask;
    private BukkitTask samplerTask;
    private long heartbeatCount;
    private long lastHeartbeatNs;
    private long liveStartHeartbeat = -1L;
    private long liveStartMs = -1L;
    private long maxGapNs;
    private long slowTicks;
    private final long[] recentGapsNs = new long[2000];
    private int recentGapCount;
    private int recentGapIndex;
    private String lastStack = "";
    private long stackSinceNs;
    private long lastStackReportNs;
    private final SimpleDateFormat clock = new SimpleDateFormat("HH:mm:ss.SSS", Locale.US);

    public PerfTestCommand(MonsterMazePlugin plugin, GameManager game) {
        this.plugin = plugin;
        this.game = game;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("monstermaze.admin")) {
            sender.sendMessage(ChatColor.RED + "No permission.");
            return true;
        }

        if (args.length == 0 || "help".equalsIgnoreCase(args[0])) {
            help(sender);
            return true;
        }

        String op = args[0].toLowerCase(Locale.US);
        if ("start".equals(op)) startMonitor(sender);
        else if ("stop".equals(op)) stopMonitor(sender);
        else if ("status".equals(op)) status(sender);
        else if ("count".equals(op) || "counts".equals(op)) counts(sender);
        else if ("jvm".equals(op) || "system".equals(op)) jvm(sender);
        else if ("tasks".equals(op)) taskReport(sender);
        else if ("events".equals(op)) unregisterPluginEvents(sender);
        else if ("foreign".equals(op) || "otherplugins".equals(op)) isolateForeignPlugins(sender);
        else if ("living".equals(op)) removeLiving(sender);
        else if ("entities".equals(op)) removeEntities(sender);
        else if ("projectiles".equals(op)) removeProjectiles(sender);
        else if ("items".equals(op)) removeItems(sender);
        else if ("npcs".equals(op)) removeNonPlayerLiving(sender);
        else if ("monsterlogic".equals(op)) cancelTasks(sender, "me.monstermaze.entity.MonsterManager$");
        else if ("kitlogic".equals(op)) cancelTasks(sender, "me.monstermaze.kit.KitManager$");
        else if ("gametasks".equals(op)) cancelTasks(sender, "me.monstermaze.game.GameManager$");
        else if ("inventory".equals(op) || "nbt".equals(op)) clearPlayerInventories(sender);
        else if ("player".equals(op)) minimizePlayer(sender);
        else if ("world".equals(op) || "worlds".equals(op)) suppressWorldWork(sender);
        else if ("chunks".equals(op)) unloadUnusedChunks(sender);
        else if ("extraworlds".equals(op) || "trimworlds".equals(op)) unloadExtraWorlds(sender);
        else if ("tiles".equals(op) || "tileentities".equals(op)) reportTileEntities(sender);
        else if ("purgetiles".equals(op)) purgeTileEntities(sender);
        else if ("physics".equals(op)) cancelPhysicsEvents(sender);
        else if ("alltasks".equals(op)) cancelAllPluginTasksExceptThisMonitor(sender);
        else if ("allplugins".equals(op)) isolateEverythingPluginOwned(sender);
        else if ("nuclear".equals(op) || "all".equals(op)) nuclear(sender);
        else sender.sendMessage(ChatColor.RED + "Unknown /perftest option. Try /perftest help");
        return true;
    }

    private void help(CommandSender s) {
        s.sendMessage(ChatColor.GOLD + "=== Monster Maze PERF ISOLATION v2 ===");
        s.sendMessage(ChatColor.YELLOW + "/perftest start" + ChatColor.GRAY + " - continuous tick/TPS + stack monitor");
        s.sendMessage(ChatColor.YELLOW + "/perftest status" + ChatColor.GRAY + " - tick stats + entity counts");
        s.sendMessage(ChatColor.YELLOW + "/perftest count" + ChatColor.GRAY + " - entity/chunk/tile counts by world");
        s.sendMessage(ChatColor.YELLOW + "/perftest jvm" + ChatColor.GRAY + " - heap/GC/thread/CPU diagnostics");
        s.sendMessage(ChatColor.YELLOW + "/perftest tasks" + ChatColor.GRAY + " - list pending scheduler work by plugin/class");
        s.sendMessage(ChatColor.AQUA + "--- workload isolation ---");
        s.sendMessage(ChatColor.YELLOW + "/perftest monsterlogic" + ChatColor.GRAY + " - cancel MonsterManager tasks");
        s.sendMessage(ChatColor.YELLOW + "/perftest kitlogic" + ChatColor.GRAY + " - cancel KitManager tasks");
        s.sendMessage(ChatColor.YELLOW + "/perftest gametasks" + ChatColor.GRAY + " - cancel GameManager tasks");
        s.sendMessage(ChatColor.YELLOW + "/perftest events" + ChatColor.GRAY + " - unregister all MonsterMaze event listeners");
        s.sendMessage(ChatColor.YELLOW + "/perftest foreign" + ChatColor.GRAY + " - strip tasks/listeners from every other plugin");
        s.sendMessage(ChatColor.YELLOW + "/perftest living" + ChatColor.GRAY + " - remove all non-player living entities");
        s.sendMessage(ChatColor.YELLOW + "/perftest entities" + ChatColor.GRAY + " - remove all non-player entities");
        s.sendMessage(ChatColor.YELLOW + "/perftest projectiles" + ChatColor.GRAY + " - remove projectiles");
        s.sendMessage(ChatColor.YELLOW + "/perftest items" + ChatColor.GRAY + " - remove dropped items");
        s.sendMessage(ChatColor.YELLOW + "/perftest inventory" + ChatColor.GRAY + " - clear all player inventory/armor");
        s.sendMessage(ChatColor.YELLOW + "/perftest player" + ChatColor.GRAY + " - minimize player mechanics + spectator mode");
        s.sendMessage(ChatColor.YELLOW + "/perftest world" + ChatColor.GRAY + " - disable natural spawns/random ticks/weather/autosave");
        s.sendMessage(ChatColor.YELLOW + "/perftest chunks" + ChatColor.GRAY + " - unload loaded chunks not in use by players");
        s.sendMessage(ChatColor.YELLOW + "/perftest extraworlds" + ChatColor.GRAY + " - unload every world except the player's current world");
        s.sendMessage(ChatColor.YELLOW + "/perftest tiles" + ChatColor.GRAY + " - report loaded tile entities");
        s.sendMessage(ChatColor.YELLOW + "/perftest purgetiles" + ChatColor.GRAY + " - remove tile-entity blocks from loaded chunks");
        s.sendMessage(ChatColor.YELLOW + "/perftest physics" + ChatColor.GRAY + " - cancel plugin-visible block physics/redstone/fluid events");
        s.sendMessage(ChatColor.AQUA + "--- final isolation ---");
        s.sendMessage(ChatColor.YELLOW + "/perftest alltasks" + ChatColor.GRAY + " - cancel every MonsterMaze task except monitor");
        s.sendMessage(ChatColor.YELLOW + "/perftest allplugins" + ChatColor.GRAY + " - strip all plugin tasks/listeners except monitor plugin");
        s.sendMessage(ChatColor.YELLOW + "/perftest nuclear" + ChatColor.GRAY + " - apply every practical isolation switch");
        s.sendMessage(ChatColor.RED + "All switches are cumulative/destructive. Restart the server to reset.");
    }

    private void startMonitor(CommandSender s) {
        if (heartbeatTask != null) {
            s.sendMessage(ChatColor.YELLOW + "Performance monitor is already running.");
            return;
        }
        heartbeatCount = 0L;
        lastHeartbeatNs = 0L;
        liveStartHeartbeat = -1L;
        liveStartMs = -1L;
        maxGapNs = 0L;
        slowTicks = 0L;
        recentGapCount = 0;
        recentGapIndex = 0;
        lastStack = "";
        stackSinceNs = 0L;
        lastStackReportNs = 0L;

        heartbeatTask = Bukkit.getScheduler().runTaskTimer(plugin, new Runnable() {
            @Override public void run() { heartbeat(); }
        }, 1L, 1L);
        samplerTask = Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, new Runnable() {
            @Override public void run() { sampleServerThread(); }
        }, 1L, 1L);
        s.sendMessage(ChatColor.GREEN + "[PERFTEST] monitor started.");
    }

    private void stopMonitor(CommandSender s) {
        if (heartbeatTask != null) heartbeatTask.cancel();
        if (samplerTask != null) samplerTask.cancel();
        heartbeatTask = null;
        samplerTask = null;
        s.sendMessage(String.format(Locale.US,
                ChatColor.GREEN + "[PERFTEST] stopped: ticks=%d maxGap=%.1fms slowGaps=%d avgGap=%.2fms p95=%.2fms p99=%.2fms",
                heartbeatCount, maxGapNs / 1_000_000.0, slowTicks,
                percentileGap(50) / 1_000_000.0,
                percentileGap(95) / 1_000_000.0,
                percentileGap(99) / 1_000_000.0));
    }

    private void heartbeat() {
        long now = System.nanoTime();
        if (lastHeartbeatNs != 0L) {
            long gap = now - lastHeartbeatNs;
            if (gap > maxGapNs) maxGapNs = gap;
            if (recentGapCount < recentGapsNs.length) recentGapCount++;
            recentGapsNs[recentGapIndex] = gap;
            recentGapIndex = (recentGapIndex + 1) % recentGapsNs.length;
            if (gap > 75_000_000L) {
                slowTicks++;
                plugin.getLogger().warning(String.format(Locale.US,
                        "[PERFTEST][TICK-GAP] %.1fms state=%s slowGaps=%d",
                        gap / 1_000_000.0, game.getState(), slowTicks));
            }
        }
        lastHeartbeatNs = now;
        heartbeatCount++;

        if (game.isLive()) {
            if (liveStartHeartbeat < 0L) {
                liveStartHeartbeat = heartbeatCount;
                liveStartMs = System.currentTimeMillis();
                plugin.getLogger().info("[PERFTEST][LIVE-START] " + clock.format(new Date())
                        + " entities=" + countEntities() + " living=" + countLiving());
            }
            long ticks = heartbeatCount - liveStartHeartbeat;
            double elapsed = (System.currentTimeMillis() - liveStartMs) / 1000.0;
            if (elapsed > 0 && ticks % 20 == 0) {
                plugin.getLogger().info(String.format(Locale.US,
                        "[PERFTEST][TPS] elapsed=%.1fs serverTicks=%d effectiveTPS=%.2f avgGap=%.2fms p95=%.2fms p99=%.2fms entities=%d living=%d worlds=%d",
                        elapsed, ticks, ticks / elapsed,
                        percentileGap(50) / 1_000_000.0,
                        percentileGap(95) / 1_000_000.0,
                        percentileGap(99) / 1_000_000.0,
                        countEntities(), countLiving(), Bukkit.getWorlds().size()));
            }
        } else if (liveStartHeartbeat >= 0L) {
            liveStartHeartbeat = -1L;
            liveStartMs = -1L;
        }
    }

    private long percentileGap(int percentile) {
        if (recentGapCount == 0) return 0L;
        List<Long> values = new ArrayList<Long>(recentGapCount);
        int start = recentGapCount == recentGapsNs.length ? recentGapIndex : 0;
        for (int i = 0; i < recentGapCount; i++) {
            values.add(recentGapsNs[(start + i) % recentGapsNs.length]);
        }
        Collections.sort(values);
        int index = (int) Math.ceil((percentile / 100.0) * values.size()) - 1;
        if (index < 0) index = 0;
        if (index >= values.size()) index = values.size() - 1;
        return values.get(index);
    }

    private void sampleServerThread() {
        Thread server = null;
        for (Thread t : Thread.getAllStackTraces().keySet()) {
            if (t.getName() != null && t.getName().toLowerCase(Locale.US).contains("server thread")) {
                server = t;
                break;
            }
        }
        if (server == null) return;
        StackTraceElement[] stack = server.getStackTrace();
        StringBuilder sig = new StringBuilder();
        for (int i = 0; i < Math.min(12, stack.length); i++) sig.append(stack[i]).append('\n');
        String signature = sig.toString();
        long now = System.nanoTime();
        if (!signature.equals(lastStack)) {
            lastStack = signature;
            stackSinceNs = now;
        }
        long sameFor = now - stackSinceNs;
        if (sameFor >= 150_000_000L && now - lastStackReportNs >= 500_000_000L) {
            lastStackReportNs = now;
            StringBuilder out = new StringBuilder();
            for (int i = 0; i < Math.min(20, stack.length); i++) out.append("    at ").append(stack[i]).append('\n');
            plugin.getLogger().warning(String.format(Locale.US,
                    "[PERFTEST][MAIN-STACK] same stack %.0fms state=%s\n%s",
                    sameFor / 1_000_000.0, game.getState(), out));
        }
    }

    private void status(CommandSender s) {
        s.sendMessage(ChatColor.AQUA + String.format(Locale.US,
                "[PERFTEST] monitor=%s ticks=%d maxGap=%.1fms slowGaps=%d avgGap=%.2fms p95=%.2fms p99=%.2fms",
                heartbeatTask != null, heartbeatCount, maxGapNs / 1_000_000.0, slowTicks,
                percentileGap(50) / 1_000_000.0,
                percentileGap(95) / 1_000_000.0,
                percentileGap(99) / 1_000_000.0));
        counts(s);
    }

    private void counts(CommandSender s) {
        int total = 0;
        int living = 0;
        int chunks = 0;
        int tiles = 0;
        for (World w : Bukkit.getWorlds()) {
            int wt = 0;
            int wl = 0;
            int wc = 0;
            int wtiles = 0;
            try {
                wc = w.getLoadedChunks().length;
                for (Chunk c : w.getLoadedChunks()) wtiles += c.getTileEntities().length;
            } catch (Throwable ignored) {}
            for (Entity e : w.getEntities()) {
                wt++;
                if (e instanceof LivingEntity) wl++;
            }
            total += wt;
            living += wl;
            chunks += wc;
            tiles += wtiles;
            s.sendMessage(ChatColor.GRAY + w.getName() + ": entities=" + wt + " living=" + wl
                    + " chunks=" + wc + " tiles=" + wtiles);
        }
        s.sendMessage(ChatColor.AQUA + "TOTAL: entities=" + total + " living=" + living
                + " chunks=" + chunks + " tiles=" + tiles
                + " online=" + Bukkit.getOnlinePlayers().size());
    }

    private int countEntities() {
        int n = 0;
        for (World w : Bukkit.getWorlds()) n += w.getEntities().size();
        return n;
    }

    private int countLiving() {
        int n = 0;
        for (World w : Bukkit.getWorlds()) {
            for (Entity e : w.getEntities()) if (e instanceof LivingEntity) n++;
        }
        return n;
    }

    private void removeLiving(CommandSender s) {
        int removed = 0;
        for (World w : Bukkit.getWorlds()) {
            for (Entity e : w.getEntities().toArray(new Entity[0])) {
                if (e instanceof LivingEntity && !(e instanceof Player)) {
                    e.remove();
                    removed++;
                }
            }
        }
        s.sendMessage(ChatColor.GREEN + "[PERFTEST] removed " + removed + " non-player living entities.");
    }

    private void removeEntities(CommandSender s) {
        int removed = 0;
        for (World w : Bukkit.getWorlds()) {
            for (Entity e : w.getEntities().toArray(new Entity[0])) {
                if (!(e instanceof Player)) {
                    e.remove();
                    removed++;
                }
            }
        }
        s.sendMessage(ChatColor.GREEN + "[PERFTEST] removed " + removed + " non-player entities.");
    }

    private void removeProjectiles(CommandSender s) {
        int removed = 0;
        for (World w : Bukkit.getWorlds()) {
            for (Entity e : w.getEntities().toArray(new Entity[0])) {
                if (e instanceof Projectile) {
                    e.remove();
                    removed++;
                }
            }
        }
        s.sendMessage(ChatColor.GREEN + "[PERFTEST] removed " + removed + " projectiles.");
    }

    private void removeItems(CommandSender s) {
        int removed = 0;
        for (World w : Bukkit.getWorlds()) {
            for (Entity e : w.getEntities().toArray(new Entity[0])) {
                if (e instanceof Item) {
                    e.remove();
                    removed++;
                }
            }
        }
        s.sendMessage(ChatColor.GREEN + "[PERFTEST] removed " + removed + " dropped items.");
    }

    private void removeNonPlayerLiving(CommandSender s) {
        removeLiving(s);
    }

    private void clearPlayerInventories(CommandSender s) {
        int stacks = 0;
        for (Player p : Bukkit.getOnlinePlayers()) {
            for (ItemStack item : p.getInventory().getContents()) if (item != null) stacks++;
            for (ItemStack item : p.getInventory().getArmorContents()) if (item != null) stacks++;
            p.getInventory().clear();
            p.getInventory().setArmorContents(null);
            try { p.closeInventory(); } catch (Throwable ignored) {}
            p.updateInventory();
        }
        s.sendMessage(ChatColor.GREEN + "[PERFTEST] cleared " + stacks + " player inventory/armor stacks.");
    }

    private void minimizePlayer(CommandSender s) {
        clearPlayerInventories(s);
        for (Player p : Bukkit.getOnlinePlayers()) {
            try { p.setGameMode(GameMode.SPECTATOR); } catch (Throwable ignored) {}
            try { p.setAllowFlight(false); } catch (Throwable ignored) {}
            try { p.setFlying(false); } catch (Throwable ignored) {}
            try { p.setFoodLevel(20); p.setSaturation(20f); } catch (Throwable ignored) {}
            try { p.setExp(0f); p.setLevel(0); } catch (Throwable ignored) {}
            try { p.setFireTicks(0); p.setNoDamageTicks(0); } catch (Throwable ignored) {}
            try { p.setVelocity(new org.bukkit.util.Vector(0, 0, 0)); } catch (Throwable ignored) {}
            for (org.bukkit.potion.PotionEffect effect : p.getActivePotionEffects()) {
                try { p.removePotionEffect(effect.getType()); } catch (Throwable ignored) {}
            }
        }
        s.sendMessage(ChatColor.GREEN + "[PERFTEST] minimized player workload and switched players to spectator mode.");
    }

    private void suppressWorldWork(CommandSender s) {
        int worlds = 0;
        for (World w : Bukkit.getWorlds()) {
            worlds++;
            try { w.setGameRuleValue("doMobSpawning", "false"); } catch (Throwable ignored) {}
            try { w.setGameRuleValue("doFireTick", "false"); } catch (Throwable ignored) {}
            try { w.setGameRuleValue("randomTickSpeed", "0"); } catch (Throwable ignored) {}
            try { w.setGameRuleValue("doTileDrops", "false"); } catch (Throwable ignored) {}
            try { w.setGameRuleValue("doEntityDrops", "false"); } catch (Throwable ignored) {}
            try { w.setStorm(false); w.setThundering(false); } catch (Throwable ignored) {}
            try { w.setAutoSave(false); } catch (Throwable ignored) {}
            try { w.setTicksPerAnimalSpawns(0); w.setTicksPerMonsterSpawns(0); } catch (Throwable ignored) {}
            try { w.setAnimalSpawnLimit(0); w.setMonsterSpawnLimit(0); w.setAmbientSpawnLimit(0); } catch (Throwable ignored) {}
            try { w.setSpawnFlags(false, false); } catch (Throwable ignored) {}
            try { w.setKeepSpawnInMemory(false); } catch (Throwable ignored) {}
        }
        s.sendMessage(ChatColor.GREEN + "[PERFTEST] suppressed natural world work in " + worlds + " loaded worlds.");
    }

    private void unloadUnusedChunks(CommandSender s) {
        int before = 0;
        int unloaded = 0;
        for (World w : Bukkit.getWorlds()) {
            Chunk[] chunks = w.getLoadedChunks();
            before += chunks.length;
            for (Chunk c : chunks) {
                try {
                    if (!w.isChunkInUse(c.getX(), c.getZ())) {
                        if (w.unloadChunk(c.getX(), c.getZ(), false, true)) unloaded++;
                    }
                } catch (Throwable ignored) {}
            }
        }
        s.sendMessage(ChatColor.GREEN + "[PERFTEST] attempted to unload " + unloaded + " of " + before + " loaded chunks not in player use.");
    }

    private void unloadExtraWorlds(CommandSender s) {
        World keep = null;
        if (!Bukkit.getOnlinePlayers().isEmpty()) keep = Bukkit.getOnlinePlayers().iterator().next().getWorld();
        if (keep == null && !Bukkit.getWorlds().isEmpty()) keep = Bukkit.getWorlds().get(0);
        if (keep == null) {
            s.sendMessage(ChatColor.RED + "[PERFTEST] no world available to keep.");
            return;
        }
        int attempted = 0;
        int unloaded = 0;
        for (World w : new ArrayList<World>(Bukkit.getWorlds())) {
            if (w == keep) continue;
            attempted++;
            try {
                if (Bukkit.unloadWorld(w, false)) unloaded++;
            } catch (Throwable t) {
                plugin.getLogger().warning("[PERFTEST] failed unloading world " + w.getName() + ": " + t.getClass().getSimpleName());
            }
        }
        s.sendMessage(ChatColor.GREEN + "[PERFTEST] kept world " + keep.getName() + "; unloaded " + unloaded + "/" + attempted + " other worlds.");
    }

    private void reportTileEntities(CommandSender s) {
        int total = 0;
        for (World w : Bukkit.getWorlds()) {
            int count = 0;
            for (Chunk c : w.getLoadedChunks()) {
                try { count += c.getTileEntities().length; } catch (Throwable ignored) {}
            }
            total += count;
            s.sendMessage(ChatColor.GRAY + w.getName() + ": tileEntities=" + count);
        }
        s.sendMessage(ChatColor.AQUA + "[PERFTEST] loaded tile entities=" + total);
    }

    private void purgeTileEntities(CommandSender s) {
        int removed = 0;
        for (World w : Bukkit.getWorlds()) {
            for (Chunk c : w.getLoadedChunks()) {
                BlockState[] states;
                try { states = c.getTileEntities(); } catch (Throwable ignored) { continue; }
                for (BlockState state : states) {
                    try {
                        state.getBlock().setType(org.bukkit.Material.AIR, false);
                        removed++;
                    } catch (Throwable ignored) {}
                }
            }
        }
        s.sendMessage(ChatColor.RED + "[PERFTEST] removed " + removed + " tile-entity blocks from loaded chunks. This is destructive.");
    }

    private void cancelPhysicsEvents(CommandSender s) {
        // These are plugin-visible event hooks, not a switch for the NMS physics engine itself.
        // The command intentionally unregisters all MonsterMaze listeners, so it covers physics,
        // redstone, fluid, block growth and entity/block interaction handlers owned by this plugin.
        unregisterPluginEvents(s);
        s.sendMessage(ChatColor.YELLOW + "[PERFTEST] Note: Bukkit event cancellation cannot disable NMS physics itself; this removes MonsterMaze's event workload.");
    }

    private void unregisterPluginEvents(CommandSender s) {
        HandlerList.unregisterAll(plugin);
        s.sendMessage(ChatColor.GREEN + "[PERFTEST] unregistered all MonsterMaze event listeners.");
    }

    private void isolateForeignPlugins(CommandSender s) {
        int plugins = 0;
        int tasks = 0;
        for (Plugin other : Bukkit.getPluginManager().getPlugins()) {
            if (other == plugin) continue;
            plugins++;
            HandlerList.unregisterAll(other);
            for (BukkitTask task : Bukkit.getScheduler().getPendingTasks()) {
                if (task != null && task.getOwner() == other) {
                    task.cancel();
                    tasks++;
                }
            }
        }
        s.sendMessage(ChatColor.GREEN + "[PERFTEST] stripped " + plugins + " foreign plugin(s), cancelling " + tasks + " of their pending tasks/listeners.");
    }

    private void cancelTasks(CommandSender s, String prefix) {
        int cancelled = 0;
        for (BukkitTask task : Bukkit.getScheduler().getPendingTasks()) {
            if (task == null || task.getOwner() != plugin) continue;
            String name = taskClassName(task);
            if (name.startsWith(prefix)) {
                task.cancel();
                cancelled++;
            }
        }
        s.sendMessage(ChatColor.GREEN + "[PERFTEST] cancelled " + cancelled + " task(s) matching " + prefix);
    }

    private void cancelAllPluginTasksExceptThisMonitor(CommandSender s) {
        int cancelled = 0;
        for (BukkitTask task : Bukkit.getScheduler().getPendingTasks()) {
            if (task == null || task.getOwner() != plugin) continue;
            if (heartbeatTask != null && task.getTaskId() == heartbeatTask.getTaskId()) continue;
            if (samplerTask != null && task.getTaskId() == samplerTask.getTaskId()) continue;
            task.cancel();
            cancelled++;
        }
        s.sendMessage(ChatColor.GREEN + "[PERFTEST] cancelled " + cancelled + " MonsterMaze task(s), leaving monitor alive.");
    }

    private void isolateEverythingPluginOwned(CommandSender s) {
        isolateForeignPlugins(s);
        unregisterPluginEvents(s);
        cancelAllPluginTasksExceptThisMonitor(s);
        s.sendMessage(ChatColor.GOLD + "[PERFTEST] all plugin-owned scheduler/listener workload isolated.");
    }

    private String taskClassName(BukkitTask task) {
        try {
            Class<?> owner = task.getClass();
            while (owner != null) {
                try {
                    Method m = owner.getDeclaredMethod("getTaskClass");
                    m.setAccessible(true);
                    Class<?> c = (Class<?>) m.invoke(task);
                    return c == null ? "" : c.getName();
                } catch (NoSuchMethodException ignored) {
                    owner = owner.getSuperclass();
                }
            }
        } catch (Throwable ignored) {}
        return task.getClass().getName();
    }

    private void taskReport(CommandSender s) {
        int total = 0;
        int sync = 0;
        int async = 0;
        int mine = 0;
        int other = 0;
        for (BukkitTask task : Bukkit.getScheduler().getPendingTasks()) {
            if (task == null) continue;
            total++;
            if (task.isSync()) sync++; else async++;
            if (task.getOwner() == plugin) mine++; else other++;
            String ownerName = task.getOwner() == null ? "<null>" : task.getOwner().getName();
            s.sendMessage(ChatColor.GRAY + "task#" + task.getTaskId() + " " + (task.isSync() ? "SYNC" : "ASYNC")
                    + " owner=" + ownerName + " class=" + taskClassName(task));
        }
        s.sendMessage(ChatColor.AQUA + "[PERFTEST] pending tasks=" + total + " sync=" + sync + " async=" + async
                + " MonsterMaze=" + mine + " other=" + other);
    }

    private void jvm(CommandSender s) {
        Runtime rt = Runtime.getRuntime();
        long used = rt.totalMemory() - rt.freeMemory();
        ThreadMXBean threads = ManagementFactory.getThreadMXBean();
        s.sendMessage(String.format(Locale.US,
                ChatColor.AQUA + "[PERFTEST][JVM] heap used=%.1fMB committed=%.1fMB max=%.1fMB processors=%d threads=%d peakThreads=%d",
                used / 1048576.0, rt.totalMemory() / 1048576.0, rt.maxMemory() / 1048576.0,
                rt.availableProcessors(), threads.getThreadCount(), threads.getPeakThreadCount()));
        for (GarbageCollectorMXBean gc : ManagementFactory.getGarbageCollectorMXBeans()) {
            s.sendMessage(ChatColor.GRAY + "GC " + gc.getName() + ": collections=" + gc.getCollectionCount()
                    + " timeMs=" + gc.getCollectionTime());
        }
        for (MemoryPoolMXBean pool : ManagementFactory.getMemoryPoolMXBeans()) {
            try {
                if (pool.getUsage() != null) {
                    s.sendMessage(String.format(Locale.US, ChatColor.GRAY + "MEMPOOL %s: used=%.1fMB max=%.1fMB",
                            pool.getName(), pool.getUsage().getUsed() / 1048576.0,
                            pool.getUsage().getMax() / 1048576.0));
                }
            } catch (Throwable ignored) {}
        }
        s.sendMessage(ChatColor.GRAY + "JVM=" + System.getProperty("java.version")
                + " vendor=" + System.getProperty("java.vendor")
                + " arch=" + System.getProperty("os.arch")
                + " os=" + System.getProperty("os.name") + " " + System.getProperty("os.version"));
    }

    private void nuclear(CommandSender s) {
        s.sendMessage(ChatColor.GOLD + "[PERFTEST] NUCLEAR isolation starting. This is intentionally destructive.");
        // Keep the monitor alive; everything else is progressively stripped.
        suppressWorldWork(s);
        clearPlayerInventories(s);
        minimizePlayer(s);
        removeLiving(s);
        removeEntities(s);
        isolateEverythingPluginOwned(s);
        unloadUnusedChunks(s);
        unloadExtraWorlds(s);
        purgeTileEntities(s);
        s.sendMessage(ChatColor.RED + "[PERFTEST] NUCLEAR isolation complete. If TPS remains ~16, the remaining suspect is below plugin/entity/world workload: base NMS/Spigot tick cost, player packet processing, JVM/host scheduling, or an external process.");
    }
}
