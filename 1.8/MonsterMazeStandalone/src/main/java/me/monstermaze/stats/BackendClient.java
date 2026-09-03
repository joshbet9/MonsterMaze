package me.monstermaze.stats;

import me.monstermaze.MonsterMazePlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

/** Direct HTTPS client used by public servers. Solo installs remain file/webhook based. */
public final class BackendClient {
    private final MonsterMazePlugin plugin;
    private final String baseUrl;
    private final String token;

    public BackendClient(MonsterMazePlugin plugin) {
        this.plugin = plugin;
        this.baseUrl = normalize(System.getenv("MM_API_URL"));
        this.token = trim(System.getenv("MM_API_TOKEN"));
        if (isEnabled()) {
            plugin.getLogger().info("Monster Maze backend API enabled: " + baseUrl);
        }
    }

    public boolean isEnabled() {
        return !baseUrl.isEmpty() && !token.isEmpty();
    }

    /** Submit a completed run without blocking the Minecraft server thread. */
    public void submit(final String submissionId, final UUID uuid, final String name,
                       final String mode, final int pattern, final String kit,
                       final int stage, final long elapsedMs, final String platform,
                       final String pluginVersion, final String configHash,
                       final long submittedAt) {
        if (!isEnabled()) return;

        new BukkitRunnable() {
            @Override public void run() {
                String payload = buildPayload(submissionId, uuid, name, mode, pattern, kit,
                        stage, elapsedMs, platform, pluginVersion, configHash, submittedAt);
                int attempts = 0;
                long delay = 1000L;
                while (attempts < 4) {
                    attempts++;
                    try {
                        post(payload);
                        plugin.getLogger().info("Run submitted to Monster Maze backend: " + submissionId);
                        return;
                    } catch (Exception e) {
                        if (attempts >= 4) {
                            plugin.getLogger().warning("Backend submission failed for " + submissionId
                                    + " after " + attempts + " attempts: " + e.getMessage());
                            return;
                        }
                        try { Thread.sleep(delay); } catch (InterruptedException interrupted) {
                            Thread.currentThread().interrupt();
                            return;
                        }
                        delay *= 2L;
                    }
                }
            }
        }.runTaskAsynchronously(plugin);
    }

    private void post(String payload) throws Exception {
        URL url = new URL(baseUrl + "/api/v1/runs");
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("POST");
        connection.setConnectTimeout(5000);
        connection.setReadTimeout(10000);
        connection.setDoOutput(true);
        connection.setRequestProperty("Authorization", "Bearer " + token);
        connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
        connection.setRequestProperty("User-Agent", "MonsterMaze-Server/1.0");

        byte[] body = payload.getBytes(StandardCharsets.UTF_8);
        OutputStream out = connection.getOutputStream();
        try {
            out.write(body);
        } finally {
            out.close();
        }

        int status = connection.getResponseCode();
        if (status < 200 || status >= 300) {
            InputStream error = connection.getErrorStream();
            String detail = "HTTP " + status;
            if (error != null) {
                BufferedReader reader = new BufferedReader(new InputStreamReader(error, StandardCharsets.UTF_8));
                try { detail += " " + reader.readLine(); } finally { reader.close(); }
            }
            connection.disconnect();
            throw new IllegalStateException(detail);
        }
        connection.disconnect();
    }

    private static String buildPayload(String submissionId, UUID uuid, String name,
                                       String mode, int pattern, String kit, int stage,
                                       long elapsedMs, String platform, String pluginVersion,
                                       String configHash, long submittedAt) {
        StringBuilder sb = new StringBuilder();
        sb.append("{");
        field(sb, "submissionId", submissionId, true);
        field(sb, "platform", platform, true);
        field(sb, "plugin", pluginVersion, true);
        field(sb, "name", name, true);
        field(sb, "uuid", uuid.toString(), true);
        field(sb, "mode", mode, true);
        number(sb, "pattern", pattern, true);
        field(sb, "kit", kit, true);
        number(sb, "stage", stage, true);
        number(sb, "timeMs", elapsedMs, true);
        field(sb, "configHash", configHash, true);
        number(sb, "submittedAt", submittedAt, false);
        sb.append("}");
        return sb.toString();
    }

    private static void field(StringBuilder sb, String key, String value, boolean comma) {
        sb.append("\"").append(escape(key)).append("\":\"").append(escape(value)).append("\"");
        if (comma) sb.append(",");
    }

    private static void number(StringBuilder sb, String key, long value, boolean comma) {
        sb.append("\"").append(escape(key)).append("\":").append(value);
        if (comma) sb.append(",");
    }

    private static String normalize(String value) {
        String v = trim(value);
        while (v.endsWith("/")) v = v.substring(0, v.length() - 1);
        return v;
    }

    private static String trim(String value) {
        return value == null ? "" : value.trim();
    }

    private static String escape(String value) {
        if (value == null) return "";
        return value.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }
}
