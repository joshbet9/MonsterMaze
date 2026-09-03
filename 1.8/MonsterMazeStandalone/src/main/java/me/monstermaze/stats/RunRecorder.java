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

/** Emits a completed solo run as a JSON record to the data folder (solo-runs/). */
public class RunRecorder {

    private static final String PLATFORM = "1.8";

    private final MonsterMazePlugin plugin;
    private final File folder;

    public RunRecorder(MonsterMazePlugin plugin) {
        this.plugin = plugin;
        this.folder = new File(plugin.getDataFolder(), "solo-runs");
    }

    public void record(Player player, MazeMode mode, int pattern, String kit,
                       int stage, long elapsedMs) {
        if (!plugin.isSoloMode()) return;
        if (player == null || mode == null) return;
        writeRecord(player, mode, pattern, kit, stage, elapsedMs);
    }

    public boolean recordHistorical(Player player, MazeMode mode, int pattern, String kit, int stage) {
        if (!plugin.isSoloMode()) return false;
        if (player == null || mode == null || pattern < 0 || stage < 1 || kit == null || kit.isEmpty()) return false;
        return writeRecord(player, mode, pattern, kit, stage, 0L);
    }

    private boolean writeRecord(Player player, MazeMode mode, int pattern, String kit,
                                int stage, long elapsedMs) {
        if (!folder.exists() && !folder.mkdirs()) {
            plugin.getLogger().warning("Could not create solo-runs directory.");
            return false;
        }

        String name = player.getName();
        UUID uuid = player.getUniqueId();
        String pluginVer = plugin.getDescription() != null
                ? plugin.getDescription().getVersion() : "0.0.0";
        String configHash = Integer.toHexString(plugin.getConfig().saveToString().hashCode());

        StringBuilder sb = new StringBuilder();
        sb.append("{\n");
        sb.append(entry("schema", "1"));
        sb.append(entry("platform", PLATFORM));
        sb.append(entry("plugin", pluginVer));
        sb.append(entry("name", name));
        sb.append(entry("uuid", uuid.toString()));
        sb.append(entry("mode", mode.id));
        sb.append(rawEntry("pattern", String.valueOf(pattern)));
        sb.append(entry("kit", kit != null ? kit : ""));
        sb.append(rawEntry("stage", String.valueOf(stage)));
        sb.append(rawEntry("timeMs", String.valueOf(elapsedMs)));
        sb.append(entry("configHash", configHash));
        sb.append(lastEntry("submittedAt", String.valueOf(System.currentTimeMillis())));
        sb.append("}\n");

        File out = new File(folder, uuid.toString().substring(0, 8) + "-" + System.currentTimeMillis() + ".json");
        try {
            OutputStreamWriter writer = new OutputStreamWriter(new FileOutputStream(out), StandardCharsets.UTF_8);
            writer.write(sb.toString());
            writer.close();
            plugin.getLogger().info("Solo run recorded: " + out.getName()
                    + " (platform=" + PLATFORM + " mode=" + mode.id + " stage=" + stage + " time=" + elapsedMs + "ms)");
            return true;
        } catch (IOException e) {
            plugin.getLogger().warning("Could not write solo run record: " + e.getMessage());
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
