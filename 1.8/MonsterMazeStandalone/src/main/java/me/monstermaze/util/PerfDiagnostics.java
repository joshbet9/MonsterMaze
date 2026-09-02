package me.monstermaze.util;

import me.monstermaze.MonsterMazePlugin;
import me.monstermaze.game.GameManager;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.scheduler.BukkitTask;

import java.util.Locale;

/**
 * Temporary 1.8 performance diagnostics. Designed to identify main-thread stalls
 * without adding per-tick logging noise during normal operation.
 */
public final class PerfDiagnostics {
    private final MonsterMazePlugin plugin;
    private final GameManager game;
    private BukkitTask heartbeatTask;
    private BukkitTask snapshotTask;
    private BukkitTask samplerTask;
    private volatile Thread serverThread;
    private volatile boolean running;

    private long lastHeartbeatNs;
    private long heartbeatCount;
    private long maxGapNs;
    private long slowTicks;

    private String lastStack = "";
    private long stackSinceNs;
    private long lastStackReportNs;

    public PerfDiagnostics(MonsterMazePlugin plugin, GameManager game) {
        this.plugin = plugin;
        this.game = game;
    }

    public void start() {
        if (running) return;
        running = true;
        plugin.getLogger().info("[PERF] Diagnostics ENABLED (temporary). Thresholds: tick gap >75ms, stack stall >150ms.");

        heartbeatTask = Bukkit.getScheduler().runTaskTimer(plugin, new Runnable() {
            @Override public void run() {
                long now = System.nanoTime();
                if (lastHeartbeatNs != 0L) {
                    long gap = now - lastHeartbeatNs;
                    if (gap > maxGapNs) maxGapNs = gap;
                    if (gap > 75_000_000L) {
                        slowTicks++;
                        plugin.getLogger().warning(String.format(Locale.US,
                                "[PERF][TICK-GAP] %.1f ms since previous heartbeat | state=%s players=%d online=%d slowTicks=%d",
                                gap / 1_000_000.0, game.getState(), game.getAlivePlayers().size(),
                                Bukkit.getOnlinePlayers().size(), slowTicks));
                    }
                }
                lastHeartbeatNs = now;
                heartbeatCount++;
            }
        }, 1L, 1L);

        snapshotTask = Bukkit.getScheduler().runTaskTimer(plugin, new Runnable() {
            @Override public void run() { snapshot(); }
        }, 20L, 20L);

        samplerTask = Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, new Runnable() {
            @Override public void run() { sampleServerThread(); }
        }, 1L, 1L);
    }

    public void stop() {
        running = false;
        if (heartbeatTask != null) heartbeatTask.cancel();
        if (snapshotTask != null) snapshotTask.cancel();
        if (samplerTask != null) samplerTask.cancel();
        heartbeatTask = null;
        snapshotTask = null;
        samplerTask = null;
        plugin.getLogger().info(String.format(Locale.US,
                "[PERF] Diagnostics stopped. heartbeats=%d maxGap=%.1fms slowTicks=%d",
                heartbeatCount, maxGapNs / 1_000_000.0, slowTicks));
    }

    private void snapshot() {
        int players = Bukkit.getOnlinePlayers().size();
        int alive = game.getAlivePlayers().size();
        int entities = 0;
        int living = 0;
        for (World world : Bukkit.getWorlds()) {
            for (Entity e : world.getEntities()) {
                entities++;
                if (e instanceof org.bukkit.entity.LivingEntity) living++;
            }
        }
        plugin.getLogger().info(String.format(Locale.US,
                "[PERF][SNAPSHOT] state=%s alive=%d online=%d worlds=%d entities=%d living=%d maxGap=%.1fms slowTicks=%d",
                game.getState(), alive, players, Bukkit.getWorlds().size(), entities, living,
                maxGapNs / 1_000_000.0, slowTicks));
    }

    private void sampleServerThread() {
        if (!running) return;
        Thread t = serverThread;
        if (t == null || !t.isAlive()) {
            t = findServerThread();
            serverThread = t;
            if (t == null) return;
        }

        StackTraceElement[] stack = t.getStackTrace();
        String signature = signature(stack);
        long now = System.nanoTime();

        if (!signature.equals(lastStack)) {
            lastStack = signature;
            stackSinceNs = now;
        }

        long sameFor = now - stackSinceNs;
        if (sameFor >= 150_000_000L && now - lastStackReportNs >= 500_000_000L) {
            lastStackReportNs = now;
            plugin.getLogger().warning(String.format(Locale.US,
                    "[PERF][MAIN-STACK] main thread appears stuck in same stack for %.0fms | state=%s\n%s",
                    sameFor / 1_000_000.0, game.getState(), formatStack(stack)));
        }
    }

    private Thread findServerThread() {
        for (Thread t : Thread.getAllStackTraces().keySet()) {
            String name = t.getName();
            if ("Server thread".equalsIgnoreCase(name) || name.toLowerCase(Locale.US).contains("server thread")) return t;
        }
        return null;
    }

    private String signature(StackTraceElement[] stack) {
        StringBuilder b = new StringBuilder();
        int limit = Math.min(stack.length, 10);
        for (int i = 0; i < limit; i++) b.append(stack[i].toString()).append('\n');
        return b.toString();
    }

    private String formatStack(StackTraceElement[] stack) {
        StringBuilder b = new StringBuilder();
        int limit = Math.min(stack.length, 18);
        for (int i = 0; i < limit; i++) b.append("    at ").append(stack[i]).append('\n');
        return b.toString();
    }
}
