package me.monstermaze.stats;

import me.monstermaze.MonsterMazePlugin;
import me.monstermaze.game.GameState;
import me.monstermaze.kit.KitType;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scheduler.BukkitTask;

/** Records every completed solo attempt; PB attempts are already emitted by GameManager. */
public final class SoloRunCompletionListener implements Listener {
    private final MonsterMazePlugin plugin;
    private final BukkitTask task;
    private Player soloPlayer;
    private int pattern = -1;
    private String kit;
    private int previousPB;
    private boolean soloGame;
    private boolean recorded;

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
            java.util.List<Player> alive = plugin.getGameManager().getAlivePlayers();
            if (soloPlayer == null && !soloGame) {
                if (alive.size() == 1) {
                    soloGame = true;
                    soloPlayer = alive.get(0);
                    pattern = plugin.getGameManager().getPatternIndex();
                    KitType selected = plugin.getGameManager().getKitManager().getKit(soloPlayer);
                    kit = selected == null ? null : selected.id;
                    previousPB = kit == null ? 0 : plugin.getLeaderboards().getKitPB(plugin.getMode(), pattern, soloPlayer.getUniqueId(), kit);
                } else if (alive.size() > 1) {
                    soloGame = false;
                }
            }
            if (soloGame && soloPlayer != null && alive.isEmpty()) recordOnce();
        } else if (state == GameState.ENDING && soloGame && soloPlayer != null) {
            recordOnce();
        } else if (state == GameState.IDLE) {
            reset();
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        if (!soloGame || soloPlayer == null || !soloPlayer.getUniqueId().equals(event.getPlayer().getUniqueId())) return;
        record(event.getPlayer());
    }

    private void recordOnce() {
        if (recorded || !soloGame || soloPlayer == null || pattern < 0 || kit == null) return;
        record(soloPlayer);
    }

    private void record(Player player) {
        if (recorded || player == null || pattern < 0 || kit == null) return;
        int stage = plugin.getGameManager().getStage();
        if (stage > previousPB) {
            // GameManager already wrote the PB attempt to solo-runs/. Do not duplicate it.
            recorded = true;
            return;
        }
        recorded = true;
        long liveStart = plugin.getGameManager().getGameLiveTime();
        long elapsed = liveStart > 0 ? Math.max(0L, System.currentTimeMillis() - liveStart) : 0L;
        plugin.getRunRecorder().record(player, plugin.getMode(), pattern, kit, stage, elapsed);
    }

    private void reset() {
        soloPlayer = null;
        pattern = -1;
        kit = null;
        previousPB = 0;
        soloGame = false;
        recorded = false;
    }

    public void shutdown() {
        task.cancel();
        reset();
    }
}
