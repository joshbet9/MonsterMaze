package me.monstermaze;

import me.monstermaze.command.MMCommand;
import me.monstermaze.command.MMUtilityCommandListener;
import me.monstermaze.entity.MonsterEntityListener;
import me.monstermaze.game.BuildBypassListener;
import me.monstermaze.game.GameManager;
import me.monstermaze.game.LobbyListener;
import me.monstermaze.game.MazeMode;
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
    private RunRecorder runRecorder;
    private SoloRunCompletionListener soloRunCompletionListener;
    private MonsterEntityListener monsterEntityListener;

    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();
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
        getLogger().info("Players join into the active map lobby. Admin: /mm start");
    }

    @Override
    public void onDisable() {
        if (monsterEntityListener != null) monsterEntityListener.shutdown();
        if (soloRunCompletionListener != null) soloRunCompletionListener.shutdown();
        if (mapThemeApplier != null) mapThemeApplier.stop();
        if (mapWorldSafetyListener != null) mapWorldSafetyListener.shutdown();
        if (gameManager != null) gameManager.forceStop();
        getLogger().info("MonsterMazeStandalone disabled.");
    }

    public static MonsterMazePlugin getInstance() { return instance; }
    public GameManager getGameManager() { return gameManager; }
    public MazeMode getMode() { return mode; }

    public MazeMode setMode(MazeMode newMode) {
        if (newMode == null) newMode = MazeMode.ORIGINAL;
        this.mode = newMode;
        getConfig().set("mode", newMode.id);
        saveConfig();
        return newMode;
    }

    public VoidWorldManager getVoidWorlds() { return voidWorlds; }
    public MapManager getMapManager() { return mapManager; }
    public MapThemeApplier getMapThemeApplier() { return mapThemeApplier; }
    public me.monstermaze.stats.LeaderboardManager getLeaderboards() { return leaderboards; }
    public boolean isSoloMode() { return soloMode; }
    public RunRecorder getRunRecorder() { return runRecorder; }
}
