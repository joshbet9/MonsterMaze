package me.monstermaze;

import me.monstermaze.command.MMCommand;
import me.monstermaze.command.MMDebugCommand;
import me.monstermaze.game.BuildBypassListener;
import me.monstermaze.game.GameManager;
import me.monstermaze.game.LobbyListener;
import me.monstermaze.game.MazeMode;
import me.monstermaze.stats.BackendClient;
import me.monstermaze.stats.ChallengeManager;
import me.monstermaze.stats.RunRecorder;
import me.monstermaze.stats.SoloRunCompletionListener;
import me.monstermaze.util.UtilEnt;
import me.monstermaze.world.MapManager;
import me.monstermaze.world.VoidWorldManager;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;

public class MonsterMazePlugin extends JavaPlugin {
    private static MonsterMazePlugin instance;
    private GameManager gameManager;
    private VoidWorldManager voidWorlds;
    private MapManager mapManager;
    private MazeMode mode = MazeMode.ORIGINAL;
    private me.monstermaze.stats.LeaderboardManager leaderboards;
    private boolean soloMode;
    private boolean recordRuns;
    private BackendClient backendClient;
    private RunRecorder runRecorder;
    private SoloRunCompletionListener soloRunCompletionListener;
    private ChallengeManager challengeManager;

    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();
        this.backendClient = new BackendClient(this);
        this.voidWorlds = new VoidWorldManager(this);
        this.mapManager = new MapManager(this, voidWorlds);
        this.leaderboards = new me.monstermaze.stats.LeaderboardManager(this);
        this.challengeManager = new ChallengeManager(this);
        FileConfiguration cfg = getConfig();
        MazeMode stored = MazeMode.byName(cfg.getString("mode", "Original"));
        if (stored == null) stored = MazeMode.ORIGINAL;
        this.mode = stored;
        this.soloMode = cfg.getBoolean("solo-mode", false);
        this.recordRuns = cfg.getBoolean("record-runs", true);
        this.runRecorder = new RunRecorder(this);
        for (MazeMode m : MazeMode.values()) {
            try {
                if (getResource("modes/" + m.id + ".txt") != null) {
                    File dest = new File(getDataFolder(), "modes/" + m.id + ".txt");
                    if (!dest.exists()) { dest.getParentFile().mkdirs(); saveResource("modes/" + m.id + ".txt", false); }
                }
            } catch (Exception ignored) { }
        }
        UtilEnt.registerGhostTypes();
        mapManager.loadActiveMapFromConfig();
        mapManager.ensureActiveWorld();
        this.gameManager = new GameManager(this);
        this.soloRunCompletionListener = new SoloRunCompletionListener(this);
        gameManager.applyMap();
        new LobbyListener(this, gameManager, voidWorlds);
        new BuildBypassListener(this);
        getCommand("mm").setExecutor(new MMCommand(this));
        getCommand("mmdebug").setExecutor(new MMDebugCommand(this));
        Bukkit.getScheduler().runTaskLater(this, new Runnable() {
            @Override public void run() {
                for (Player p : Bukkit.getOnlinePlayers()) gameManager.sendToLobby(p);
            }
        }, 20L);
        getLogger().info("MonsterMazeStandalone enabled ('" + mapManager.getActiveMap() + "' map).");
        getLogger().info("Players join into the lobby. Admin: /mm start");
        getLogger().info("Run backend: " + (backendClient.isEnabled() ? "enabled" : "Solo/local webhook mode"));
    }

    @Override public void onDisable() {
        if (soloRunCompletionListener != null) soloRunCompletionListener.shutdown();
        if (gameManager != null) gameManager.forceStop();
        getLogger().info("MonsterMazeStandalone disabled.");
    }
    public static MonsterMazePlugin getInstance() { return instance; }
    public GameManager getGameManager() { return gameManager; }
    public MazeMode getMode() { return mode; }
    public MazeMode setMode(MazeMode newMode) {
        if (newMode == null) newMode = MazeMode.ORIGINAL;
        this.mode = newMode; getConfig().set("mode", newMode.id); saveConfig(); return newMode;
    }
    public VoidWorldManager getVoidWorlds() { return voidWorlds; }
    public MapManager getMapManager() { return mapManager; }
    public me.monstermaze.stats.LeaderboardManager getLeaderboards() { return leaderboards; }
    public ChallengeManager getChallengeManager() { return challengeManager; }
    public boolean isSoloMode() { return soloMode; }
    public boolean isRecordRuns() { return recordRuns; }
    public BackendClient getBackendClient() { return backendClient; }
    public RunRecorder getRunRecorder() { return runRecorder; }
}