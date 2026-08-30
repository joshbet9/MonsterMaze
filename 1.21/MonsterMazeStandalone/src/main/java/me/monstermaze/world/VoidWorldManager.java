package me.monstermaze.world;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.WorldType;
import org.bukkit.entity.Player;

/**
 * Creates and loads a dedicated void world for Monster Maze.
 */
public class VoidWorldManager {

    public static final String WORLD_NAME = "mm_void";

    private final me.monstermaze.MonsterMazePlugin plugin;

    public VoidWorldManager(me.monstermaze.MonsterMazePlugin plugin) {
        this.plugin = plugin;
    }

    /** Create or load the void world. Safe to call multiple times. */
    public World ensureWorld() {
        World existing = Bukkit.getWorld(WORLD_NAME);
        if (existing != null) {
            return existing;
        }

        plugin.getLogger().info("Creating void world '" + WORLD_NAME + "'...");

        WorldCreator creator = new WorldCreator(WORLD_NAME);
        creator.type(WorldType.FLAT);
        creator.generator(new VoidChunkGenerator());
        creator.generateStructures(false);
        creator.environment(World.Environment.NORMAL);

        World world = creator.createWorld();
        if (world != null) {
            world.setSpawnLocation(0, 64, 0);
            world.setGameRule(org.bukkit.GameRule.DO_MOB_SPAWNING, false);
            world.setGameRule(org.bukkit.GameRule.DO_DAYLIGHT_CYCLE, false);
            world.setGameRule(org.bukkit.GameRule.DO_WEATHER_CYCLE, false);
            world.setGameRule(org.bukkit.GameRule.KEEP_INVENTORY, true);
            world.setTime(6000);
            world.setStorm(false);
            world.setThundering(false);
            plugin.getLogger().info("Void world ready: " + WORLD_NAME);
        } else {
            plugin.getLogger().severe("Failed to create void world!");
        }
        return world;
    }

    public Location lobbySpawn() {
        World w = ensureWorld();
        if (w == null) return null;
        return new Location(w, 0.5, 64, 0.5);
    }

    public void sendToVoid(Player player) {
        Location loc = lobbySpawn();
        if (loc == null) {
            player.sendMessage(org.bukkit.ChatColor.RED + "Could not create void world.");
            return;
        }
        player.teleport(loc);
        player.sendMessage(org.bukkit.ChatColor.GREEN + "Teleported to Monster Maze void world.");
        player.sendMessage(org.bukkit.ChatColor.GRAY + "Run " + org.bukkit.ChatColor.YELLOW + "/mm setcenter"
                + org.bukkit.ChatColor.GRAY + " then " + org.bukkit.ChatColor.YELLOW + "/mm start");
    }
}