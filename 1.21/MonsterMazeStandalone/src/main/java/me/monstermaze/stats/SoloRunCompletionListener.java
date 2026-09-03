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

/**
 * Bridges the existing 1.21 game loop to the Solo run recorder without changing
 * the mature multiplayer elimination flow. It snapshots each run's kit PB while
 * the game is live, then emits a record only when the final stage beats that PB.
 */
public final class SoloRunCompletionListener implements Listener {

    private final MonsterMazePlugin plugin;
    private final Map<UUID, Baseline> baselines = new HashMap<UUID, Baseline>();
    private final BukkitTask task;

    public SoloRunCompletionListener(final MonsterMazePlugin plugin) {
        this.plugin = plugin;
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        this.task = Bukkit.getScheduler().runTaskTimer(plugin, new Runnable() {
            @Override public void run() { snapshotLiveRun(); }
        }, 1L, 1L);
    }

    private void snapshotLiveRun() {
        if (!plugin.isSoloMode()) {
            baselines.clear();
            return;
        }

        GameState state = plugin.getGameManager().getState();
        if (state != GameState.LIVE) {
            if (state == GameState.IDLE) baselines.clear();
            return;
        }

        int pattern = plugin.getGameManager().getPatternIndex();
        if (pattern < 0) return;

        for (Player player : plugin.getGameManager().getAlivePlayers()) {
            KitType kit = plugin.getGameManager().getKitManager().getKit(player);
            if (kit == null) continue;
            UUID uuid = player.getUniqueId();
            Baseline existing = baselines.get(uuid);
            if (existing == null || existing.pattern != pattern || !existing.kit.equals(kit.id)) {
                int previous = plugin.getLeaderboards().getKitPB(
                        plugin.getMode(), pattern, uuid, kit.id);
                baselines.put(uuid, new Baseline(pattern, kit.id, previous));
            }
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
                Baseline baseline = baselines.remove(player.getUniqueId());
                if (baseline == null) return;

                int stage = plugin.getGameManager().getStage();
                if (stage <= baseline.previousPB) return;

                long liveStart = plugin.getGameManager().getGameLiveTime();
                long elapsed = liveStart > 0 ? Math.max(0L, System.currentTimeMillis() - liveStart) : 0L;
                plugin.getRunRecorder().record(player, plugin.getMode(), baseline.pattern,
                        baseline.kit, stage, elapsed);
            }
        });
    }

    public void shutdown() {
        task.cancel();
        baselines.clear();
    }

    private static final class Baseline {
        final int pattern;
        final String kit;
        final int previousPB;

        Baseline(int pattern, String kit, int previousPB) {
            this.pattern = pattern;
            this.kit = kit;
            this.previousPB = previousPB;
        }
    }
}
