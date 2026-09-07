package me.monstermaze.stats;

import me.monstermaze.MonsterMazePlugin;
import org.bukkit.Bukkit;
import org.bukkit.scheduler.BukkitRunnable;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/** Pushes a short-lived player-count heartbeat to the Oracle gateway. */
public final class StatusHeartbeatReporter {
    private final MonsterMazePlugin plugin;
    private final String url;
    private final String token;
    private final String serverName;

    public StatusHeartbeatReporter(MonsterMazePlugin plugin) {
        this.plugin = plugin;
        this.url = trim(System.getenv("MM_STATUS_HEARTBEAT_URL"));
        this.token = trim(System.getenv("MM_STATUS_HEARTBEAT_TOKEN"));
        this.serverName = trim(System.getenv("MM_STATUS_SERVER_NAME")).isEmpty() ? "1.8" : trim(System.getenv("MM_STATUS_SERVER_NAME"));
    }

    public boolean isEnabled() {
        return !url.isEmpty() && !token.isEmpty();
    }

    public void start() {
        if (!isEnabled()) {
            plugin.getLogger().info("Status heartbeat disabled (MM_STATUS_HEARTBEAT_URL/TOKEN not configured).");
            return;
        }
        Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, new Runnable() {
            @Override public void run() { send(); }
        }, 20L, 300L);
        plugin.getLogger().info("Status heartbeat enabled: " + serverName + " -> " + url);
    }

    private void send() {
        final int players = Bukkit.getOnlinePlayers().size();
        HttpURLConnection connection = null;
        try {
            byte[] body = ("{\"server\":\"" + escape(serverName) + "\",\"players\":" + players + "}").getBytes(StandardCharsets.UTF_8);
            connection = (HttpURLConnection) new URL(url).openConnection();
            connection.setRequestMethod("POST");
            connection.setConnectTimeout(3000);
            connection.setReadTimeout(3000);
            connection.setDoOutput(true);
            connection.setRequestProperty("Authorization", "Bearer " + token);
            connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
            connection.setRequestProperty("User-Agent", "MonsterMaze-Server/1");
            OutputStream out = connection.getOutputStream();
            try { out.write(body); } finally { out.close(); }
            int status = connection.getResponseCode();
            if (status < 200 || status >= 300) {
                plugin.getLogger().warning("Status heartbeat returned HTTP " + status);
            }
        } catch (Exception e) {
            plugin.getLogger().fine("Status heartbeat failed: " + e.getMessage());
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    private static String trim(String value) { return value == null ? "" : value.trim(); }
    private static String escape(String value) { return value.replace("\\", "\\\\").replace("\"", "\\\""); }
}
