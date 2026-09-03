package me.monstermaze.stats;

import me.monstermaze.MonsterMazePlugin;
import me.monstermaze.game.GameState;
import me.monstermaze.kit.KitType;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.UUID;

/** Records every completed player attempt for the competition submitter. */
public final class SoloRunCompletionListener implements Listener {
    private final MonsterMazePlugin plugin;
    private final Map<UUID, RunInfo> runs = new HashMap<UUID, RunInfo>();
    private final BukkitTask task;

    public SoloRunCompletionListener(final MonsterMazePlugin plugin) {
        this.plugin = plugin;
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        this.task = Bukkit.getScheduler().runTaskTimer(plugin, new Runnable() {
            @Override public void run() { tick(); }
        }, 1L, 1L);
    }

    private void tick() {
        GameState state = plugin.getGameManager().getState();
        if (state == GameState.LIVE) {
            snapshotLiveRun();
            checkForElimination();
        } else if (state == GameState.ENDING) {
            checkForElimination();
            recordRemainingPlayers();
        } else if (state == GameState.IDLE) {
            runs.clear();
        }
    }

    private void snapshotLiveRun() {
        if (!plugin.isRecordRuns()) {
            runs.clear();
            return;
        }
        int pattern = plugin.getGameManager().getPatternIndex();
        if (pattern < 0) return;

        for (Player player : plugin.getGameManager().getAlivePlayers()) {
            if (runs.containsKey(player.getUniqueId())) continue;
            KitType kit = plugin.getGameManager().getKitManager().getKit(player);
            if (kit == null) continue;
            runs.put(player.getUniqueId(), new RunInfo(pattern, kit.id));
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDeath(PlayerDeathEvent event) {
        scheduleRecord(event.getEntity());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        scheduleRecord(event.getPlayer());
    }

    private void scheduleRecord(final Player player) {
        if (!plugin.isRecordRuns()) return;
        Bukkit.getScheduler().runTask(plugin, new Runnable() {
            @Override public void run() {
                RunInfo info = runs.remove(player.getUniqueId());
                if (info == null) return;
                record(player, info, plugin.getGameManager().getStage());
            }
        });
    }

    /** Detects Monster Maze's own elimination path, which does not fire PlayerDeathEvent. */
    private void checkForElimination() {
        if (!plugin.isRecordRuns() || runs.isEmpty()) return;
        GameState state = plugin.getGameManager().getState();
        if (state != GameState.LIVE && state != GameState.ENDING) return;

        for (UUID uuid : new HashSet<UUID>(runs.keySet())) {
            Player player = Bukkit.getPlayer(uuid);
            if (player == null || !player.isOnline()) continue;
            if (!plugin.getGameManager().getAlivePlayers().contains(player)) {
                RunInfo info = runs.remove(uuid);
                if (info != null) record(player, info, plugin.getGameManager().getStage());
            }
        }
    }

    /** Records survivors when a multiplayer game transitions to ENDING. */
    private void recordRemainingPlayers() {
        if (!plugin.isRecordRuns()) return;
        for (UUID uuid : new HashSet<UUID>(runs.keySet())) {
            Player player = Bukkit.getPlayer(uuid);
            if (player == null || !player.isOnline()) continue;
            if (plugin.getGameManager().getAlivePlayers().contains(player)) {
                RunInfo info = runs.remove(uuid);
                if (info != null) record(player, info, plugin.getGameManager().getStage());
            }
        }
    }

    private void record(Player player, RunInfo info, int stage) {
        if (player == null || info == null || stage < 1) return;
        long liveStart = plugin.getGameManager().getGameLiveTime();
        long elapsed = liveStart > 0 ? Math.max(0L, System.currentTimeMillis() - liveStart) : 0L;
        plugin.getRunRecorder().record(player, plugin.getMode(), info.pattern, info.kit, stage, elapsed);
    }

    public void shutdown() {
        task.cancel();
        runs.clear();
    }

    private static final class RunInfo {
        final int pattern;
        final String kit;
        RunInfo(int pattern, String kit) {
            this.pattern = pattern;
            this.kit = kit;
        }
    }
}
