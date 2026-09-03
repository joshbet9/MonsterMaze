package me.monstermaze.stats;

import me.monstermaze.MonsterMazePlugin;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.TimeZone;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Hosted-server weekly challenge cache. Completely inactive when the backend is not configured. */
public final class ChallengeManager {
    private final MonsterMazePlugin plugin;
    private final BackendClient backend;
    private volatile Challenge challenge;
    private volatile List<Row> rows = new ArrayList<Row>();

    private static final Pattern FIELD = Pattern.compile("\\\"%s\\\":(\\\"(?:\\\\.|[^\\\"\\\\])*\\\"|-?\\d+)");

    public ChallengeManager(MonsterMazePlugin plugin) {
        this.plugin = plugin;
        this.backend = plugin.getBackendClient();
        if (backend != null && backend.isEnabled()) {
            Bukkit.getScheduler().runTaskLater(plugin, new Runnable() { @Override public void run() { refresh(); } }, 40L);
            Bukkit.getScheduler().runTaskTimer(plugin, new Runnable() { @Override public void run() { refresh(); } }, 1200L, 1200L);
        }
    }

    public boolean isEnabled() { return backend != null && backend.isEnabled(); }
    public Challenge getChallenge() { return challenge; }
    public List<Row> getRows() { return new ArrayList<Row>(rows); }

    public void refresh() {
        if (!isEnabled()) return;
        Bukkit.getScheduler().runTaskAsynchronously(plugin, new Runnable() {
            @Override public void run() {
                try {
                    Challenge c = parseChallenge(backend.get("/api/v1/challenge/1.8"));
                    ChallengeStandings s = parseStandings(backend.get("/api/v1/challenge/1.8/leaderboard"));
                    if (c == null || s == null) throw new IllegalStateException("invalid challenge response");
                    challenge = c;
                    rows = s.rows;
                } catch (Exception e) {
                    plugin.getLogger().warning("Weekly challenge sync failed: " + e.getMessage());
                }
            }
        });
    }

    public void show(Player player, boolean leaderboardOnly) {
        if (!isEnabled()) {
            player.sendMessage(ChatColor.GRAY + "Weekly challenges are available on the hosted Monster Maze server.");
            return;
        }
        Challenge c = challenge;
        if (c == null) {
            player.sendMessage(ChatColor.YELLOW + "Weekly challenge is still loading...");
            refresh();
            return;
        }
        player.sendMessage("");
        player.sendMessage(ChatColor.GOLD + "=== Weekly Challenge #" + c.number + " ===");
        if (!leaderboardOnly) {
            player.sendMessage(ChatColor.AQUA + "Mode: " + ChatColor.WHITE + pretty(c.mode));
            player.sendMessage(ChatColor.AQUA + "Maze: " + ChatColor.WHITE + (c.pattern + 1));
            player.sendMessage(ChatColor.AQUA + "Kit: " + ChatColor.WHITE + c.kit);
            player.sendMessage(ChatColor.AQUA + "Ends: " + ChatColor.WHITE + formatDate(c.end));
        }
        player.sendMessage(ChatColor.YELLOW + "--- Challenge Standings ---");
        List<Row> current = rows;
        if (current.isEmpty()) {
            player.sendMessage(ChatColor.GRAY + "No completed challenge runs yet.");
        } else {
            int rank = 1;
            for (Row row : current) {
                player.sendMessage(ChatColor.GRAY + "#" + rank + " " + ChatColor.WHITE + row.name
                        + ChatColor.DARK_GRAY + " — Stage " + ChatColor.GOLD + row.stage
                        + ChatColor.DARK_GRAY + " (" + formatTime(row.timeMs) + ")");
                rank++;
            }
        }
        player.sendMessage("");
    }

    private static Challenge parseChallenge(String json) {
        if (json == null) return null;
        String week = string(json, "week");
        String mode = string(json, "mode");
        String kit = string(json, "kit");
        String start = string(json, "start");
        String end = string(json, "end");
        String status = string(json, "status");
        int number = integer(json, "number", 0);
        int pattern = integer(json, "pattern", 0);
        if (week == null || mode == null || kit == null || end == null) return null;
        return new Challenge(week, number, mode, pattern, kit, start, end, status);
    }

    private static ChallengeStandings parseStandings(String json) {
        if (json == null) return null;
        Challenge c = parseChallenge(json);
        if (c == null) return null;
        List<Row> out = new ArrayList<Row>();
        Pattern rowPattern = Pattern.compile("\\{\\\"name\\\":\\\"((?:\\\\.|[^\\\"\\\\])*)\\\",\\\"stage\\\":(\\d+),\\\"timeMs\\\":(\\d+)\\}");
        Matcher m = rowPattern.matcher(json);
        while (m.find()) out.add(new Row(unescape(m.group(1)), Integer.parseInt(m.group(2)), Long.parseLong(m.group(3))));
        return new ChallengeStandings(c, out);
    }

    private static String string(String json, String key) {
        Matcher m = Pattern.compile(String.format(FIELD.pattern(), Pattern.quote(key))).matcher(json);
        if (!m.find()) return null;
        String v = m.group(1);
        if (v.startsWith("\"")) return unescape(v.substring(1, v.length() - 1));
        return v;
    }

    private static int integer(String json, String key, int fallback) {
        String v = string(json, key);
        try { return v == null ? fallback : Integer.parseInt(v); } catch (NumberFormatException e) { return fallback; }
    }

    private static String unescape(String value) {
        return value == null ? "" : value.replace("\\\"", "\"").replace("\\\\", "\\").replace("\\n", "\n").replace("\\r", "\r").replace("\\t", "\t");
    }

    private static String pretty(String mode) { if (mode == null || mode.isEmpty()) return "Unknown"; return Character.toUpperCase(mode.charAt(0)) + mode.substring(1); }
    private static String formatDate(String iso) { if (iso == null) return "Unknown"; return iso.replace('T', ' ').replace("+00:00", " UTC"); }
    private static String formatTime(long ms) { long seconds = Math.max(0L, ms) / 1000L; return (seconds / 60L) + ":" + String.format("%02d", seconds % 60L); }

    public static final class Challenge {
        public final String week; public final int number; public final String mode; public final int pattern; public final String kit; public final String start; public final String end; public final String status;
        Challenge(String week, int number, String mode, int pattern, String kit, String start, String end, String status) { this.week=week; this.number=number; this.mode=mode; this.pattern=pattern; this.kit=kit; this.start=start; this.end=end; this.status=status; }
    }
    public static final class Row {
        public final String name; public final int stage; public final long timeMs;
        Row(String name, int stage, long timeMs) { this.name=name; this.stage=stage; this.timeMs=timeMs; }
    }
    private static final class ChallengeStandings { final Challenge challenge; final List<Row> rows; ChallengeStandings(Challenge c, List<Row> r) { challenge=c; rows=r; } }
}
