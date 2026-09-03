package me.monstermaze.stats;

import me.monstermaze.MonsterMazePlugin;
import me.monstermaze.game.MazeMode;
import me.monstermaze.kit.KitType;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.scheduler.BukkitRunnable;

import java.io.File;
import java.io.IOException;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Leaderboards and personal bests.
 *
 * When the public backend is enabled, the backend is authoritative and this
 * class maintains a short-lived in-memory mirror for the 1.8 lobby/game UI.
 * The existing YAML file remains the offline/local fallback.
 */
public class LeaderboardManager {

    public static final int PATTERN_COUNT = 3;

    private final MonsterMazePlugin plugin;
    private final File file;
    private YamlConfiguration data;
    private final BackendClient backend;

    private volatile boolean remoteReady;
    private volatile Map<String, List<OverallEntry>> remoteOverall = new HashMap<String, List<OverallEntry>>();
    private volatile Map<String, Map<String, List<OverallEntry>>> remoteKit = new HashMap<String, Map<String, List<OverallEntry>>>();
    private volatile Map<String, Map<Integer, Map<String, PBInfo>>> remotePB = new HashMap<String, Map<Integer, Map<String, PBInfo>>>();

    private static final Pattern JSON_ROW = Pattern.compile(
            "\\{\\\"name\\\":\\\"((?:\\\\.|[^\\\"\\\\])*)\\\",\\\"kit\\\":\\\"((?:\\\\.|[^\\\"\\\\])*)\\\",\\\"stage\\\":(\\d+)\\}");
    private static final Pattern JSON_PB = Pattern.compile(
            "\\{\\\"pattern\\\":(\\d+),\\\"kit\\\":\\\"((?:\\\\.|[^\\\"\\\\])*)\\\",\\\"stage\\\":(\\d+),\\\"timeMs\\\":(\\d+)\\}");

    public static class PBInfo {
        public final int stage;
        public final String kit;
        public PBInfo(int stage, String kit) { this.stage = stage; this.kit = kit; }
    }

    public static class Entry {
        public final String name;
        public final int stage;
        public final String kit;
        public Entry(String name, int stage, String kit) { this.name = name; this.stage = stage; this.kit = kit; }
    }

    public static class OverallEntry {
        public final String name;
        public final int stage;
        public final String kit;
        public final int pattern;
        public OverallEntry(String name, int stage, String kit, int pattern) {
            this.name = name; this.stage = stage; this.kit = kit; this.pattern = pattern;
        }
    }

    public LeaderboardManager(MonsterMazePlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "leaderboards.yml");
        this.backend = plugin.getBackendClient();
        reload();
        if (backend != null && backend.isEnabled()) {
            Bukkit.getScheduler().runTaskLater(plugin, new Runnable() {
                @Override public void run() { refreshFromBackend(); }
            }, 20L);
            Bukkit.getScheduler().runTaskTimer(plugin, new Runnable() {
                @Override public void run() { refreshFromBackend(); }
            }, 600L, 600L);
        }
    }

    public void reload() {
        this.data = YamlConfiguration.loadConfiguration(file);
    }

    private static String modeKey(MazeMode mode) { return mode.id.toLowerCase(); }
    private static String patternKey(int pattern) { return "pattern" + pattern; }
    public static String patternName(int pattern) { return "Maze " + (pattern + 1); }

    /** Refresh the public mirror without blocking the Minecraft server thread. */
    public void refreshFromBackend() {
        if (backend == null || !backend.isEnabled()) return;
        new BukkitRunnable() {
            @Override public void run() {
                try {
                    Map<String, List<OverallEntry>> overall = new HashMap<String, List<OverallEntry>>();
                    Map<String, Map<String, List<OverallEntry>>> kits = new HashMap<String, Map<String, List<OverallEntry>>>();
                    Map<String, Map<Integer, Map<String, PBInfo>>> pbs = new HashMap<String, Map<Integer, Map<String, PBInfo>>>();

                    for (MazeMode mode : MazeMode.values()) {
                        String mk = modeKey(mode);
                        overall.put(mk, parseBoard(backend.get("/api/v1/leaderboard/1.8/" + mk + "/overall")));
                        Map<String, List<OverallEntry>> modeKits = new HashMap<String, List<OverallEntry>>();
                        for (KitType kit : KitType.values()) {
                            String id = kit.id;
                            modeKits.put(id, parseBoard(backend.get("/api/v1/leaderboard/1.8/" + mk + "/kit/" + id)));
                        }
                        kits.put(mk, modeKits);

                        for (org.bukkit.entity.Player player : Bukkit.getOnlinePlayers()) {
                            String raw = backend.get("/api/v1/pb/1.8/" + mk + "/" + player.getUniqueId().toString());
                            Map<Integer, Map<String, PBInfo>> byPattern = parsePB(raw);
                            pbs.put(mk + "|" + player.getUniqueId().toString(), byPattern);
                        }
                    }

                    remoteOverall = overall;
                    remoteKit = kits;
                    remotePB = pbs;
                    remoteReady = true;
                    plugin.getLogger().fine("Monster Maze backend leaderboards/PBs refreshed.");
                } catch (Exception e) {
                    plugin.getLogger().warning("Backend leaderboard sync failed: " + e.getMessage());
                }
            }
        }.runTaskAsynchronously(plugin);
    }

    private static List<OverallEntry> parseBoard(String json) {
        List<OverallEntry> out = new ArrayList<OverallEntry>();
        if (json == null) return out;
        Matcher m = JSON_ROW.matcher(json);
        while (m.find()) out.add(new OverallEntry(unescape(m.group(1)), Integer.parseInt(m.group(3)), unescape(m.group(2)), -1));
        return out;
    }

    private static Map<Integer, Map<String, PBInfo>> parsePB(String json) {
        Map<Integer, Map<String, PBInfo>> out = new HashMap<Integer, Map<String, PBInfo>>();
        if (json == null) return out;
        Matcher m = JSON_PB.matcher(json);
        while (m.find()) {
            int pattern = Integer.parseInt(m.group(1));
            Map<String, PBInfo> kits = out.get(pattern);
            if (kits == null) { kits = new HashMap<String, PBInfo>(); out.put(pattern, kits); }
            String kit = unescape(m.group(2));
            int stage = Integer.parseInt(m.group(3));
            PBInfo old = kits.get(kit);
            if (old == null || stage > old.stage) kits.put(kit, new PBInfo(stage, kit));
        }
        return out;
    }

    private static String unescape(String value) {
        if (value == null) return "";
        return value.replace("\\\"", "\"").replace("\\\\", "\\").replace("\\n", "\n").replace("\\r", "\r").replace("\\t", "\t");
    }

    private Map<Integer, Map<String, PBInfo>> remotePlayer(MazeMode mode, UUID player) {
        return remotePB.get(modeKey(mode) + "|" + player.toString());
    }

    public int getKitPB(MazeMode mode, int pattern, UUID player, String kitId) {
        if (kitId == null) return 0;
        if (remoteReady) {
            Map<Integer, Map<String, PBInfo>> byPattern = remotePlayer(mode, player);
            if (byPattern != null) {
                Map<String, PBInfo> kits = byPattern.get(pattern);
                if (kits != null) {
                    PBInfo info = kits.get(kitId);
                    if (info == null) {
                        for (Map.Entry<String, PBInfo> e : kits.entrySet()) if (e.getKey().equalsIgnoreCase(kitId)) return e.getValue().stage;
                    } else return info.stage;
                }
            }
        }
        ConfigurationSection ps = playerSection(mode, pattern, player);
        return ps == null ? 0 : ps.getInt(kitId, 0);
    }

    public int getPB(MazeMode mode, int pattern, UUID player) {
        PBInfo best = getBest(mode, pattern, player);
        return best == null ? 0 : best.stage;
    }

    public PBInfo getBest(MazeMode mode, int pattern, UUID player) {
        if (remoteReady) {
            Map<Integer, Map<String, PBInfo>> byPattern = remotePlayer(mode, player);
            if (byPattern != null) {
                Map<String, PBInfo> kits = byPattern.get(pattern);
                if (kits != null) {
                    PBInfo best = null;
                    for (PBInfo info : kits.values()) if (best == null || info.stage > best.stage) best = info;
                    if (best != null) return best;
                }
            }
        }
        ConfigurationSection ps = playerSection(mode, pattern, player);
        if (ps == null) return null;
        String bestKit = null; int bestStage = 0;
        for (String kit : ps.getKeys(false)) {
            int stage = ps.getInt(kit, 0);
            if (stage > bestStage) { bestStage = stage; bestKit = kit; }
        }
        return bestStage < 1 ? null : new PBInfo(bestStage, bestKit);
    }

    /** Local persistence is used only when no backend is configured. */
    public void recordRun(MazeMode mode, int pattern, UUID player, int stage, String kitId) {
        if (backend != null && backend.isEnabled()) return;
        if (player == null || pattern < 0 || pattern >= PATTERN_COUNT || stage < 1 || kitId == null || kitId.isEmpty()) return;
        if (stage <= getKitPB(mode, pattern, player, kitId)) return;
        data.set(modeKey(mode) + "." + patternKey(pattern) + "." + player.toString() + "." + kitId, stage);
        save();
    }

    public void recordRun(MazeMode mode, int pattern, UUID player, int stage, KitType kit) {
        if (kit != null) recordRun(mode, pattern, player, stage, kit.id);
    }

    public List<Entry> getLeaderboard(MazeMode mode, int pattern, int limit) {
        reload();
        Map<Integer, List<Map.Entry<UUID, String>>> byStage = new TreeMap<Integer, List<Map.Entry<UUID, String>>(new Comparator<Integer>() {
            @Override public int compare(Integer a, Integer b) { return b.compareTo(a); }
        });
        ConfigurationSection m = data.getConfigurationSection(modeKey(mode));
        if (m != null) {
            ConfigurationSection p = m.getConfigurationSection(patternKey(pattern));
            if (p != null) for (String key : p.getKeys(false)) {
                UUID uuid; try { uuid = UUID.fromString(key); } catch (IllegalArgumentException ignored) { continue; }
                ConfigurationSection ps = p.getConfigurationSection(key); if (ps == null) continue;
                int bestStage = 0; String bestKit = null;
                for (String kit : ps.getKeys(false)) { int stage = ps.getInt(kit, 0); if (stage > bestStage) { bestStage = stage; bestKit = kit; } }
                if (bestStage < 1) continue;
                List<Map.Entry<UUID, String>> bucket = byStage.get(bestStage);
                if (bucket == null) { bucket = new ArrayList<Map.Entry<UUID, String>>(); byStage.put(bestStage, bucket); }
                bucket.add(new AbstractMap.SimpleEntry<UUID, String>(uuid, bestKit));
            }
        }
        List<Entry> out = new ArrayList<Entry>();
        for (Map.Entry<Integer, List<Map.Entry<UUID, String>>> bucket : byStage.entrySet()) for (Map.Entry<UUID, String> idKit : bucket.getValue()) {
            if (out.size() >= limit) return out;
            out.add(new Entry(displayName(idKit.getKey()), bucket.getKey(), idKit.getValue()));
        }
        return out;
    }

    public List<OverallEntry> getModeLeaderboard(MazeMode mode, int limit) {
        if (remoteReady) {
            List<OverallEntry> rows = remoteOverall.get(modeKey(mode));
            if (rows != null) return new ArrayList<OverallEntry>(rows.subList(0, Math.min(limit, rows.size())));
        }
        return localModeLeaderboard(mode, limit, null);
    }

    public List<OverallEntry> getModeAndKitLeaderboard(MazeMode mode, KitType kit, int limit) {
        if (kit == null) return getModeLeaderboard(mode, limit);
        if (remoteReady) {
            Map<String, List<OverallEntry>> modeKits = remoteKit.get(modeKey(mode));
            if (modeKits != null) {
                List<OverallEntry> rows = modeKits.get(kit.id);
                if (rows == null) for (Map.Entry<String, List<OverallEntry>> e : modeKits.entrySet()) if (e.getKey().equalsIgnoreCase(kit.id)) rows = e.getValue();
                if (rows != null) return new ArrayList<OverallEntry>(rows.subList(0, Math.min(limit, rows.size())));
            }
        }
        return localModeLeaderboard(mode, limit, kit);
    }

    private List<OverallEntry> localModeLeaderboard(MazeMode mode, int limit, KitType filterKit) {
        reload();
        Map<UUID, OverallEntry> bestOf = new HashMap<UUID, OverallEntry>();
        ConfigurationSection m = data.getConfigurationSection(modeKey(mode));
        if (m != null) for (String patKey : m.getKeys(false)) {
            ConfigurationSection pat = m.getConfigurationSection(patKey); Integer pattern = parsePatternKey(patKey); if (pat == null || pattern == null) continue;
            for (String key : pat.getKeys(false)) {
                UUID uuid; try { uuid = UUID.fromString(key); } catch (IllegalArgumentException ignored) { continue; }
                ConfigurationSection ps = pat.getConfigurationSection(key); if (ps == null) continue;
                int bestStage = 0; String bestKit = null;
                for (String k : ps.getKeys(false)) {
                    if (filterKit != null && !(k.equalsIgnoreCase(filterKit.id) || k.equalsIgnoreCase(filterKit.name()))) continue;
                    int stage = ps.getInt(k, 0); if (stage > bestStage) { bestStage = stage; bestKit = k; }
                }
                if (bestStage < 1) continue;
                OverallEntry prev = bestOf.get(uuid);
                if (prev == null || bestStage > prev.stage) bestOf.put(uuid, new OverallEntry(displayName(uuid), bestStage, bestKit, pattern));
            }
        }
        List<OverallEntry> ranked = new ArrayList<OverallEntry>(bestOf.values());
        java.util.Collections.sort(ranked, new Comparator<OverallEntry>() { @Override public int compare(OverallEntry a, OverallEntry b) { return b.stage - a.stage; } });
        return ranked.size() > limit ? new ArrayList<OverallEntry>(ranked.subList(0, limit)) : ranked;
    }

    private Integer parsePatternKey(String key) {
        if (key == null || !key.startsWith("pattern")) return null;
        try { return Integer.valueOf(key.substring("pattern".length())); } catch (NumberFormatException ignored) { return null; }
    }

    private ConfigurationSection playerSection(MazeMode mode, int pattern, UUID player) {
        ConfigurationSection m = data.getConfigurationSection(modeKey(mode));
        if (m == null) return null;
        ConfigurationSection p = m.getConfigurationSection(patternKey(pattern));
        return p == null ? null : p.getConfigurationSection(player.toString());
    }

    private String displayName(UUID uuid) {
        String name = Bukkit.getOfflinePlayer(uuid).getName();
        return name != null && !name.isEmpty() ? name : uuid.toString().substring(0, 6);
    }

    private void save() {
        try { data.save(file); } catch (IOException e) { plugin.getLogger().warning("Could not save leaderboards.yml: " + e.getMessage()); }
    }
}
