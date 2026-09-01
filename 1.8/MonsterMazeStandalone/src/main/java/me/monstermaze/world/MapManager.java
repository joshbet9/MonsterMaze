package me.monstermaze.world;

import me.monstermaze.MonsterMazePlugin;
import me.monstermaze.maze.MazeBlockData;
import me.monstermaze.util.MobTypes;
import me.monstermaze.util.UtilEnt;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

/** Manages the active Monster Maze arena map and its release-controlled theme settings. */
public class MapManager {
    private static final String DEFAULT_MAP = "eyeofender";
    private static final Random RANDOM = new Random();
    private final MonsterMazePlugin plugin;
    private final VoidWorldManager voidWorlds;
    private final FileConfiguration mapConfig;
    private String activeMap;

    public MapManager(MonsterMazePlugin plugin, VoidWorldManager voidWorlds) {
        this.plugin = plugin;
        this.voidWorlds = voidWorlds;
        this.mapConfig = loadBundledMapConfig();
    }

    private FileConfiguration loadBundledMapConfig() {
        InputStream resource = plugin.getResource("maps.yml");
        if (resource != null) {
            try {
                InputStreamReader reader = new InputStreamReader(resource, StandardCharsets.UTF_8);
                FileConfiguration config = YamlConfiguration.loadConfiguration(reader);
                reader.close();
                plugin.getLogger().info("Loaded bundled map definitions from maps.yml.");
                return config;
            } catch (Exception ex) {
                plugin.getLogger().severe("Could not load bundled maps.yml: " + ex.getMessage());
            }
        } else {
            plugin.getLogger().severe("maps.yml is missing from the plugin JAR.");
        }

        // Compatibility fallback for an older/malformed plugin build: use legacy
        // config map definitions if they are present, otherwise the map list is empty.
        ConfigurationSection legacy = plugin.getConfig().getConfigurationSection("maps");
        if (legacy != null) {
            plugin.getLogger().warning("Falling back to legacy config.yml map definitions.");
            FileConfiguration fallback = new YamlConfiguration();
            fallback.set("maps", legacy.getValues(false));
            return fallback;
        }
        return new YamlConfiguration();
    }

    public void loadActiveMapFromConfig() {
        migrateLegacyMapSettings();
        String map = plugin.getConfig().getString("map", DEFAULT_MAP);
        if ("void".equalsIgnoreCase(map)) map = DEFAULT_MAP;
        if (!isKnown(map)) map = DEFAULT_MAP;
        activeMap = map;
        plugin.getConfig().set("map", map);
        plugin.saveConfig();
    }

    /**
     * Older Solo installs stored map definitions and mob overrides inside config.yml.
     * Keep the user's active map and overrides, then remove the release-controlled maps
     * section so future plugin releases can update maps.yml without touching user data.
     */
    private void migrateLegacyMapSettings() {
        ConfigurationSection legacyMaps = plugin.getConfig().getConfigurationSection("maps");
        if (legacyMaps == null) return;

        ConfigurationSection overrides = plugin.getConfig().getConfigurationSection("mob-overrides");
        if (overrides == null) overrides = plugin.getConfig().createSection("mob-overrides");

        for (String map : legacyMaps.getKeys(false)) {
            ConfigurationSection legacyMap = legacyMaps.getConfigurationSection(map);
            if (legacyMap == null) continue;
            String override = legacyMap.getString("mob-override", "");
            if (override != null && !override.trim().isEmpty() && !overrides.contains(map)) {
                overrides.set(map, override);
            }
        }

        plugin.getConfig().set("maps", null);
        plugin.saveConfig();
        plugin.getLogger().info("Migrated legacy map definitions from config.yml to bundled maps.yml.");
    }

    public String getActiveMap() { return activeMap == null ? DEFAULT_MAP : activeMap; }

    public List<String> knownMaps() {
        Set<String> names = new LinkedHashSet<String>(mapConfig.getKeys(false));
        names.remove("void");
        if (!names.contains(DEFAULT_MAP)) names.add(DEFAULT_MAP);
        return new ArrayList<String>(names);
    }

    public boolean isKnown(String map) {
        return map != null && mapConfig.contains(map);
    }

    public boolean setActiveMap(String map) {
        if (!isKnown(map)) return false;
        activeMap = map;
        plugin.getConfig().set("map", map);
        plugin.saveConfig();
        return true;
    }

    private ConfigurationSection section(String map) {
        return mapConfig.getConfigurationSection(map);
    }

    public World ensureActiveWorld() {
        String map = getActiveMap();
        if (map.equalsIgnoreCase(DEFAULT_MAP)) return voidWorlds.ensureWorld();
        String folder = worldFolder(map);
        return folder == null ? null : ensureMapWorld(folder);
    }

    private String worldFolder(String map) {
        ConfigurationSection s = section(map);
        if (s == null) return null;
        String folder = s.getString("world-folder");
        return folder == null || folder.isEmpty() ? null : folder;
    }

    private World ensureMapWorld(String folder) {
        World existing = Bukkit.getWorld(folder);
        if (existing != null) return existing;
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

    public void clearMobs(World world) {
        if (world == null) return;
        int removed = 0;
        for (Entity e : world.getEntities()) {
            if (e instanceof Player) continue;
            e.remove();
            removed++;
        }
        if (removed > 0) plugin.getLogger().info("Cleared " + removed + " lingering entities from arena world '" + world.getName() + "'.");
    }

    public Location defaultCenter() { return defaultCenter(getActiveMap()); }

    public Location defaultCenter(String map) {
        ConfigurationSection s = section(map);
        World world;
        if (map.equalsIgnoreCase(DEFAULT_MAP)) {
            world = voidWorlds.ensureWorld();
        } else {
            String folder = worldFolder(map);
            world = folder == null ? null : ensureMapWorld(folder);
        }
        if (world == null) return null;
        if (s != null && s.contains("center")) {
            ConfigurationSection c = s.getConfigurationSection("center");
            return new Location(world, c.getInt("x", 0) + 0.5, c.getInt("y", 64), c.getInt("z", 0) + 0.5);
        }
        return new Location(world, 0.5, 64, 0.5);
    }

    public MazeBlockData theme(String map) {
        MazeBlockData fallback = MazeBlockData.defaultTheme();
        ConfigurationSection s = section(map);
        if (s == null) return fallback;
        Material top = mat(s.getConfigurationSection("top"));
        Material mid = mat(s.getConfigurationSection("mid"));
        Material bottom = mat(s.getConfigurationSection("bottom"));
        return MazeBlockData.from(top, data(s.getConfigurationSection("top")), mid, data(s.getConfigurationSection("mid")), bottom, data(s.getConfigurationSection("bottom")), fallback);
    }

    public MazeBlockData activeTheme() { return theme(getActiveMap()); }

    private byte data(ConfigurationSection s) { return s != null && s.contains("data") ? (byte) s.getInt("data", 0) : -1; }

    private Material mat(ConfigurationSection s) {
        if (s == null) return null;
        String name = s.getString("material", "");
        if (name != null && !name.isEmpty()) {
            Material named = Material.matchMaterial(name);
            if (named != null) return named;
        }
        int id = s.getInt("id", -1);
        return id >= 0 ? Material.getMaterial(id) : null;
    }

    public String mob(String map) {
        ConfigurationSection s = section(map);
        if (s == null || !s.isString("mob")) return "snowman";
        String configured = s.getString("mob");
        if (configured == null || configured.trim().isEmpty()) return "snowman";
        MobTypes.MobType byId = MobTypes.byId(configured.trim());
        if (byId != null) return byId.id;
        for (MobTypes.MobType type : MobTypes.all()) {
            if (type.display.equalsIgnoreCase(configured.trim())) return type.id;
        }
        return configured.trim().toLowerCase();
    }

    public String activeMob() {
        String selected = selectedMob(getActiveMap());
        UtilEnt.setSelectedGhostMobType(selected);
        return selected;
    }

    public String selectedMob(String map) {
        ConfigurationSection overrides = plugin.getConfig().getConfigurationSection("mob-overrides");
        String override = overrides == null ? "" : overrides.getString(map, "");
        return override == null || override.isEmpty() ? mob(map) : override;
    }

    public void setMobOverride(String map, String mobType) {
        if (!isKnown(map)) return;
        ConfigurationSection overrides = plugin.getConfig().getConfigurationSection("mob-overrides");
        if (overrides == null) overrides = plugin.getConfig().createSection("mob-overrides");

        if (mobType == null || mobType.trim().isEmpty()) {
            overrides.set(map, null);
        } else if ("random".equalsIgnoreCase(mobType.trim())) {
            List<MobTypes.MobType> mobs = new ArrayList<MobTypes.MobType>();
            for (MobTypes.MobType mob : MobTypes.all()) if (!"random".equalsIgnoreCase(mob.id)) mobs.add(mob);
            if (!mobs.isEmpty()) overrides.set(map, mobs.get(RANDOM.nextInt(mobs.size())).id);
        } else {
            MobTypes.MobType valid = MobTypes.byId(mobType.trim());
            if (valid != null && !"random".equalsIgnoreCase(valid.id)) overrides.set(map, valid.id);
            else if (valid == null) overrides.set(map, mobType.trim().toLowerCase());
        }
        plugin.saveConfig();
    }

    public void setActiveMobOverride(String mobType) { setMobOverride(getActiveMap(), mobType); }

    public boolean hasMobOverride(String map) {
        ConfigurationSection overrides = plugin.getConfig().getConfigurationSection("mob-overrides");
        String o = overrides == null ? "" : overrides.getString(map, "");
        return o != null && !o.isEmpty();
    }

    public Set<String> mobTypes() {
        Set<String> out = new LinkedHashSet<String>();
        for (String m : knownMaps()) out.add(mob(m));
        return out;
    }
}
