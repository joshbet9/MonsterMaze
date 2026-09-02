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
import java.util.Map;
import java.util.UUID;

/** Records every completed solo attempt for the Discord feed; PB filtering is not applied here. */
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
        checkForElimination();
        snapshotLiveRun();
    }

    private void snapshotLiveRun() {
        if (!plugin.isSoloMode()) {
            runs.clear();
            return;
        }
        GameState state = plugin.getGameManager().getState();
        if (state != GameState.LIVE) {
            if (state == GameState.IDLE) runs.clear();
            return;
        }
        int pattern = plugin.getGameManager().getPatternIndex();
        if (pattern < 0) return;
        for (Player player : plugin.getGameManager().getAlivePlayers()) {
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
        if (!plugin.isSoloMode()) return;
        Bukkit.getScheduler().runTask(plugin, new Runnable() {
            @Override public void run() {
                RunInfo info = runs.remove(player.getUniqueId());
                if (info == null) return;
                record(player, info);
            }
        });
    }

    /**
     * Detects Monster Maze's own elimination path. GameManager removes the player from
     * its alive set and changes the game to ENDING without firing PlayerDeathEvent.
     */
    private void checkForElimination() {
        if (!plugin.isSoloMode() || runs.isEmpty()) return;
        GameState state = plugin.getGameManager().getState();
        if (state != GameState.LIVE && state != GameState.ENDING) return;

        Player player = null;
        for (UUID uuid : new java.util.HashSet<UUID>(runs.keySet())) {
            Player candidate = Bukkit.getPlayer(uuid);
            if (candidate != null && !plugin.getGameManager().getAlivePlayers().contains(candidate)) {
                player = candidate;
                break;
            }
        }
        if (player == null) return;

        RunInfo info = runs.remove(player.getUniqueId());
        if (info != null) record(player, info);
    }

    private void record(final Player player, final RunInfo info) {
        if (player == null || info == null) return;
        long liveStart = plugin.getGameManager().getGameLiveTime();
        long elapsed = liveStart > 0 ? Math.max(0L, System.currentTimeMillis() - liveStart) : 0L;
        plugin.getRunRecorder().record(player, plugin.getMode(), info.pattern, info.kit,
                plugin.getGameManager().getStage(), elapsed);
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
