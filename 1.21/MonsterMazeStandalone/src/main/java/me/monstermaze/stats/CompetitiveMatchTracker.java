package me.monstermaze.stats;

import me.monstermaze.MonsterMazePlugin;
import me.monstermaze.game.GameState;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Captures immutable multiplayer results independently of solo PB recording. */
public final class CompetitiveMatchTracker implements Listener {
    private final MonsterMazePlugin plugin;
    private final Map<UUID, Integer> eliminationTicks = new HashMap<UUID, Integer>();
    private final Map<UUID, String> names = new HashMap<UUID, String>();
    private final Set<UUID> participants = new HashSet<UUID>();
    private String matchId;
    private long startedAt;
    private int tick;
    private boolean active;
    private BukkitTask tickTask;

    public CompetitiveMatchTracker(MonsterMazePlugin plugin) {
        this.plugin = plugin;
        Bukkit.getPluginManager().registerEvents(this, plugin);
        tickTask = Bukkit.getScheduler().runTaskTimer(plugin, new Runnable() { @Override public void run() { tick(); } }, 1L, 1L);
    }

    private void tick() {
        GameState state = plugin.getGameManager() == null ? GameState.IDLE : plugin.getGameManager().getState();
        if (!active && state == GameState.LIVE) {
            List<Player> players = plugin.getGameManager().getAlivePlayers();
            if (players.size() >= 2) begin(players);
        }
        if (active) tick++;
    }

    private void begin(List<Player> players) {
        participants.clear(); eliminationTicks.clear(); names.clear();
        for (Player p : players) { participants.add(p.getUniqueId()); names.put(p.getUniqueId(), p.getName()); }
        matchId = UUID.randomUUID().toString(); startedAt = System.currentTimeMillis(); tick = 0; active = true;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onDeath(PlayerDeathEvent event) {
        if (!active) return;
        UUID id = event.getEntity().getUniqueId();
        if (participants.contains(id) && !eliminationTicks.containsKey(id)) eliminationTicks.put(id, tick);
        scheduleFinishCheck();
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onQuit(PlayerQuitEvent event) {
        if (!active) return;
        UUID id = event.getPlayer().getUniqueId();
        if (participants.contains(id) && !eliminationTicks.containsKey(id)) eliminationTicks.put(id, tick);
        scheduleFinishCheck();
    }

    private void scheduleFinishCheck() {
        Bukkit.getScheduler().runTask(plugin, new Runnable() { @Override public void run() { if (active && eliminationTicks.size() >= participants.size() - 1) finish(); } });
    }

    private void finish() {
        if (!active) return;
        active = false;
        List<Result> eliminated = new ArrayList<Result>();
        for (UUID id : participants) { Integer t = eliminationTicks.get(id); if (t != null) eliminated.add(new Result(id, names.get(id), t)); }
        int survivors = participants.size() - eliminated.size();
        List<Map<String,Object>> rows = new ArrayList<Map<String,Object>>();
        for (Result r : eliminated) {
            int later = 0;
            for (Result other : eliminated) if (other.tick > r.tick) later++;
            int placement = survivors > 0 ? 2 + later : 1 + later;
            Map<String,Object> row = new HashMap<String,Object>();
            row.put("uuid",r.uuid.toString()); row.put("name",r.name); row.put("placement",placement); row.put("eliminationTick",r.tick); rows.add(row);
        }
        if (survivors > 0) for (UUID id : participants) if (!eliminationTicks.containsKey(id)) {
            Map<String,Object> row = new HashMap<String,Object>(); row.put("uuid",id.toString()); row.put("name",names.get(id)); row.put("placement",1); row.put("eliminationTick",-1); rows.add(row);
        }
        int pattern = plugin.getGameManager().getPatternIndex();
        if (pattern < 0) pattern = 0;
        plugin.getBackendClient().submitMatch(matchId,"1.21",plugin.getMode().id,pattern,rows,startedAt,System.currentTimeMillis());
    }

    public void shutdown() { if (tickTask != null) { tickTask.cancel(); tickTask = null; } }

    private static final class Result {
        final UUID uuid; final String name; final int tick;
        Result(UUID uuid,String name,int tick) { this.uuid=uuid; this.name=name; this.tick=tick; }
    }
}
