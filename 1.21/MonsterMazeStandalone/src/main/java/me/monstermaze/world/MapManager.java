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

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Release-controlled Monster Maze map definitions for the 1.21 implementation. */
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
            return YamlConfiguration.loadConfiguration(reader);
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

    public String getActiveMap() { return activeMap == null ? DEFAULT_MAP : activeMap; }

    public List<String> knownMaps() {
        Set<String> names = new LinkedHashSet<String>(maps.getKeys(false));
        names.remove("void");
        return new ArrayList<String>(names);
    }

    public boolean isKnown(String map) {
        return map != null && maps.isConfigurationSection(map.toLowerCase());
    }

    /**
     * Returns whether the map's physical world is installed in the server world container.
     * Every 1.21 Solo map, including mm_void, is shipped as a real world asset.
     */
    public boolean isAvailable(String map) {
        ConfigurationSection section = section(map);
        if (section == null) return false;
        String folder = section.getString("world-folder", "mm_void");
        if (Bukkit.getWorld(folder) != null) return true;
        File worldFolder = new File(Bukkit.getWorldContainer(), folder);
        return worldFolder.isDirectory() && new File(worldFolder, "level.dat").isFile();
    }

    /**
     * Select an installed map. Missing physical map worlds are rejected instead of being
     * silently generated as fresh test worlds, which previously caused the wrong maze to load.
     */
    public boolean setActiveMap(String map) {
        if (!isKnown(map)) return false;
        String normalized = map.toLowerCase();
        if (!isAvailable(normalized)) {
            plugin.getLogger().warning("Map '" + normalized + "' is not installed; refusing to activate it.");
            return false;
        }
        activeMap = normalized;
        plugin.getConfig().set("map", activeMap);
        plugin.saveConfig();
        return true;
    }

    private ConfigurationSection section(String map) {
        return maps.getConfigurationSection(map == null ? "" : map.toLowerCase());
    }

    public World ensureActiveWorld() {
        ConfigurationSection section = section(getActiveMap());
        if (section == null) return null;
        String folder = section.getString("world-folder", "mm_void");
        return ensureWorld(folder);
    }

    private World ensureWorld(String folder) {
        if (folder == null || folder.trim().isEmpty()) return null;
        World existing = Bukkit.getWorld(folder);
        if (existing != null) return existing;

        File worldFolder = new File(Bukkit.getWorldContainer(), folder);
        if (!worldFolder.isDirectory() || !new File(worldFolder, "level.dat").isFile()) {
            plugin.getLogger().warning("Map world folder is missing: " + worldFolder.getAbsolutePath());
            return null;
        }

        World world = new WorldCreator(folder).generateStructures(false).createWorld();
        if (world != null) {
            world.setGameRule(GameRule.DO_MOB_SPAWNING, false);
            world.setGameRule(GameRule.DO_DAYLIGHT_CYCLE, false);
            world.setGameRule(GameRule.DO_WEATHER_CYCLE, false);
            world.setGameRule(GameRule.KEEP_INVENTORY, true);
            world.setTime(6000L);
            world.setStorm(false);
            world.setThundering(false);
            clearMobs(world);
        }
        return world;
    }

    public void clearMobs(World world) {
        if (world == null) return;
        for (org.bukkit.entity.Entity entity : world.getEntities()) {
            if (!(entity instanceof org.bukkit.entity.Player)) entity.remove();
        }
    }

    public Location defaultCenter() { return defaultCenter(getActiveMap()); }

    public Location defaultCenter(String map) {
        ConfigurationSection section = section(map);
        if (section == null) return null;
        String folder = section.getString("world-folder", "mm_void");
        World world = ensureWorld(folder);
        if (world == null) return null;
        ConfigurationSection center = section.getConfigurationSection("center");
        if (center == null) return new Location(world, 0.5, 64, 0.5);
        return new Location(world, center.getInt("x", 0) + 0.5,
                center.getInt("y", 64), center.getInt("z", 0) + 0.5);
    }

    public MazeBlockData activeTheme() { return theme(getActiveMap()); }

    public MazeBlockData theme(String map) {
        ConfigurationSection section = section(map);
        MazeBlockData fallback = MazeBlockData.defaultTheme();
        if (section == null) return fallback;
        return new MazeBlockData(
                material(section.getString("top", ""), fallback.top),
                material(section.getString("mid", ""), fallback.middle),
                material(section.getString("bottom", ""), fallback.bottom));
    }

    private Material material(String name, Material fallback) {
        if (name == null || name.trim().isEmpty()) return fallback;
        Material material = Material.matchMaterial(name.trim());
        return material == null ? fallback : material;
    }

    public String activeMob() { return selectedMob(getActiveMap()); }

    public String mob(String map) {
        ConfigurationSection section = section(map);
        if (section == null) return "snowman";
        String configured = section.getString("mob", "snowman");
        return configured == null || configured.trim().isEmpty()
                ? "snowman" : configured.trim().toLowerCase();
    }

    public String selectedMob(String map) {
        ConfigurationSection overrides = plugin.getConfig().getConfigurationSection("mob-overrides");
        String override = overrides == null ? "" : overrides.getString(map, "");
        return override == null || override.trim().isEmpty()
                ? mob(map) : override.trim().toLowerCase();
    }

    public void setMobOverride(String map, String mobType) {
        if (!isKnown(map)) return;
        ConfigurationSection overrides = plugin.getConfig().getConfigurationSection("mob-overrides");
        if (overrides == null) overrides = plugin.getConfig().createSection("mob-overrides");
        if (mobType == null || mobType.trim().isEmpty()) overrides.set(map, null);
        else overrides.set(map, mobType.trim().toLowerCase());
        plugin.saveConfig();
    }

    public void setActiveMobOverride(String mobType) { setMobOverride(getActiveMap(), mobType); }

    public boolean hasMobOverride(String map) {
        ConfigurationSection overrides = plugin.getConfig().getConfigurationSection("mob-overrides");
        String value = overrides == null ? "" : overrides.getString(map, "");
        return value != null && !value.trim().isEmpty();
    }
}
