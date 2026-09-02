package me.monstermaze;

import me.monstermaze.command.MMCommand;
import me.monstermaze.command.MMDebugCommand;
import me.monstermaze.game.BuildBypassListener;
import me.monstermaze.game.GameManager;
import me.monstermaze.game.LobbyListener;
import me.monstermaze.game.MazeMode;
import me.monstermaze.stats.RunRecorder;
import me.monstermaze.stats.SoloRunCompletionListener;
import me.monstermaze.util.PerfDiagnostics;
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
    private RunRecorder runRecorder;
    private SoloRunCompletionListener soloRunCompletionListener;
    private PerfDiagnostics perfDiagnostics;

    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();
        this.voidWorlds = new VoidWorldManager(this);
        this.mapManager = new MapManager(this, voidWorlds);
        this.leaderboards = new me.monstermaze.stats.LeaderboardManager(this);
        FileConfiguration cfg = getConfig();
        MazeMode stored = MazeMode.byName(cfg.getString("mode", "Original"));
        if (stored == null) stored = MazeMode.ORIGINAL;
        this.mode = stored;
        this.soloMode = cfg.getBoolean("solo-mode", false);
        this.runRecorder = new me.monstermaze.stats.RunRecorder(this);
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
        this.perfDiagnostics = new PerfDiagnostics(this, gameManager);
        this.perfDiagnostics.start();
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
    }

    @Override public void onDisable() {
        if (perfDiagnostics != null) perfDiagnostics.stop();
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
    public boolean isSoloMode() { return soloMode; }
    public me.monstermaze.stats.RunRecorder getRunRecorder() { return runRecorder; }
}
