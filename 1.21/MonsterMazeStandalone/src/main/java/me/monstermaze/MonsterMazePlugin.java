package me.monstermaze;

import me.monstermaze.command.MMCommand;
import me.monstermaze.game.GameManager;
import me.monstermaze.game.LobbyListener;
import me.monstermaze.game.MazeMode;
import me.monstermaze.world.MapManager;
import me.monstermaze.world.MapThemeApplier;
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
    private MazeMode mode = MazeMode.ORIGINAL;
    private me.monstermaze.stats.LeaderboardManager leaderboards;

    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();
        this.voidWorlds = new VoidWorldManager(this);
        this.mapManager = new MapManager(this);
        this.mapThemeApplier = new MapThemeApplier(this);
        this.leaderboards = new me.monstermaze.stats.LeaderboardManager(this);

        FileConfiguration cfg = getConfig();
        MazeMode stored = MazeMode.byName(cfg.getString("mode", "Original"));
        if (stored == null) stored = MazeMode.ORIGINAL;
        this.mode = stored;

        // Extract per-mode change documents (txt) into the data folder.
        for (MazeMode m : MazeMode.values()) {
            try {
                if (getResource("modes/" + m.id + ".txt") != null) {
                    File dest = new File(getDataFolder(), "modes/" + m.id + ".txt");
                    if (!dest.exists()) {
                        dest.getParentFile().mkdirs();
                        saveResource("modes/" + m.id + ".txt", false);
                    }
                }
            } catch (Exception ignored) {
            }
        }

        // The active map determines the arena world. Eye of Ender uses mm_void;
        // the other maps load their staged Mineplex world folder.
        Location mapCenter = mapManager.defaultCenter();
        if (mapCenter == null) {
            getLogger().warning("Active map '" + mapManager.getActiveMap()
                    + "' has no available world; falling back to mm_void.");
            voidWorlds.ensureWorld();
            mapCenter = voidWorlds.lobbySpawn();
        }

        this.gameManager = new GameManager(this);
        this.mapThemeApplier.start();

        // Lobby at the active map's configured center + kit NPCs
        gameManager.bootstrapLobby(mapCenter);

        new LobbyListener(this, gameManager, voidWorlds);
        new me.monstermaze.world.MapCommandListener(this);
        getCommand("mm").setExecutor(new MMCommand(this));

        // Move anyone already online into the active map lobby
        Bukkit.getScheduler().runTaskLater(this, new Runnable() {
            @Override
            public void run() {
                for (Player p : Bukkit.getOnlinePlayers()) {
                    gameManager.sendToLobby(p);
                }
            }
        }, 20L);

        getLogger().info("MonsterMazeStandalone enabled.");
        getLogger().info("Active map: " + mapManager.getActiveMap());
        getLogger().info("Players join into the active map lobby. Admin: /mm start");
    }

    @Override
    public void onDisable() {
        if (mapThemeApplier != null) mapThemeApplier.stop();
        if (gameManager != null) {
            gameManager.forceStop();
        }
        getLogger().info("MonsterMazeStandalone disabled.");
    }

    public static MonsterMazePlugin getInstance() {
        return instance;
    }

    public GameManager getGameManager() {
        return gameManager;
    }

    public MazeMode getMode() {
        return mode;
    }

    /** Set and persist the active game mode. Returns the effective mode (null input -> Original). */
    public MazeMode setMode(MazeMode newMode) {
        if (newMode == null) newMode = MazeMode.ORIGINAL;
        this.mode = newMode;
        getConfig().set("mode", newMode.id);
        saveConfig();
        return newMode;
    }

    public VoidWorldManager getVoidWorlds() {
        return voidWorlds;
    }

    public MapManager getMapManager() {
        return mapManager;
    }

    public me.monstermaze.stats.LeaderboardManager getLeaderboards() {
        return leaderboards;
    }
}
