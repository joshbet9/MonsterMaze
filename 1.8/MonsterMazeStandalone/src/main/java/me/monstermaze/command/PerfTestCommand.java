package me.monstermaze.command;

import me.monstermaze.MonsterMazePlugin;
import me.monstermaze.game.GameManager;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;

import java.lang.reflect.Method;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Temporary, cumulative performance isolation harness for the 1.8 implementation.
 *
 * The commands deliberately disable/remove workload rather than changing normal game code.
 * This lets a single running server be tested from broad causes to narrow causes while the
 * PerfTest monitor continues measuring the actual main-thread tick rate.
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
        if ("start".equals(op)) {
            startMonitor(sender);
        } else if ("stop".equals(op)) {
            stopMonitor(sender);
        } else if ("status".equals(op)) {
            status(sender);
        } else if ("living".equals(op)) {
            removeLiving(sender);
        } else if ("entities".equals(op)) {
            removeEntities(sender);
        } else if ("projectiles".equals(op)) {
            removeProjectiles(sender);
        } else if ("items".equals(op)) {
            removeItems(sender);
        } else if ("monsterlogic".equals(op)) {
            cancelTasks(sender, "me.monstermaze.entity.MonsterManager$");
        } else if ("kitlogic".equals(op)) {
            cancelTasks(sender, "me.monstermaze.kit.KitManager$");
        } else if ("npcs".equals(op)) {
            removeNonPlayerNamedOrLiving(sender);
        } else if ("inventory".equals(op) || "nbt".equals(op)) {
            clearPlayerInventories(sender);
        } else if ("world".equals(op) || "worlds".equals(op)) {
            suppressWorldWork(sender);
        } else if ("player".equals(op)) {
            minimizePlayer(sender);
        } else if ("gametasks".equals(op)) {
            cancelTasks(sender, "me.monstermaze.game.GameManager$");
        } else if ("alltasks".equals(op)) {
            cancelAllPluginTasksExceptThisMonitor(sender);
        } else if ("all".equals(op)) {
            all(sender);
        } else if ("count".equals(op)) {
            counts(sender);
        } else {
            sender.sendMessage(ChatColor.RED + "Unknown /perftest option. Try /perftest help");
        }
        return true;
    }

    private void help(CommandSender s) {
        s.sendMessage(ChatColor.GOLD + "=== Monster Maze PERF ISOLATION ===");
        s.sendMessage(ChatColor.YELLOW + "/perftest start" + ChatColor.GRAY + " - start continuous tick/TPS monitor");
        s.sendMessage(ChatColor.YELLOW + "/perftest status" + ChatColor.GRAY + " - current counters/entity counts");
        s.sendMessage(ChatColor.YELLOW + "/perftest count" + ChatColor.GRAY + " - detailed entity counts by world/type");
        s.sendMessage(ChatColor.AQUA + "Cumulative isolation switches (safe order):");
        s.sendMessage(ChatColor.YELLOW + "/perftest monsterlogic" + ChatColor.GRAY + " - stop MonsterManager scheduled work");
        s.sendMessage(ChatColor.YELLOW + "/perftest kitlogic" + ChatColor.GRAY + " - stop KitManager scheduled work");
        s.sendMessage(ChatColor.YELLOW + "/perftest living" + ChatColor.GRAY + " - remove every non-player LivingEntity");
        s.sendMessage(ChatColor.YELLOW + "/perftest entities" + ChatColor.GRAY + " - remove every non-player entity");
        s.sendMessage(ChatColor.YELLOW + "/perftest projectiles" + ChatColor.GRAY + " - remove projectiles");
        s.sendMessage(ChatColor.YELLOW + "/perftest items" + ChatColor.GRAY + " - remove dropped items");
        s.sendMessage(ChatColor.YELLOW + "/perftest npcs" + ChatColor.GRAY + " - remove non-player living entities (NPC/mob cleanup)");
        s.sendMessage(ChatColor.YELLOW + "/perftest inventory" + ChatColor.GRAY + " - clear player inventory/armor to remove item/NBT work");
        s.sendMessage(ChatColor.YELLOW + "/perftest world" + ChatColor.GRAY + " - disable natural spawns/random ticks/weather/autosave");
        s.sendMessage(ChatColor.YELLOW + "/perftest player" + ChatColor.GRAY + " - minimize player-side workload (inventory/effects/flight)");
        s.sendMessage(ChatColor.YELLOW + "/perftest gametasks" + ChatColor.GRAY + " - stop GameManager repeating tasks (last-resort test)");
        s.sendMessage(ChatColor.YELLOW + "/perftest alltasks" + ChatColor.GRAY + " - stop every plugin task except this monitor (last-resort) ");
        s.sendMessage(ChatColor.YELLOW + "/perftest all" + ChatColor.GRAY + " - apply all non-destructive isolation switches at once");
        s.sendMessage(ChatColor.RED + "IMPORTANT: switches are intentionally cumulative and mostly irreversible until server restart.");
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
        lastStack = "";
        stackSinceNs = 0L;
        lastStackReportNs = 0L;

        heartbeatTask = Bukkit.getScheduler().runTaskTimer(plugin, new Runnable() {
            @Override public void run() {
                heartbeat();
            }
        }, 1L, 1L);
        samplerTask = Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, new Runnable() {
            @Override public void run() {
                sampleServerThread();
            }
        }, 1L, 1L);
        s.sendMessage(ChatColor.GREEN + "[PERFTEST] monitor started. Effective TPS will be logged during LIVE.");
    }

    private void stopMonitor(CommandSender s) {
        if (heartbeatTask != null) heartbeatTask.cancel();
        if (samplerTask != null) samplerTask.cancel();
        heartbeatTask = null;
        samplerTask = null;
        s.sendMessage(String.format(Locale.US,
                ChatColor.GREEN + "[PERFTEST] stopped: ticks=%d maxGap=%.1fms slowGaps=%d",
                heartbeatCount, maxGapNs / 1_000_000.0, slowTicks));
    }

    private void heartbeat() {
        long now = System.nanoTime();
        if (lastHeartbeatNs != 0L) {
            long gap = now - lastHeartbeatNs;
            if (gap > maxGapNs) maxGapNs = gap;
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
            if (elapsed > 0 && (ticks % 20 == 0)) {
                plugin.getLogger().info(String.format(Locale.US,
                        "[PERFTEST][TPS] elapsed=%.1fs serverTicks=%d effectiveTPS=%.2f entities=%d living=%d",
                        elapsed, ticks, ticks / elapsed, countEntities(), countLiving()));
            }
        } else if (liveStartHeartbeat >= 0L) {
            liveStartHeartbeat = -1L;
            liveStartMs = -1L;
        }
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
        for (int i = 0; i < Math.min(10, stack.length); i++) sig.append(stack[i]).append('\n');
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
            for (int i = 0; i < Math.min(18, stack.length); i++) out.append("    at ").append(stack[i]).append('\n');
            plugin.getLogger().warning(String.format(Locale.US,
                    "[PERFTEST][MAIN-STACK] same stack %.0fms state=%s\n%s",
                    sameFor / 1_000_000.0, game.getState(), out));
        }
    }

    private void status(CommandSender s) {
        s.sendMessage(ChatColor.AQUA + "[PERFTEST] monitor=" + (heartbeatTask != null)
                + " ticks=" + heartbeatCount
                + " maxGap=" + String.format(Locale.US, "%.1fms", maxGapNs / 1_000_000.0)
                + " slowGaps=" + slowTicks);
        counts(s);
    }

    private void counts(CommandSender s) {
        int total = 0;
        int living = 0;
        for (World w : Bukkit.getWorlds()) {
            int wt = 0;
            int wl = 0;
            for (Entity e : w.getEntities()) {
                wt++;
                if (e instanceof LivingEntity) wl++;
            }
            total += wt;
            living += wl;
            s.sendMessage(ChatColor.GRAY + w.getName() + ": entities=" + wt + " living=" + wl);
        }
        s.sendMessage(ChatColor.AQUA + "TOTAL: entities=" + total + " living=" + living
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

    private void removeNonPlayerNamedOrLiving(CommandSender s) {
        // 1.8 has no generic AI toggle. Removing non-player living entities is the reliable
        // diagnostic equivalent for vanilla/custom AI ticking.
        removeLiving(s);
    }

    private void clearPlayerInventories(CommandSender s) {
        int stacks = 0;
        for (Player p : Bukkit.getOnlinePlayers()) {
            for (ItemStack item : p.getInventory().getContents()) if (item != null) stacks++;
            for (ItemStack item : p.getInventory().getArmorContents()) if (item != null) stacks++;
            p.getInventory().clear();
            p.getInventory().setArmorContents(null);
            p.updateInventory();
        }
        s.sendMessage(ChatColor.GREEN + "[PERFTEST] cleared " + stacks + " player inventory/armor stacks.");
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
        }
        s.sendMessage(ChatColor.GREEN + "[PERFTEST] suppressed natural world work in " + worlds + " loaded worlds.");
    }

    private void minimizePlayer(CommandSender s) {
        clearPlayerInventories(s);
        for (Player p : Bukkit.getOnlinePlayers()) {
            p.setAllowFlight(false);
            p.setFlying(false);
            p.setFoodLevel(20);
            p.setSaturation(20f);
            p.setExp(0f);
            p.setLevel(0);
        }
        s.sendMessage(ChatColor.GREEN + "[PERFTEST] minimized player-side inventory/flight/hunger/XP workload.");
    }

    private void cancelTasks(CommandSender s, String prefix) {
        int cancelled = 0;
        for (BukkitTask task : Bukkit.getScheduler().getPendingTasks()) {
            if (task == null || task.getOwner() != plugin || !task.isSync()) continue;
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
            if (task == null || task.getOwner() != plugin || !task.isSync()) continue;
            if (heartbeatTask != null && task.getTaskId() == heartbeatTask.getTaskId()) continue;
            if (samplerTask != null && task.getTaskId() == samplerTask.getTaskId()) continue;
            task.cancel();
            cancelled++;
        }
        s.sendMessage(ChatColor.GREEN + "[PERFTEST] cancelled " + cancelled + " plugin sync task(s), leaving monitor alive.");
    }

    private String taskClassName(BukkitTask task) {
        try {
            Method m = task.getClass().getDeclaredMethod("getTaskClass");
            m.setAccessible(true);
            Class<?> c = (Class<?>) m.invoke(task);
            return c == null ? "" : c.getName();
        } catch (Throwable ignored) {
            return "";
        }
    }

    private void all(CommandSender s) {
        s.sendMessage(ChatColor.GOLD + "[PERFTEST] Applying cumulative broad isolation switches...");
        cancelTasks(s, "me.monstermaze.entity.MonsterManager$");
        cancelTasks(s, "me.monstermaze.kit.KitManager$");
        clearPlayerInventories(s);
        suppressWorldWork(s);
        removeLiving(s);
        removeEntities(s);
        s.sendMessage(ChatColor.GOLD + "[PERFTEST] ALL broad switches applied. Watch effective TPS now.");
    }
}
