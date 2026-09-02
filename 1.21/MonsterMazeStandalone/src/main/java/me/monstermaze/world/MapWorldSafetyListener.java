package me.monstermaze.world;

import me.monstermaze.MonsterMazePlugin;
import me.monstermaze.game.GameState;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Difficulty;
import org.bukkit.GameRule;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityExhaustionEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.world.WorldLoadEvent;
import org.bukkit.scheduler.BukkitTask;

/** Keeps Monster Maze map worlds free of vanilla creatures and hunger/exhaustion. */
public final class MapWorldSafetyListener implements Listener {
    private static final String MONSTER_METADATA = "monstermaze_mob";
    private static final String NPC_METADATA = "monstermaze_npc";

    private final MonsterMazePlugin plugin;
    private final BukkitTask enforcementTask;

    public MapWorldSafetyListener(MonsterMazePlugin plugin) {
        this.plugin = plugin;
        Bukkit.getPluginManager().registerEvents(this, plugin);
        this.enforcementTask = Bukkit.getScheduler().runTaskTimer(plugin, new Runnable() {
            @Override public void run() {
                for (World world : Bukkit.getWorlds()) {
                    if (!isManagedWorld(world)) continue;
                    configureWorld(world);
                    purgeLoadedChunks(world);
                }
                for (Player player : Bukkit.getOnlinePlayers()) {
                    player.setFoodLevel(20);
                    player.setSaturation(20f);
                }
            }
        }, 1L, 10L);
    }

    public void shutdown() {
        enforcementTask.cancel();
    }

    private boolean isManagedWorld(World world) {
        return world != null && world.getName().startsWith("mm_");
    }

    private void configureWorld(World world) {
        world.setSpawnFlags(false, false);
        world.setGameRule(GameRule.DO_MOB_SPAWNING, false);
        world.setGameRule(GameRule.DO_DAYLIGHT_CYCLE, false);
        world.setGameRule(GameRule.DO_WEATHER_CYCLE, false);
        world.setStorm(false);
        world.setThundering(false);
        world.setTime(6000L);

        // Converted 1.8 map worlds can contain saved hostile entities. Keep every map peaceful
        // while it is a lobby/world-selection area so those entities are removed by the engine
        // before they can repopulate the map. Monster Maze switches to NORMAL automatically once
        // a game enters STARTING/LIVE, where its own CUSTOM entities are spawned.
        GameState state = plugin.getGameManager() == null
                ? null : plugin.getGameManager().getState();
        world.setDifficulty(state == GameState.STARTING || state == GameState.LIVE
                ? Difficulty.NORMAL : Difficulty.PEACEFUL);
    }

    private void purgeLoadedChunks(World world) {
        for (Chunk chunk : world.getLoadedChunks()) purgeChunk(chunk);
    }

    private void purgeChunk(Chunk chunk) {
        if (chunk == null) return;
        for (Entity entity : chunk.getEntities()) {
            if (!(entity instanceof LivingEntity) || entity instanceof Player) continue;
            if (entity.hasMetadata(MONSTER_METADATA) || entity.hasMetadata(NPC_METADATA)) continue;
            entity.remove();
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onWorldLoad(WorldLoadEvent event) {
        if (!isManagedWorld(event.getWorld())) return;
        configureWorld(event.getWorld());
        purgeLoadedChunks(event.getWorld());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onChunkLoad(ChunkLoadEvent event) {
        if (!isManagedWorld(event.getWorld())) return;
        configureWorld(event.getWorld());
        purgeChunk(event.getChunk());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onCreatureSpawn(CreatureSpawnEvent event) {
        if (!isManagedWorld(event.getLocation().getWorld())) return;
        if (event.getSpawnReason() == CreatureSpawnEvent.SpawnReason.CUSTOM) return;
        event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onFood(FoodLevelChangeEvent event) {
        if (!(event.getEntity() instanceof Player)) return;
        event.setCancelled(true);
        Player player = (Player) event.getEntity();
        player.setFoodLevel(20);
        player.setSaturation(20f);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onExhaustion(EntityExhaustionEvent event) {
        if (event.getEntity() instanceof Player) event.setCancelled(true);
    }
}
