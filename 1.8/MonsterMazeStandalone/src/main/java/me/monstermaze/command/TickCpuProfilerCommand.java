package me.monstermaze.command;

import me.monstermaze.MonsterMazePlugin;
import me.monstermaze.game.GameManager;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.scheduler.BukkitTask;

import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;
import java.util.Locale;

/**
 * Measures actual server-thread CPU time separately from wall-clock tick spacing.
 *
 * This is deliberately separate from PerfTestCommand's heartbeat-gap measurement.
 * A heartbeat gap includes both actual server work and time spent waiting/sleeping in
 * the MinecraftServer tick loop. Thread CPU time excludes sleep/wait, so the comparison
 * tells us whether the ~62 ms tick interval is caused by real CPU work or by scheduling.
 */
public final class TickCpuProfilerCommand implements CommandExecutor {
    private final MonsterMazePlugin plugin;
    private final GameManager game;
    private final ThreadMXBean threadMx = ManagementFactory.getThreadMXBean();

    private BukkitTask task;
    private long tickCount;
    private long lastWallNs;
    private long lastCpuNs;
    private long windowStartWallNs;
    private long windowStartCpuNs;
    private long windowStartTicks;
    private long serverThreadId = -1L;

    public TickCpuProfilerCommand(MonsterMazePlugin plugin, GameManager game) {
        this.plugin = plugin;
        this.game = game;
        if (threadMx.isThreadCpuTimeSupported() && !threadMx.isThreadCpuTimeEnabled()) {
            try {
                threadMx.setThreadCpuTimeEnabled(true);
            } catch (UnsupportedOperationException ignored) {
            } catch (SecurityException ignored) {
            }
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("monstermaze.admin")) {
            sender.sendMessage(ChatColor.RED + "No permission.");
            return true;
        }

        String op = args.length == 0 ? "status" : args[0].toLowerCase(Locale.US);
        if ("start".equals(op)) start(sender);
        else if ("stop".equals(op)) stop(sender);
        else if ("status".equals(op)) status(sender);
        else if ("reset".equals(op)) reset(sender);
        else help(sender);
        return true;
    }

    private void help(CommandSender sender) {
        sender.sendMessage(ChatColor.GOLD + "=== Monster Maze SERVER TICK CPU PROFILER ===");
        sender.sendMessage(ChatColor.YELLOW + "/tickprofile start" + ChatColor.GRAY + " - measure server-thread CPU vs wall time");
        sender.sendMessage(ChatColor.YELLOW + "/tickprofile status" + ChatColor.GRAY + " - show current measurements");
        sender.sendMessage(ChatColor.YELLOW + "/tickprofile reset" + ChatColor.GRAY + " - reset measurement window");
        sender.sendMessage(ChatColor.YELLOW + "/tickprofile stop" + ChatColor.GRAY + " - stop profiler");
    }

    private void start(CommandSender sender) {
        if (task != null) {
            sender.sendMessage(ChatColor.YELLOW + "[TICKPROFILE] already running.");
            return;
        }
        resetMeasurements();
        task = Bukkit.getScheduler().runTaskTimer(plugin, new Runnable() {
            @Override
            public void run() {
                sample();
            }
        }, 1L, 1L);
        sender.sendMessage(ChatColor.GREEN + "[TICKPROFILE] started. CPU time excludes Thread.sleep/waiting.");
    }

    private void stop(CommandSender sender) {
        if (task != null) {
            task.cancel();
            task = null;
        }
        sender.sendMessage(ChatColor.GREEN + "[TICKPROFILE] stopped.");
        logWindow(true);
    }

    private void reset(CommandSender sender) {
        resetMeasurements();
        sender.sendMessage(ChatColor.GREEN + "[TICKPROFILE] measurement window reset.");
    }

    private void status(CommandSender sender) {
        if (task == null) {
            sender.sendMessage(ChatColor.YELLOW + "[TICKPROFILE] not running.");
            return;
        }
        logWindow(false);
    }

    private void resetMeasurements() {
        tickCount = 0L;
        lastWallNs = 0L;
        lastCpuNs = 0L;
        windowStartWallNs = 0L;
        windowStartCpuNs = 0L;
        windowStartTicks = 0L;
        serverThreadId = Thread.currentThread().getId();
    }

    /** Runs synchronously on the actual server thread once per tick. */
    private void sample() {
        long nowWall = System.nanoTime();
        long nowCpu = threadMx.isThreadCpuTimeSupported()
                ? threadMx.getThreadCpuTime(Thread.currentThread().getId())
                : -1L;

        if (lastWallNs == 0L) {
            lastWallNs = nowWall;
            lastCpuNs = nowCpu;
            windowStartWallNs = nowWall;
            windowStartCpuNs = nowCpu;
            windowStartTicks = tickCount;
        }

        tickCount++;
        serverThreadId = Thread.currentThread().getId();

        if (tickCount % 20L == 0L) {
            logWindow(false);
            windowStartWallNs = nowWall;
            windowStartCpuNs = nowCpu;
            windowStartTicks = tickCount;
        }

        lastWallNs = nowWall;
        lastCpuNs = nowCpu;
    }

    private void logWindow(boolean finalWindow) {
        if (windowStartWallNs == 0L || lastWallNs == 0L) {
            plugin.getLogger().info("[TICKPROFILE] no samples yet.");
            return;
        }

        long wallNs = Math.max(1L, lastWallNs - windowStartWallNs);
        long cpuNs = lastCpuNs >= 0L && windowStartCpuNs >= 0L
                ? Math.max(0L, lastCpuNs - windowStartCpuNs)
                : -1L;
        long ticks = Math.max(0L, tickCount - windowStartTicks);

        double wallMsPerTick = ticks > 0 ? wallNs / 1_000_000.0 / ticks : 0.0;
        double cpuMsPerTick = cpuNs >= 0L && ticks > 0 ? cpuNs / 1_000_000.0 / ticks : -1.0;
        double cpuUtil = cpuNs >= 0L ? (cpuNs * 100.0 / wallNs) : -1.0;
        double measuredTps = wallNs > 0L ? ticks * 1_000_000_000.0 / wallNs : 0.0;

        String cpuPart = cpuNs >= 0L
                ? String.format(Locale.US, "cpu=%.2fms/tick cpuUtil=%.1f%%", cpuMsPerTick, cpuUtil)
                : "cpu=UNAVAILABLE";
        String phase = game.getState() == null ? "?" : game.getState().toString();

        plugin.getLogger().info(String.format(Locale.US,
                "[TICKPROFILE] %s ticks=%d wall=%.2fms/tick measuredTPS=%.2f %s phase=%s serverThreadId=%d",
                finalWindow ? "FINAL" : "WINDOW",
                ticks, wallMsPerTick, measuredTps, cpuPart, phase, serverThreadId));

        if (cpuNs >= 0L) {
            if (cpuMsPerTick < 55.0 && wallMsPerTick > 57.0) {
                plugin.getLogger().info("[TICKPROFILE][DIAGNOSIS] server thread is not CPU-saturated; significant wall-clock time is being spent waiting/sleeping/scheduled away from CPU.");
            } else if (cpuMsPerTick >= 57.0) {
                plugin.getLogger().info("[TICKPROFILE][DIAGNOSIS] server thread is CPU-busy for most of each tick; investigate actual tick workload/NMS/plugin work next.");
            }
        }
    }
}
