package me.monstermaze.stats;

import me.monstermaze.MonsterMazePlugin;
import me.monstermaze.game.MazeMode;
import me.monstermaze.kit.KitType;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

/**
 * Persisted personal bests / leaderboards.
 *
 * Storage (leaderboards.yml):
 *   <modeId>:
 *     pattern<0|1|2>:
 *       <playerUuid>:
 *         <kitId>: <highest stage reached with that kit>
 */
public class LeaderboardManager {

    public static final int PATTERN_COUNT = 3;

    private final MonsterMazePlugin plugin;
    private final File file;
    private YamlConfiguration data;

    /** A player's stored personal best for one mode+pattern. */
    public static class PBInfo {
        public final int stage;
        public final String kit;

        public PBInfo(int stage, String kit) {
            this.stage = stage;
            this.kit = kit;
        }
    }

    /** One ranked row of the leaderboard. */
    public static class Entry {
        public final String name;
        public final int stage;
        public final String kit;

        public Entry(String name, int stage, String kit) {
            this.name = name;
            this.stage = stage;
            this.kit = kit;
        }
    }

    /** One ranked row of the combined (across patterns) mode leaderboard. */
    public static class OverallEntry {
        public final String name;
        public final int stage;
        public final String kit;
        public final int pattern;

        public OverallEntry(String name, int stage, String kit, int pattern) {
            this.name = name;
            this.stage = stage;
            this.kit = kit;
            this.pattern = pattern;
        }
    }

    public LeaderboardManager(MonsterMazePlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "leaderboards.yml");
        reload();
    }

    /** Reload configuration from disk to fetch cross-instance updates. */
    public void reload() {
        this.data = YamlConfiguration.loadConfiguration(file);
    }

    private static String modeKey(MazeMode mode) {
        return mode.id.toLowerCase();
    }

    private static String patternKey(int pattern) {
        return "pattern" + pattern;
    }

    public static String patternName(int pattern) {
        return "Maze " + (pattern + 1);
    }

    private ConfigurationSection playerSection(MazeMode mode, int pattern, UUID player) {
        ConfigurationSection m = data.getConfigurationSection(modeKey(mode));
        if (m == null) return null;
        ConfigurationSection p = m.getConfigurationSection(patternKey(pattern));
        if (p == null) return null;
        return p.getConfigurationSection(player.toString());
    }

    /** A player's best stage for a specific kit on a mode+pattern (0 if none). */
    public int getKitPB(MazeMode mode, int pattern, UUID player, String kitId) {
        if (kitId == null) return 0;
        ConfigurationSection ps = playerSection(mode, pattern, player);
        if (ps == null) return 0;
        return ps.getInt(kitId, 0);
    }

    /** A player's best stage across all kits on a mode+pattern (0 if none). */
    public int getPB(MazeMode mode, int pattern, UUID player) {
        PBInfo best = getBest(mode, pattern, player);
        return best == null ? 0 : best.stage;
    }

    /** A player's overall best for a mode+pattern (null if none), with the achieving kit. */
    public PBInfo getBest(MazeMode mode, int pattern, UUID player) {
        ConfigurationSection ps = playerSection(mode, pattern, player);
        if (ps == null) return null;
        String bestKit = null;
        int bestStage = 0;
        for (String kit : ps.getKeys(false)) {
            int stage = ps.getInt(kit, 0);
            if (stage > bestStage) {
                bestStage = stage;
                bestKit = kit;
            }
        }
        if (bestStage < 1) return null;
        return new PBInfo(bestStage, bestKit);
    }

    /**
     * Record a finished run for the given mode+pattern with the given kit.
     * Only raises that kit's stored PB; never lowers it.
     */
    public void recordRun(MazeMode mode, int pattern, UUID player, int stage, String kitId) {
        if (player == null || pattern < 0 || pattern >= PATTERN_COUNT) return;
        if (stage < 1 || kitId == null || kitId.isEmpty()) return;
        if (stage <= getKitPB(mode, pattern, player, kitId)) return; // no improvement

        String path = modeKey(mode) + "." + patternKey(pattern) + "." + player.toString() + "." + kitId;
        data.set(path, stage);
        save();
    }

    /** Overload to accept KitType directly. */
    public void recordRun(MazeMode mode, int pattern, UUID player, int stage, KitType kit) {
        if (kit == null) return;
        recordRun(mode, pattern, player, stage, kit.id);
    }

    /** Top players on a mode+pattern by overall best stage. Returns (name, stage, kit). */
    public List<Entry> getLeaderboard(MazeMode mode, int pattern, int limit) {
        reload();
        Map<Integer, List<Map.Entry<UUID, String>>> byStage =
                new TreeMap<Integer, List<Map.Entry<UUID, String>>>(
                        new Comparator<Integer>() {
                            @Override public int compare(Integer a, Integer b) {
                                return b.compareTo(a); // descending
                            }
                        });

        ConfigurationSection m = data.getConfigurationSection(modeKey(mode));
        if (m != null) {
            ConfigurationSection p = m.getConfigurationSection(patternKey(pattern));
            if (p != null) {
                for (String key : p.getKeys(false)) {
                    UUID uuid;
                    try {
                        uuid = UUID.fromString(key);
                    } catch (IllegalArgumentException ignored) {
                        continue;
                    }
                    ConfigurationSection ps = p.getConfigurationSection(key);
                    if (ps == null) continue;
                    int bestStage = 0;
                    String bestKit = null;
                    for (String kit : ps.getKeys(false)) {
                        int stage = ps.getInt(kit, 0);
                        if (stage > bestStage) {
                            bestStage = stage;
                            bestKit = kit;
                        }
                    }
                    if (bestStage < 1) continue;
                    List<Map.Entry<UUID, String>> bucket = byStage.get(bestStage);
                    if (bucket == null) {
                        bucket = new ArrayList<Map.Entry<UUID, String>>();
                        byStage.put(bestStage, bucket);
                    }
                    bucket.add(new AbstractMap.SimpleEntry<UUID, String>(uuid, bestKit));
                }
            }
        }

        List<Entry> out = new ArrayList<Entry>();
        for (Map.Entry<Integer, List<Map.Entry<UUID, String>>> bucket : byStage.entrySet()) {
            for (Map.Entry<UUID, String> idKit : bucket.getValue()) {
                if (out.size() >= limit) return out;
                String name = displayName(idKit.getKey());
                if (name == null) continue;
                out.add(new Entry(name, bucket.getKey(), idKit.getValue()));
            }
        }
        return out;
    }

    /** Combined leaderboard across all patterns for a mode (player's single best). */
    public List<OverallEntry> getModeLeaderboard(MazeMode mode, int limit) {
        reload();
        Map<UUID, OverallEntry> bestOf = new java.util.HashMap<UUID, OverallEntry>();

        ConfigurationSection m = data.getConfigurationSection(modeKey(mode));
        if (m != null) {
            for (String patKey : m.getKeys(false)) {
                ConfigurationSection pat = m.getConfigurationSection(patKey);
                if (pat == null) continue;
                Integer pattern = parsePatternKey(patKey);
                if (pattern == null) continue;
                for (String key : pat.getKeys(false)) {
                    UUID uuid;
                    try {
                        uuid = UUID.fromString(key);
                    } catch (IllegalArgumentException ignored) {
                        continue;
                    }
                    ConfigurationSection ps = pat.getConfigurationSection(key);
                    if (ps == null) continue;
                    int bestStage = 0;
                    String bestKit = null;
                    for (String kit : ps.getKeys(false)) {
                        int stage = ps.getInt(kit, 0);
                        if (stage > bestStage) {
                            bestStage = stage;
                            bestKit = kit;
                        }
                    }
                    if (bestStage < 1) continue;
                    OverallEntry prev = bestOf.get(uuid);
                    if (prev == null || bestStage > prev.stage) {
                        bestOf.put(uuid, new OverallEntry(displayName(uuid), bestStage, bestKit, pattern));
                    }
                }
            }
        }

        List<OverallEntry> ranked = new ArrayList<OverallEntry>(bestOf.values());
        java.util.Collections.sort(ranked, new Comparator<OverallEntry>() {
            @Override public int compare(OverallEntry a, OverallEntry b) {
                return b.stage - a.stage; // descending
            }
        });
        if (ranked.size() > limit) ranked = new ArrayList<OverallEntry>(ranked.subList(0, limit));
        return ranked;
    }

    /** Combined leaderboard across all patterns for a specific mode AND specific kit. */
    public List<OverallEntry> getModeAndKitLeaderboard(MazeMode mode, KitType kit, int limit) {
        if (kit == null) {
            return getModeLeaderboard(mode, limit);
        }

        reload();
        Map<UUID, OverallEntry> bestOf = new java.util.HashMap<UUID, OverallEntry>();
        ConfigurationSection m = data.getConfigurationSection(modeKey(mode));

        if (m != null) {
            for (String patKey : m.getKeys(false)) {
                ConfigurationSection pat = m.getConfigurationSection(patKey);
                if (pat == null) continue;
                Integer pattern = parsePatternKey(patKey);
                if (pattern == null) continue;

                for (String key : pat.getKeys(false)) {
                    UUID uuid;
                    try {
                        uuid = UUID.fromString(key);
                    } catch (IllegalArgumentException ignored) {
                        continue;
                    }
                    ConfigurationSection ps = pat.getConfigurationSection(key);
                    if (ps == null) continue;

                    int bestStage = 0;
                    String matchedKit = null;

                    for (String k : ps.getKeys(false)) {
                        if (k.equalsIgnoreCase(kit.id) || k.equalsIgnoreCase(kit.name())) {
                            int stage = ps.getInt(k, 0);
                            if (stage > bestStage) {
                                bestStage = stage;
                                matchedKit = k;
                            }
                        }
                    }

                    if (bestStage < 1) continue;

                    OverallEntry prev = bestOf.get(uuid);
                    if (prev == null || bestStage > prev.stage) {
                        bestOf.put(uuid, new OverallEntry(displayName(uuid), bestStage, matchedKit != null ? matchedKit : kit.id, pattern));
                    }
                }
            }
        }

        List<OverallEntry> ranked = new ArrayList<OverallEntry>(bestOf.values());
        java.util.Collections.sort(ranked, new Comparator<OverallEntry>() {
            @Override public int compare(OverallEntry a, OverallEntry b) {
                return b.stage - a.stage; // descending
            }
        });

        if (ranked.size() > limit) {
            ranked = new ArrayList<OverallEntry>(ranked.subList(0, limit));
        }
        return ranked;
    }

    private Integer parsePatternKey(String key) {
        if (key == null || !key.startsWith("pattern")) return null;
        try {
            return Integer.valueOf(key.substring("pattern".length()));
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    /** Uppercase-friendly player name for an offline uuid (falls back to short uuid). */
    private String displayName(UUID uuid) {
        String name = Bukkit.getOfflinePlayer(uuid).getName();
        if (name != null && !name.isEmpty()) return name;
        return uuid.toString().substring(0, 6);
    }

    private void save() {
        try {
            data.save(file);
        } catch (IOException e) {
            plugin.getLogger().warning("Could not save leaderboards.yml: " + e.getMessage());
        }
    }
}