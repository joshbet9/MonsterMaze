package me.monstermaze.world;

import me.monstermaze.MonsterMazePlugin;
import me.monstermaze.maze.MazeBlockData;
import org.bukkit.Bukkit;
import org.bukkit.GameRule;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Release-controlled Monster Maze map definitions for the 1.21 implementation.
 * User-selected map and mob overrides remain in config.yml; map definitions live
 * in the bundled maps.yml so plugin releases can update them safely.
 */
public final class MapManager {

    private static final String DEFAULT_MAP = "eyeofender";

    private final MonsterMazePlugin plugin;
    private final FileConfiguration maps;
    private String activeMap;

    public MapManager(MonsterMazePlugin plugin) {
        this.plugin = plugin;
        this.maps = loadBundledMaps();
        loadActiveMapFromConfig();
    }

    private FileConfiguration loadBundledMaps() {
        InputStream resource = plugin.getResource("maps.yml");
        if (resource == null) {
            plugin.getLogger().severe("maps.yml is missing from the 1.21 plugin JAR.");
            return new YamlConfiguration();
        }

        try (InputStreamReader reader = new InputStreamReader(resource, StandardCharsets.UTF_8)) {
            FileConfiguration cfg = YamlConfiguration.loadConfiguration(reader);
            plugin.getLogger().info("Loaded bundled 1.21 map definitions from maps.yml.");
            return cfg;
        } catch (Exception ex) {
            plugin.getLogger().severe("Could not load bundled maps.yml: " + ex.getMessage());
            return new YamlConfiguration();
        }
    }

    public void loadActiveMapFromConfig() {
        String configured = plugin.getConfig().getString("map", DEFAULT_MAP);
        activeMap = isKnown(configured) ? configured.toLowerCase() : DEFAULT_MAP;
        plugin.getConfig().set("map", activeMap);
        plugin.saveConfig();
    }

    public String getActiveMap() {
        return activeMap == null ? DEFAULT_MAP : activeMap;
    }

    public List<String> knownMaps() {
        Set<String> names = new LinkedHashSet<String>(maps.getKeys(false));
        names.remove("void");
        return new ArrayList<String>(names);
    }

    public boolean isKnown(String map) {
        return map != null && maps.isConfigurationSection(map.toLowerCase());
    }

    public boolean setActiveMap(String map) {
        if (!isKnown(map)) return false;
        activeMap = map.toLowerCase();
        plugin.getConfig().set("map", activeMap);
        plugin.saveConfig();
        return true;
    }

    private ConfigurationSection section(String map) {
        return maps.getConfigurationSection(map == null ? "" : map.toLowerCase());
    }

    /** Load the configured arena world, using the existing mm_void world for Eye of Ender. */
    public World ensureActiveWorld() {
        ConfigurationSection section = section(getActiveMap());
        if (section == null) return null;

        String folder = section.getString("world-folder", "mm_void");
        World existing = Bukkit.getWorld(folder);
        if (existing != null) return existing;

        World world = new WorldCreator(folder)
                .generateStructures(false)
                .createWorld();

        if (world != null) {
            world.setGameRule(GameRule.DO_MOB_SPAWNING, false);
            world.setGameRule(GameRule.DO_DAYLIGHT_CYCLE, false);
            world.setGameRule(GameRule.DO_WEATHER_CYCLE, false);
            world.setGameRule(GameRule.KEEP_INVENTORY, true);
            world.setTime(6000L);
            world.setStorm(false);
            world.setThundering(false);
            plugin.getLogger().info("Loaded Monster Maze arena world '" + folder + "'.");
        } else {
            plugin.getLogger().severe("Failed to load Monster Maze arena world '" + folder + "'.");
        }
        return world;
    }

    public Location defaultCenter() {
        return defaultCenter(getActiveMap());
    }

    public Location defaultCenter(String map) {
        ConfigurationSection section = section(map);
        if (section == null) return null;

        World world;
        if ("mm_void".equalsIgnoreCase(section.getString("world-folder", ""))) {
            world = plugin.getVoidWorlds().ensureWorld();
        } else {
            world = ensureWorld(section.getString("world-folder", ""));
        }
        if (world == null) return null;

        ConfigurationSection center = section.getConfigurationSection("center");
        if (center == null) return new Location(world, 0.5, 64, 0.5);

        return new Location(
                world,
                center.getInt("x", 0) + 0.5,
                center.getInt("y", 64),
                center.getInt("z", 0) + 0.5
        );
    }

    private World ensureWorld(String folder) {
        if (folder == null || folder.trim().isEmpty()) return null;
        World existing = Bukkit.getWorld(folder);
        if (existing != null) return existing;
        return new WorldCreator(folder).generateStructures(false).createWorld();
    }

    public MazeBlockData activeTheme() {
        return theme(getActiveMap());
    }

    public MazeBlockData theme(String map) {
        ConfigurationSection section = section(map);
        MazeBlockData fallback = MazeBlockData.defaultTheme();
        if (section == null) return fallback;

        return new MazeBlockData(
                material(section.getString("top", ""), fallback.top),
                material(section.getString("mid", ""), fallback.middle),
                material(section.getString("bottom", ""), fallback.bottom)
        );
    }

    private Material material(String name, Material fallback) {
        if (name == null || name.trim().isEmpty()) return fallback;
        Material material = Material.matchMaterial(name.trim());
        return material == null ? fallback : material;
    }

    /**
     * Returns the configured mob skin id. The 1.21 entity/disguise implementation
     * is intentionally handled separately because the 1.8 NMS disguise mechanism
     * cannot be copied directly to modern Paper.
     */
    public String activeMob() {
        ConfigurationSection section = section(getActiveMap());
        if (section == null) return "snowman";
        return section.getString("mob", "snowman").toLowerCase();
    }
}
