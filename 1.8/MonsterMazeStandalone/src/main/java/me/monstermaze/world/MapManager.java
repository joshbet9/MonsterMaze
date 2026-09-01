package me.monstermaze.world;

import me.monstermaze.MonsterMazePlugin;
import me.monstermaze.maze.MazeBlockData;
import me.monstermaze.util.MobTypes;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

/** Manages the active Monster Maze arena map and its per-map theme settings. */
public class MapManager {
    private static final String DEFAULT_MAP = "eyeofender";
    private static final Random RANDOM = new Random();
    private final MonsterMazePlugin plugin;
    private final VoidWorldManager voidWorlds;
    private String activeMap;
    public MapManager(MonsterMazePlugin plugin, VoidWorldManager voidWorlds) { this.plugin = plugin; this.voidWorlds = voidWorlds; }

    public void loadActiveMapFromConfig() {
        ensureEyeOfEnderConfig();
        String map = plugin.getConfig().getString("map", DEFAULT_MAP);
        if ("void".equalsIgnoreCase(map)) map = DEFAULT_MAP;
        if (!isKnown(map)) map = DEFAULT_MAP;
        activeMap = map;
        plugin.getConfig().set("map", map);
        plugin.saveConfig();
    }

    private void ensureEyeOfEnderConfig() {
        ConfigurationSection maps = plugin.getConfig().getConfigurationSection("maps");
        if (maps == null) maps = plugin.getConfig().createSection("maps");
        ConfigurationSection eye = maps.getConfigurationSection(DEFAULT_MAP);
        if (eye == null) eye = maps.createSection(DEFAULT_MAP);
        // Eye of Ender deliberately uses the existing empty-air world, but its
        // three maze layers must always be End Stone regardless of an older config.
        eye.set("world-folder", "mm_void");
        eye.set("mob", "enderman");
        eye.set("top.material", "END_STONE");
        eye.set("top.id", 121);
        eye.set("top.data", 0);
        eye.set("mid.material", "END_STONE");
        eye.set("mid.id", 121);
        eye.set("mid.data", 0);
        eye.set("bottom.material", "END_STONE");
        eye.set("bottom.id", 121);
        eye.set("bottom.data", 0);
        if (!eye.contains("center.x")) eye.set("center.x", 0);
        if (!eye.contains("center.y")) eye.set("center.y", 64);
        if (!eye.contains("center.z")) eye.set("center.z", 0);
        if (maps.contains("void")) maps.set("void", null);
    }

    public String getActiveMap() { return activeMap == null ? DEFAULT_MAP : activeMap; }
    public List<String> knownMaps() {
        ensureEyeOfEnderConfig();
        ConfigurationSection maps = plugin.getConfig().getConfigurationSection("maps");
        if (maps == null) return new ArrayList<String>();
        Set<String> names = new LinkedHashSet<String>(maps.getKeys(false));
        names.remove("void");
        if (!names.contains(DEFAULT_MAP)) names.add(DEFAULT_MAP);
        return new ArrayList<String>(names);
    }
    public boolean isKnown(String map) { ConfigurationSection maps = plugin.getConfig().getConfigurationSection("maps"); return map != null && maps != null && maps.contains(map); }
    public boolean setActiveMap(String map) { if (!isKnown(map)) return false; activeMap = map; plugin.getConfig().set("map", map); plugin.saveConfig(); return true; }
    private ConfigurationSection section(String map) { ConfigurationSection maps = plugin.getConfig().getConfigurationSection("maps"); return maps == null ? null : maps.getConfigurationSection(map); }

    public World ensureActiveWorld() { String map = getActiveMap(); if (map.equalsIgnoreCase(DEFAULT_MAP)) return voidWorlds.ensureWorld(); String folder = worldFolder(map); return folder == null ? null : ensureMapWorld(folder); }
    private String worldFolder(String map) { ConfigurationSection s = section(map); if (s == null) return null; String folder = s.getString("world-folder"); return folder == null || folder.isEmpty() ? null : folder; }
    private World ensureMapWorld(String folder) {
        World existing = Bukkit.getWorld(folder); if (existing != null) return existing;
        WorldCreator creator = new WorldCreator(folder); creator.generateStructures(false); creator.environment(World.Environment.NORMAL);
        try {
            World world = creator.createWorld();
            if (world != null) {
                world.setGameRuleValue("doMobSpawning", "false"); world.setGameRuleValue("doDaylightCycle", "false"); world.setGameRuleValue("doWeatherCycle", "false"); world.setGameRuleValue("keepInventory", "true"); world.setTime(6000); world.setStorm(false); world.setThundering(false); world.setSpawnFlags(false, false); world.setMonsterSpawnLimit(0); world.setAnimalSpawnLimit(0); world.setAmbientSpawnLimit(0); world.setWaterAnimalSpawnLimit(0); clearMobs(world); plugin.getLogger().info("Loaded arena world '" + folder + "'.");
            } else plugin.getLogger().severe("Failed to load arena world '" + folder + "'.");
            return world;
        } catch (Exception ex) { plugin.getLogger().severe("Could not load arena world '" + folder + "': " + ex.getMessage()); return null; }
    }
    public void clearMobs(World world) { if (world == null) return; int removed = 0; for (Entity e : world.getEntities()) { if (e instanceof Player) continue; e.remove(); removed++; } if (removed > 0) plugin.getLogger().info("Cleared " + removed + " lingering entities from arena world '" + world.getName() + "'."); }

    public Location defaultCenter() { return defaultCenter(getActiveMap()); }
    public Location defaultCenter(String map) {
        ConfigurationSection s = section(map); World world;
        if (map.equalsIgnoreCase(DEFAULT_MAP)) world = voidWorlds.ensureWorld(); else { String folder = worldFolder(map); world = folder == null ? null : ensureMapWorld(folder); }
        if (world == null) return null;
        if (s != null && s.contains("center")) { ConfigurationSection c = s.getConfigurationSection("center"); return new Location(world, c.getInt("x", 0) + 0.5, c.getInt("y", 64), c.getInt("z", 0) + 0.5); }
        return new Location(world, 0.5, 64, 0.5);
    }

    public MazeBlockData theme(String map) {
        MazeBlockData fallback = MazeBlockData.defaultTheme(); ConfigurationSection s = section(map); if (s == null) return fallback;
        Material top = mat(s.getConfigurationSection("top")); Material mid = mat(s.getConfigurationSection("mid")); Material bottom = mat(s.getConfigurationSection("bottom"));
        return MazeBlockData.from(top, data(s.getConfigurationSection("top")), mid, data(s.getConfigurationSection("mid")), bottom, data(s.getConfigurationSection("bottom")), fallback);
    }
    public MazeBlockData activeTheme() { return theme(getActiveMap()); }
    private byte data(ConfigurationSection s) { return s != null && s.contains("data") ? (byte) s.getInt("data", 0) : -1; }
    private Material mat(ConfigurationSection s) { if (s == null) return null; String name = s.getString("material", ""); if (name != null && !name.isEmpty()) { Material named = Material.matchMaterial(name); if (named != null) return named; } int id = s.getInt("id", -1); return id >= 0 ? Material.getMaterial(id) : null; }

    public String mob(String map) { ConfigurationSection s = section(map); return s != null && s.isString("mob") ? s.getString("mob") : "snowman"; }
    public String activeMob() { return selectedMob(getActiveMap()); }
    public String selectedMob(String map) { ConfigurationSection s = section(map); if (s == null) return "snowman"; String override = s.getString("mob-override", ""); return override == null || override.isEmpty() ? mob(map) : override; }
    public void setMobOverride(String map, String mobType) {
        ConfigurationSection s = section(map); if (s == null) return;
        if (mobType == null || mobType.trim().isEmpty()) s.set("mob-override", null);
        else if ("random".equalsIgnoreCase(mobType.trim())) { List<MobTypes.MobType> mobs = new ArrayList<MobTypes.MobType>(); for (MobTypes.MobType mob : MobTypes.all()) if (!"random".equalsIgnoreCase(mob.id)) mobs.add(mob); if (!mobs.isEmpty()) s.set("mob-override", mobs.get(RANDOM.nextInt(mobs.size())).id); }
        else { MobTypes.MobType valid = MobTypes.byId(mobType.trim()); if (valid != null && !"random".equalsIgnoreCase(valid.id)) s.set("mob-override", valid.id); else if (valid == null) s.set("mob-override", mobType.trim().toLowerCase()); }
        plugin.saveConfig();
    }
    public void setActiveMobOverride(String mobType) { setMobOverride(getActiveMap(), mobType); }
    public boolean hasMobOverride(String map) { ConfigurationSection s = section(map); String o = s == null ? "" : s.getString("mob-override", ""); return o != null && !o.isEmpty(); }
    public Set<String> mobTypes() { Set<String> out = new LinkedHashSet<String>(); for (String m : knownMaps()) out.add(mob(m)); return out; }
}
