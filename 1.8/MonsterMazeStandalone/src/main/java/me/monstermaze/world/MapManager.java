package me.monstermaze.world;

import me.monstermaze.MonsterMazePlugin;
import me.monstermaze.maze.MazeBlockData;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Manages the arena map (the world a Monster Maze game runs in) and its per-map
 * theme settings — maze palette, mob type and the default center anchor.
 *
 * <p>Two kinds of worlds are supported:
 * <ul>
 *   <li>{@code void} — the procedurally-generated empty world used by original
 *       releases (maze floats in open air).</li>
 *   <li>a named {@code world-folder} — a shipped Mineplex-style arena world
 *       folder (e.g. {@code mm_volcano}) loaded as-is; the maze is generated on
 *       top of it.</li>
 * </ul>
 *
 * <p>Per-map settings live in {@code config.yml} (protected from the updater).
 * The maze palette mirrors Mineplex {@code B1/B2/B3} (top/middle/bottom). This
 * class is read-only on config value; the active map is switchable via
 * {@code /mm map <name>} and persisted.
 */
public class MapManager {

    private static final String DEFAULT_MAP = "void";

    private final MonsterMazePlugin plugin;
    private final VoidWorldManager voidWorlds;
    private String activeMap;

    public MapManager(MonsterMazePlugin plugin, VoidWorldManager voidWorlds) {
        this.plugin = plugin;
        this.voidWorlds = voidWorlds;
    }

    // -------------------- Active map --------------------

    /** Reload the active map key from config (defaults to "void"). */
    public void loadActiveMapFromConfig() {
        String map = plugin.getConfig().getString("map", DEFAULT_MAP);
        if (!isKnown(map)) map = DEFAULT_MAP;
        this.activeMap = map;
    }

    public String getActiveMap() {
        return activeMap == null ? DEFAULT_MAP : activeMap;
    }

    public List<String> knownMaps() {
        ConfigurationSection maps = plugin.getConfig().getConfigurationSection("maps");
        if (maps == null) return new ArrayList<String>();
        return new ArrayList<String>(maps.getKeys(false));
    }

    public boolean isKnown(String map) {
        if (map == null) return false;
        ConfigurationSection maps = plugin.getConfig().getConfigurationSection("maps");
        return maps != null && maps.contains(map);
    }

    /** Set the active map, persisting to config. Returns false if unknown. */
    public boolean setActiveMap(String map) {
        if (!isKnown(map)) return false;
        this.activeMap = map;
        plugin.getConfig().set("map", map);
        plugin.saveConfig();
        return true;
    }

    private ConfigurationSection section(String map) {
        ConfigurationSection maps = plugin.getConfig().getConfigurationSection("maps");
        if (maps == null) return null;
        return maps.getConfigurationSection(map);
    }

    // -------------------- World --------------------

    /** The world the active map should be played in; null if it failed to load. */
    public World ensureActiveWorld() {
        String map = getActiveMap();
        if (map.equals("void")) {
            return voidWorlds.ensureWorld();
        }
        String folder = worldFolder(map);
        if (folder == null) {
            return null;
        }
        return ensureMapWorld(folder);
    }

    private String worldFolder(String map) {
        ConfigurationSection s = section(map);
        if (s == null) return null;
        String folder = s.getString("world-folder");
        return (folder == null || folder.isEmpty()) ? null : folder;
    }

    /** Load an existing world folder (shipped arena world) as-is; creates it if absent. */
    private World ensureMapWorld(String folder) {
        World existing = Bukkit.getWorld(folder);
        if (existing != null) {
            return existing;
        }
        WorldCreator creator = new WorldCreator(folder);
        creator.generateStructures(false);
        creator.environment(World.Environment.NORMAL);
        try {
            World world = creator.createWorld();
            if (world != null) {
                world.setGameRuleValue("doMobSpawning", "false");
                world.setGameRuleValue("doDaylightCycle", "false");
                world.setGameRuleValue("doWeatherCycle", "false");
                world.setGameRuleValue("keepInventory", "true");
                world.setTime(6000);
                world.setStorm(false);
                world.setThundering(false);
                world.setSpawnFlags(false, false);
                // Arena worlds must never spawn mobs naturally (the only monsters are the
                // plugin's ghost maze mobs). Archived maps ship with saved entities (e.g.
                // zombie pigmen baked into the chunks on the live Mineplex server), so zero
                // the spawn limits and clear any that already loaded.
                world.setMonsterSpawnLimit(0);
                world.setAnimalSpawnLimit(0);
                world.setAmbientSpawnLimit(0);
                world.setWaterAnimalSpawnLimit(0);
                clearMobs(world);
                plugin.getLogger().info("Loaded arena world '" + folder + "'.");
            } else {
                plugin.getLogger().severe("Failed to load arena world '" + folder + "'.");
            }
            return world;
        } catch (Exception ex) {
            plugin.getLogger().severe("Could not load arena world '" + folder + "': " + ex.getMessage());
            return null;
        }
    }

    /**
     * Remove every entity from an arena world except online players. Archived map
     * worlds ship with entities baked into their chunks (e.g. zombie pigmen saved on
     * the live Mineplex server), and lazily-loaded chunks can surface more long after
     * the world first loads, so this is called both at world load and before each game.
     */
    public void clearMobs(World world) {
        if (world == null) return;
        int removed = 0;
        for (Entity e : world.getEntities()) {
            if (e instanceof Player) continue;
            e.remove();
            removed++;
        }
        if (removed > 0) {
            plugin.getLogger().info("Cleared " + removed + " lingering entities from arena world '" + world.getName() + "'.");
        }
    }

    /** The default center (maze anchor) for the active map, or null if unset. */
    public Location defaultCenter() {
        return defaultCenter(getActiveMap());
    }

    public Location defaultCenter(String map) {
        ConfigurationSection s = section(map);
        World world;
        if (map.equals("void")) {
            world = voidWorlds.ensureWorld();
        } else {
            String folder = worldFolder(map);
            world = folder == null ? null : ensureMapWorld(folder);
        }
        if (world == null) return null;
        if (s != null && s.contains("center")) {
            ConfigurationSection c = s.getConfigurationSection("center");
            int x = c.getInt("x", 0);
            int y = c.getInt("y", 64);
            int z = c.getInt("z", 0);
            return new Location(world, x + 0.5, y, z + 0.5);
        }
        // Void default: original spawn platform
        return new Location(world, 0.5, 64, 0.5);
    }

    // -------------------- Maze palette --------------------
    /** Per-map maze palette, falling back to the original quartz theme. */
    public MazeBlockData theme(String map) {
        MazeBlockData fallback = MazeBlockData.defaultTheme();
        ConfigurationSection s = section(map);
        if (s == null) return fallback;
        Material top = mat(s.getConfigurationSection("top"));
        Material mid = mat(s.getConfigurationSection("mid"));
        Material bottom = mat(s.getConfigurationSection("bottom"));
        return MazeBlockData.from(
                top, s.getConfigurationSection("top") != null ? (byte) s.getInt("top.data", 0) : -1,
                mid, s.getConfigurationSection("mid") != null ? (byte) s.getInt("mid.data", 0) : -1,
                bottom, s.getConfigurationSection("bottom") != null ? (byte) s.getInt("bottom.data", 0) : -1,
                fallback);
    }

    public MazeBlockData activeTheme() {
        return theme(getActiveMap());
    }

    private Material mat(ConfigurationSection s) {
        if (s == null) return null;
        int id = s.getInt("id", -1);
        return id >= 0 ? Material.getMaterial(id) : null;
    }

    // -------------------- Mob --------------------

    /** Per-map mob entity type name (default "snowman"). Phase 1: only snowman is implemented. */
    public String mob(String map) {
        ConfigurationSection s = section(map);
        if (s != null && s.isString("mob")) {
            return s.getString("mob");
        }
        return "snowman";
    }

    public String activeMob() {
        return mob(getActiveMap());
    }

    public Set<String> mobTypes() {
        Set<String> out = new LinkedHashSet<String>();
        for (String m : knownMaps()) out.add(mob(m));
        return out;
    }
}
