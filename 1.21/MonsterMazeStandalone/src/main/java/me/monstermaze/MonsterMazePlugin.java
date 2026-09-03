package me.monstermaze;

import me.monstermaze.command.MMCommand;
import me.monstermaze.command.MMUtilityCommandListener;
import me.monstermaze.entity.MonsterDisguiseListener;
import me.monstermaze.entity.MonsterEntityListener;
import me.monstermaze.game.BuildBypassListener;
import me.monstermaze.game.GameManager;
import me.monstermaze.game.LobbyListener;
import me.monstermaze.game.MazeMode;
import me.monstermaze.stats.BackendClient;
import me.monstermaze.stats.RunRecorder;
import me.monstermaze.stats.SoloRunCompletionListener;
import me.monstermaze.world.MapCommandListener;
import me.monstermaze.world.MapManager;
import me.monstermaze.world.MapThemeApplier;
import me.monstermaze.world.MapWorldSafetyListener;
import me.monstermaze.world.SoloPBCommandListener;
import me.monstermaze.world.VoidWorldManager;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;

public class MonsterMazePlugin extends JavaPlugin {
    private static MonsterMazePlugin instance;
    private GameManager gameManager;
    private VoidWorldManager voidWorlds;
    private MapManager mapManager;
    private MapThemeApplier mapThemeApplier;
    private MapWorldSafetyListener mapWorldSafetyListener;
    private MazeMode mode = MazeMode.ORIGINAL;
    private me.monstermaze.stats.LeaderboardManager leaderboards;
    private boolean soloMode;
    private boolean recordRuns;
    private BackendClient backendClient;
    private RunRecorder runRecorder;
    private SoloRunCompletionListener soloRunCompletionListener;
    private MonsterEntityListener monsterEntityListener;
    private MonsterDisguiseListener monsterDisguiseListener;

    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();
        this.backendClient = new BackendClient(this);
        this.voidWorlds = new VoidWorldManager(this);
        this.mapManager = new MapManager(this);
        this.mapThemeApplier = new MapThemeApplier(this);
        this.mapWorldSafetyListener = new MapWorldSafetyListener(this);
        this.leaderboards = new me.monstermaze.stats.LeaderboardManager(this);
        this.runRecorder = new RunRecorder(this);

        FileConfiguration cfg = getConfig();
        MazeMode stored = MazeMode.byName(cfg.getString("mode", "Original"));
        if (stored == null) stored = MazeMode.ORIGINAL;
        this.mode = stored;
        this.soloMode = cfg.getBoolean("solo-mode", false);
        this.recordRuns = cfg.getBoolean("record-runs", true);
        if (!cfg.contains("forced-pattern")) {
            cfg.set("forced-pattern", -1);
            saveConfig();
        }

        for (MazeMode m : MazeMode.values()) {
            try {
                if (getResource("modes/" + m.id + ".txt") != null) {
                    File dest = new File(getDataFolder(), "modes/" + m.id + ".txt");
                    if (!dest.exists()) {
                        dest.getParentFile().mkdirs();
                        saveResource("modes/" + m.id + ".txt", false);
                    }
                }
            } catch (Exception ignored) { }
        }

        Location mapCenter = mapManager.defaultCenter();
        if (mapCenter == null) {
            getLogger().warning("Active map '" + mapManager.getActiveMap() + "' has no available world; falling back to mm_void.");
            voidWorlds.ensureWorld();
            mapCenter = voidWorlds.lobbySpawn();
        }

        this.gameManager = new GameManager(this);
        this.gameManager.getMonsterManager().setMobType(mapManager.activeMob());
        this.monsterEntityListener = new MonsterEntityListener(this, gameManager);
        if (Bukkit.getPluginManager().getPlugin("ProtocolLib") != null) {
            try {
                this.monsterDisguiseListener = new MonsterDisguiseListener(this);
                getLogger().info("ProtocolLib detected; Monster Maze ghost-mob disguises enabled.");
            } catch (Throwable t) {
                getLogger().warning("ProtocolLib is present but ghost-mob disguises could not be enabled: " + t.getClass().getSimpleName());
            }
        } else {
            getLogger().warning("ProtocolLib not installed; Monster Maze mobs will render as Snow Golems.");
        }
        this.mapThemeApplier.start();
        this.soloRunCompletionListener = new SoloRunCompletionListener(this);
        gameManager.bootstrapLobby(mapCenter);

        new LobbyListener(this, gameManager, voidWorlds);
        new MapCommandListener(this);
        new SoloPBCommandListener(this);
        new BuildBypassListener(this);
        new MMUtilityCommandListener(this);
        getCommand("mm").setExecutor(new MMCommand(this));

        Bukkit.getScheduler().runTaskLater(this, new Runnable() {
            @Override public void run() {
                for (Player p : Bukkit.getOnlinePlayers()) gameManager.sendToLobby(p);
            }
        }, 20L);

        getLogger().info("MonsterMazeStandalone enabled.");
        getLogger().info("Active map: " + mapManager.getActiveMap());
        getLogger().info("Solo mode: " + soloMode);
        getLogger().info("Run recording: " + recordRuns);
        getLogger().info("Run backend: " + (backendClient.isEnabled() ? "enabled" : "Solo/local webhook mode"));
        getLogger().info("Players join into the active map lobby. Admin: /mm start");
    }

    @Override public void onDisable() {
        if (soloRunCompletionListener != null) soloRunCompletionListener.shutdown();
        if (gameManager != null) gameManager.forceStop();
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
    public MapThemeApplier getMapThemeApplier() { return mapThemeApplier; }
    public me.monstermaze.stats.LeaderboardManager getLeaderboards() { return leaderboards; }
    public boolean isSoloMode() { return soloMode; }
    public boolean isRecordRuns() { return recordRuns; }
    public BackendClient getBackendClient() { return backendClient; }
    public RunRecorder getRunRecorder() { return runRecorder; }
}