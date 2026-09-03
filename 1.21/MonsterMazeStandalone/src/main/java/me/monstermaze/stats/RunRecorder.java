package me.monstermaze.stats;

import me.monstermaze.MonsterMazePlugin;
import me.monstermaze.game.MazeMode;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

/** Emits completed player runs locally for Solo installs or directly to the backend on public servers. */
public class RunRecorder {

    private static final String PLATFORM = "1.21";

    private final MonsterMazePlugin plugin;
    private final File folder;
    private final BackendClient backend;

    public RunRecorder(MonsterMazePlugin plugin) {
        this.plugin = plugin;
        this.folder = new File(plugin.getDataFolder(), "solo-runs");
        this.backend = new BackendClient(plugin);
    }

    public boolean isBackendEnabled() { return backend.isEnabled(); }

    public void record(Player player, MazeMode mode, int pattern, String kit,
                       int stage, long elapsedMs) {
        if (!plugin.isRecordRuns()) return;
        if (player == null || mode == null || pattern < 0 || stage < 1 || kit == null || kit.isEmpty()) return;

        UUID uuid = player.getUniqueId();
        String pluginVer = plugin.getDescription() != null ? plugin.getDescription().getVersion() : "0.0.0";
        String configHash = Integer.toHexString(plugin.getConfig().saveToString().hashCode());
        long submittedAt = System.currentTimeMillis();
        String submissionId = uuid.toString() + "-" + submittedAt + "-" + stage + "-" + pattern + "-" + kit;

        if (backend.isEnabled()) {
            backend.submit(submissionId, uuid, player.getName(), mode.id, pattern, kit, stage,
                    elapsedMs, PLATFORM, pluginVer, configHash, submittedAt);
            return;
        }

        writeRecord(submissionId, player, mode, pattern, kit, stage, elapsedMs, pluginVer, configHash, submittedAt);
    }

    /** Re-export an existing PB. Solo distributions normally use this path because no backend is configured. */
    public boolean recordHistorical(Player player, MazeMode mode, int pattern, String kit, int stage) {
        if (!plugin.isRecordRuns()) return false;
        if (player == null || mode == null || pattern < 0 || stage < 1 || kit == null || kit.isEmpty()) return false;
        UUID uuid = player.getUniqueId();
        String pluginVer = plugin.getDescription() != null ? plugin.getDescription().getVersion() : "0.0.0";
        String configHash = Integer.toHexString(plugin.getConfig().saveToString().hashCode());
        long submittedAt = System.currentTimeMillis();
        String submissionId = uuid.toString() + "-historical-" + submittedAt + "-" + stage + "-" + pattern + "-" + kit;
        if (backend.isEnabled()) {
            backend.submit(submissionId, uuid, player.getName(), mode.id, pattern, kit, stage,
                    0L, PLATFORM, pluginVer, configHash, submittedAt);
            return true;
        }
        return writeRecord(submissionId, player, mode, pattern, kit, stage, 0L, pluginVer, configHash, submittedAt);
    }

    private boolean writeRecord(String submissionId, Player player, MazeMode mode, int pattern, String kit,
                                int stage, long elapsedMs, String pluginVer, String configHash,
                                long submittedAt) {
        if (!folder.exists() && !folder.mkdirs()) {
            plugin.getLogger().warning("Could not create solo-runs directory.");
            return false;
        }

        String name = player.getName();
        UUID uuid = player.getUniqueId();
        StringBuilder sb = new StringBuilder();
        sb.append("{\n");
        sb.append(entry("schema", "1"));
        sb.append(entry("submissionId", submissionId));
        sb.append(entry("platform", PLATFORM));
        sb.append(entry("plugin", pluginVer));
        sb.append(entry("name", name));
        sb.append(entry("uuid", uuid.toString()));
        sb.append(entry("mode", mode.id));
        sb.append(rawEntry("pattern", String.valueOf(pattern)));
        sb.append(entry("kit", kit));
        sb.append(rawEntry("stage", String.valueOf(stage)));
        sb.append(rawEntry("timeMs", String.valueOf(elapsedMs)));
        sb.append(entry("configHash", configHash));
        sb.append(lastEntry("submittedAt", String.valueOf(submittedAt)));
        sb.append("}\n");

        File out = new File(folder, uuid.toString().substring(0, 8) + "-" + submittedAt + ".json");
        try {
            OutputStreamWriter writer = new OutputStreamWriter(new FileOutputStream(out), StandardCharsets.UTF_8);
            try {
                writer.write(sb.toString());
            } finally {
                writer.close();
            }
            plugin.getLogger().info("Run recorded: " + out.getName()
                    + " (platform=" + PLATFORM + " mode=" + mode.id + " stage=" + stage + " time=" + elapsedMs + "ms)");
            return true;
        } catch (IOException e) {
            plugin.getLogger().warning("Could not write run record: " + e.getMessage());
            return false;
        }
    }

    private static String entry(String key, String value) {
        return "  \"" + key + "\": \"" + escape(value) + "\",\n";
    }

    private static String rawEntry(String key, String value) {
        return "  \"" + key + "\": " + value + ",\n";
    }

    private static String lastEntry(String key, String value) {
        return "  \"" + key + "\": " + value + "\n";
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
