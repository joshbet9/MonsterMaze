package me.monstermaze.stats;

import me.monstermaze.MonsterMazePlugin;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Lightweight in-game view of the authoritative competitive backend. */
public final class CompetitiveUI {
    private static final String NUMBER_PATTERN = "\\\"%s\\\":(-?\\d+(?:\\.\\d+)?)";
    private static final Pattern ROW = Pattern.compile("\\{\\\"uuid\\\":\\\"[^\\\"]+\\\",\\\"name\\\":\\\"((?:\\\\.|[^\\\"\\\\])*)\\\",\\\"score\\\":(-?\\d+(?:\\.\\d+)?)\\}");
    private final MonsterMazePlugin plugin;

    public CompetitiveUI(MonsterMazePlugin plugin) { this.plugin = plugin; }

    public void showStats(final Player player) {
        if (!plugin.getBackendClient().isEnabled()) { player.sendMessage(ChatColor.YELLOW + "Competitive stats are unavailable while the backend is disabled."); return; }
        new BukkitRunnable() {
            @Override public void run() {
                try {
                    String mmcl = plugin.getBackendClient().get("/api/v1/mmcl/player/" + player.getUniqueId());
                    String mmr = plugin.getBackendClient().get("/api/v1/mmr/player/" + player.getUniqueId());
                    String season = plugin.getBackendClient().get("/api/v1/season/current");
                    final String message = formatStats(mmcl, mmr, season);
                    Bukkit.getScheduler().runTask(plugin, new Runnable() { @Override public void run() { if (player.isOnline()) sendLines(player, message); } });
                } catch (final Exception e) {
                    Bukkit.getScheduler().runTask(plugin, new Runnable() { @Override public void run() { if (player.isOnline()) player.sendMessage(ChatColor.RED + "Could not load competitive stats: " + e.getMessage()); } });
                }
            }
        }.runTaskAsynchronously(plugin);
    }

    public void showLeaderboard(final Player player, final String kind) {
        final String k = kind == null ? "mmcl" : kind.toLowerCase();
        if (!(k.equals("mmcl") || k.equals("mmr") || k.equals("elo") || k.equals("weekly") || k.equals("tournament"))) { player.sendMessage(ChatColor.RED + "Usage: /mm clb <mmcl|mmr|elo|weekly|tournament>"); return; }
        if (!plugin.getBackendClient().isEnabled()) { player.sendMessage(ChatColor.YELLOW + "Competitive leaderboards are unavailable while the backend is disabled."); return; }
        new BukkitRunnable() {
            @Override public void run() {
                try {
                    final String text = formatLeaderboard(k, plugin.getBackendClient().get("/api/v1/" + k + "/leaderboard"));
                    Bukkit.getScheduler().runTask(plugin, new Runnable() { @Override public void run() { if (player.isOnline()) sendLines(player, text); } });
                } catch (final Exception e) {
                    Bukkit.getScheduler().runTask(plugin, new Runnable() { @Override public void run() { if (player.isOnline()) player.sendMessage(ChatColor.RED + "Could not load competitive leaderboard: " + e.getMessage()); } });
                }
            }
        }.runTaskAsynchronously(plugin);
    }

    private String formatStats(String mmcl, String mmr, String season) {
        double elo = number(mmcl, "elo"), weekly = number(mmcl, "weeklyPoints"), tournament = number(mmcl, "tournamentPoints");
        double ec = number(mmcl, "eloComponent"), wc = number(mmcl, "weeklyComponent"), tc = number(mmcl, "tournamentComponent"), score = number(mmcl, "mmcl");
        double permanent = number(mmr, "mmr"), seasonNumber = number(season, "number");
        StringBuilder s = new StringBuilder(ChatColor.GOLD + "=== Monster Maze Competitive ===\\n");
        if (seasonNumber >= 0) s.append(ChatColor.GRAY).append("Season ").append((int)seasonNumber).append("\\n");
        s.append(ChatColor.AQUA).append("MMCL: ").append(ChatColor.WHITE).append(fmt(score)).append("\\n");
        s.append(ChatColor.GRAY).append("  ELO: ").append(ChatColor.WHITE).append(fmt(ec)).append(ChatColor.DARK_GRAY).append(" × 40% = ").append(ChatColor.WHITE).append(fmt(ec * .40)).append("\\n");
        s.append(ChatColor.GRAY).append("  Weekly: ").append(ChatColor.WHITE).append(fmt(wc)).append(ChatColor.DARK_GRAY).append(" × 30% = ").append(ChatColor.WHITE).append(fmt(wc * .30)).append("\\n");
        s.append(ChatColor.GRAY).append("  Tournament: ").append(ChatColor.WHITE).append(fmt(tc)).append(ChatColor.DARK_GRAY).append(" × 30% = ").append(ChatColor.WHITE).append(fmt(tc * .30)).append("\\n");
        s.append(ChatColor.YELLOW).append("Season ELO: ").append(ChatColor.WHITE).append(fmt(elo)).append("\\n");
        s.append(ChatColor.YELLOW).append("Weekly points: ").append(ChatColor.WHITE).append(fmt(weekly)).append("\\n");
        s.append(ChatColor.YELLOW).append("Tournament points: ").append(ChatColor.WHITE).append(fmt(tournament)).append("\\n");
        s.append(ChatColor.LIGHT_PURPLE).append("Permanent MMR: ").append(ChatColor.WHITE).append(fmt(permanent));
        return s.toString();
    }

    private String formatLeaderboard(String kind, String json) {
        StringBuilder s = new StringBuilder(ChatColor.GOLD + "=== " + kind.toUpperCase() + " Leaderboard ===\\n");
        Matcher m = ROW.matcher(json == null ? "" : json); int rank = 1;
        while (m.find() && rank <= 10) { s.append(ChatColor.GRAY).append("#").append(rank++).append(" ").append(ChatColor.WHITE).append(unescape(m.group(1))).append(ChatColor.DARK_GRAY).append(" — ").append(ChatColor.GOLD).append(fmt(Double.parseDouble(m.group(2)))).append("\\n"); }
        if (rank == 1) s.append(ChatColor.GRAY).append("No entries yet.");
        return s.toString().trim();
    }
    private void sendLines(Player p, String text) { for (String line : text.split("\\n")) p.sendMessage(line); }
    private static double number(String json, String key) { if (json == null) return -1; Matcher m = Pattern.compile(String.format(NUMBER_PATTERN, Pattern.quote(key))).matcher(json); return m.find() ? Double.parseDouble(m.group(1)) : -1; }
    private static String fmt(double v) { return v < 0 ? "—" : String.format(java.util.Locale.US, "%.1f", v); }
    private static String unescape(String s) { return s == null ? "" : s.replace("\\\"", "\"").replace("\\\\", "\\"); }
}
