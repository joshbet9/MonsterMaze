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

/**
 * Emits a completed solo run as a JSON record to the data folder (solo-runs/).
 *
 * This is intentionally lightweight and trust-based: it captures the facts of a
 * finished run (mode, pattern, kit, stage reached, elapsed time, config hash) for
 * crowd-sourced leaderboards. It does NOT record a replay or any anti-cheat proof.
 *
 * The solo launcher's submitter watches solo-runs/ and forwards records to whatever
 * central leaderboard endpoint is configured.
 */
public class RunRecorder {

    private final MonsterMazePlugin plugin;
    private final File folder;

    public RunRecorder(MonsterMazePlugin plugin) {
        this.plugin = plugin;
        this.folder = new File(plugin.getDataFolder(), "solo-runs");
    }

    /**
     * Record a finished solo run.
     *
     * @param player   the solo player
     * @param mode     game mode
     * @param pattern  maze pattern index, or -1 if unknown
     * @param kit      kit id used, or null
     * @param stage    highest stage reached (curSafe)
     * @param elapsedMs run duration in milliseconds
     */
    public void record(Player player, MazeMode mode, int pattern, String kit,
                       int stage, long elapsedMs) {
        if (!plugin.isSoloMode()) return;
        if (player == null || mode == null) return;
        writeRecord(player, mode, pattern, kit, stage, elapsedMs);
    }

    /**
     * Re-export an already persisted personal best as a submission record.
     * The leaderboard file does not retain the original run time, so historical
     * exports use timeMs=0 while preserving the authoritative mode/pattern/kit/stage.
     */
    public boolean recordHistorical(Player player, MazeMode mode, int pattern, String kit, int stage) {
        if (!plugin.isSoloMode()) return false;
        if (player == null || mode == null || pattern < 0 || stage < 1 || kit == null || kit.isEmpty()) return false;
        writeRecord(player, mode, pattern, kit, stage, 0L);
        return true;
    }

    private void writeRecord(Player player, MazeMode mode, int pattern, String kit,
                              int stage, long elapsedMs) {
        if (!folder.exists()) {
            folder.mkdirs();
        }

        String name = player.getName();
        UUID uuid = player.getUniqueId();
        String pluginVer = plugin.getDescription() != null
                ? plugin.getDescription().getVersion() : "0.0.0";
        String configHash = configHash();

        // Build the JSON from labelled pairs. Comma placement is decided by
        // position (append one except after the last), so it can never be wrong.
        StringBuilder sb = new StringBuilder();
        sb.append("{");
        sb.append(entry("schema", "1"));
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
        sb.append("}");

        String fileName = uuid.toString().substring(0, 8) + "-" + System.currentTimeMillis() + ".json";
        File out = new File(folder, fileName);
        try {
            OutputStreamWriter w = new OutputStreamWriter(
                    new FileOutputStream(out), StandardCharsets.UTF_8);
            w.write(sb.toString());
            w.close();
            plugin.getLogger().info("Solo run recorded: " + out.getName()
                    + " (mode=" + mode.id + " stage=" + stage + " time=" + elapsedMs + "ms)");
        } catch (IOException e) {
            plugin.getLogger().warning("Could not write solo run record: " + e.getMessage());
        }
    }

    /** A string-value JSON entry (key, comma, value, newline). */
    private static String entry(String key, String val) {
        return "  \"" + key + "\": \"" + escape(val) + "\",\n";
    }

    /** A numeric-value JSON entry (values are already non-quoted strings). */
    private static String rawEntry(String key, String val) {
        return "  \"" + key + "\": " + val + ",\n";
    }

    /** The final entry (no trailing comma). */
    private static String lastEntry(String key, String val) {
        return "  \"" + key + "\": " + val + "\n";
    }

    private static String escape(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    /**
     * A short stable hash of the plugin config (mode + solo settings). Lets the
     * leaderboard detect a participant running a divergent (e.g. timer-cheated)
     * config so it can be filtered from the board without accusing anyone of cheating.
     */
    private String configHash() {
        String cfg = plugin.getConfig().saveToString();
        return Integer.toHexString(cfg.hashCode());
    }
}
