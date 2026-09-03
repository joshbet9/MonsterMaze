package me.monstermaze;

import me.monstermaze.command.MMCommand;
import me.monstermaze.command.PerfTestCommand;
import me.monstermaze.command.TickCpuProfilerCommand;
import me.monstermaze.game.GameManager;
import me.monstermaze.game.LobbyListener;
import me.monstermaze.game.MazeMode;
import me.monstermaze.util.UtilEnt;
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
    private MazeMode mode = MazeMode.ORIGINAL;
    private me.monstermaze.stats.LeaderboardManager leaderboards;

    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();
        this.voidWorlds = new VoidWorldManager(this);
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

        // Map AddonGhostSnowman -> snowman id so the client renders maze monsters correctly.
        UtilEnt.registerGhostSnowmanEntityType();

        // Always use void world as the play space
        voidWorlds.ensureWorld();
        this.gameManager = new GameManager(this);

        // Lobby at void spawn + kit NPCs
        Location voidSpawn = voidWorlds.lobbySpawn();
        gameManager.bootstrapLobby(voidSpawn);

        new LobbyListener(this, gameManager, voidWorlds);
        getCommand("mm").setExecutor(new MMCommand(this));
        getCommand("perftest").setExecutor(new PerfTestCommand(this, gameManager));
        getCommand("tickprofile").setExecutor(new TickCpuProfilerCommand(this, gameManager));

        // Move anyone already online into the void lobby
        Bukkit.getScheduler().runTaskLater(this, new Runnable() {
            @Override
            public void run() {
                for (Player p : Bukkit.getOnlinePlayers()) {
                    gameManager.sendToLobby(p);
                }
            }
        }, 20L);

        getLogger().info("MonsterMazeStandalone enabled.");
        getLogger().info("Players join into mm_void lobby. Admin: /mm start");
    }

    @Override
    public void onDisable() {
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

    public me.monstermaze.stats.LeaderboardManager getLeaderboards() {
        return leaderboards;
    }
}
