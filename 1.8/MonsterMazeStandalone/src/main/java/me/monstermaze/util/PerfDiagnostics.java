package me.monstermaze.util;

import me.monstermaze.MonsterMazePlugin;
import me.monstermaze.game.GameManager;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;

import java.lang.reflect.Field;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Temporary 1.8 performance diagnostics. Designed to identify main-thread stalls
 * without adding per-tick logging noise during normal operation.
 */
public final class PerfDiagnostics {
    private static final boolean STRIP_KIT_ITEM_META_TEST = true;

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

    private long liveHeartbeatStart = -1L;
    private long liveElapsedLogSecond = -1L;
    private int lastLoggedPhaseTimer = Integer.MIN_VALUE;
    private final SimpleDateFormat clockFormat = new SimpleDateFormat("HH:mm:ss.SSS", Locale.US);
    private Field phaseTimerField;
    private boolean kitMetaStrippedThisLive;

    private String lastStack = "";
    private long stackSinceNs;
    private long lastStackReportNs;

    public PerfDiagnostics(MonsterMazePlugin plugin, GameManager game) {
        this.plugin = plugin;
        this.game = game;
        try {
            phaseTimerField = GameManager.class.getDeclaredField("phaseTimer");
            phaseTimerField.setAccessible(true);
        } catch (Exception e) {
            plugin.getLogger().warning("[PERF] Could not access phaseTimer for diagnostics: " + e.getMessage());
        }
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
                                "[PERF][TICK-GAP] %s | %.1f ms since previous heartbeat | state=%s players=%d online=%d slowTicks=%d",
                                wallClock(), gap / 1_000_000.0, game.getState(), game.getAlivePlayers().size(),
                                Bukkit.getOnlinePlayers().size(), slowTicks));
                    }
                }
                lastHeartbeatNs = now;
                heartbeatCount++;

                if (game.isLive()) {
                    if (liveHeartbeatStart < 0L) {
                        liveHeartbeatStart = heartbeatCount;
                        liveElapsedLogSecond = -1L;
                        lastLoggedPhaseTimer = Integer.MIN_VALUE;
                        kitMetaStrippedThisLive = false;
                        if (STRIP_KIT_ITEM_META_TEST) stripKitItemMeta();
                        plugin.getLogger().info(String.format(Locale.US,
                                "[PERF][LIVE-START] real=%s stopwatchElapsed=0.000s serverTick=0 phaseTimer=%d",
                                wallClock(), getPhaseTimer()));
                    }

                    long serverTicks = heartbeatCount - liveHeartbeatStart;
                    long liveStartMs = game.getGameLiveTime();
                    double elapsed = liveStartMs > 0L
                            ? Math.max(0L, System.currentTimeMillis() - liveStartMs) / 1000.0
                            : 0.0;
                    long elapsedSecond = (long) elapsed;
                    if (elapsedSecond != liveElapsedLogSecond) {
                        liveElapsedLogSecond = elapsedSecond;
                        double effectiveTps = elapsed > 0.0 ? serverTicks / elapsed : 0.0;
                        plugin.getLogger().info(String.format(Locale.US,
                                "[PERF][REALTIME] real=%s elapsed=%.3fs serverTicks=%d effectiveTPS=%.2f phaseTimer=%d",
                                wallClock(), elapsed, serverTicks, effectiveTps, getPhaseTimer()));
                    }

                    int timer = getPhaseTimer();
                    if (timer != Integer.MIN_VALUE && timer != lastLoggedPhaseTimer) {
                        lastLoggedPhaseTimer = timer;
                        plugin.getLogger().info(String.format(Locale.US,
                                "[PERF][TIMER] real=%s elapsed=%.3fs serverTicks=%d phaseTimer=%d",
                                wallClock(), elapsed, serverTicks, timer));
                    }
                } else if (liveHeartbeatStart >= 0L) {
                    liveHeartbeatStart = -1L;
                    liveElapsedLogSecond = -1L;
                    lastLoggedPhaseTimer = Integer.MIN_VALUE;
                    kitMetaStrippedThisLive = false;
                }
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

    /** TEST 6: remove ItemMeta/NBT-bearing metadata from the player's inventory once at LIVE start. */
    private void stripKitItemMeta() {
        int stripped = 0;
        for (Player player : Bukkit.getOnlinePlayers()) {
            org.bukkit.inventory.PlayerInventory inv = player.getInventory();
            for (int i = 0; i < inv.getSize(); i++) {
                ItemStack item = inv.getItem(i);
                if (item == null || !item.hasItemMeta()) continue;
                inv.setItem(i, new ItemStack(item.getType(), item.getAmount(), item.getDurability()));
                stripped++;
            }
        }
        kitMetaStrippedThisLive = true;
        plugin.getLogger().info("[PERF][TEST6] stripped ItemMeta from " + stripped + " kit inventory stack(s)");
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

    private int getPhaseTimer() {
        if (phaseTimerField == null) return Integer.MIN_VALUE;
        try {
            return phaseTimerField.getInt(game);
        } catch (Exception e) {
            return Integer.MIN_VALUE;
        }
    }

    private String wallClock() {
        return clockFormat.format(new Date());
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
