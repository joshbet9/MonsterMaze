package me.monstermaze.game;

import me.monstermaze.MonsterMazePlugin;
import me.monstermaze.world.VoidWorldManager;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerRespawnEvent;

/** Routes idle players through the isolated game selector while preserving the original MM lobby. */
public class LobbyListener implements Listener {
    private final MonsterMazePlugin plugin;
    private final GameManager game;
    private final VoidWorldManager voids;
    private final SandboxGameSelector selector;

    public LobbyListener(MonsterMazePlugin plugin, GameManager game, VoidWorldManager voids) {
        this.plugin = plugin;
        this.game = game;
        this.voids = voids;
        this.selector = new SandboxGameSelector(plugin, game);
        Bukkit.getPluginManager().registerEvents(this, plugin);
        if (!plugin.isCohostedMode() && plugin.isSoloMode()) {
            new SoloIntegrityListener(plugin);
        }
        // On a shared Mineplex Reborn server, preserve its existing lobby and online players.
        if (!plugin.isCohostedMode()) {
            Bukkit.getScheduler().runTaskLater(plugin, new Runnable() {
                @Override public void run() {
                    for (Player player : Bukkit.getOnlinePlayers()) {
                        if (!player.isOnline() || game.isLive()) continue;
                        selector.sendToHub(player);
                        selector.openGameSelector(player);
                    }
                }
            }, 20L);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onJoin(PlayerJoinEvent event) {
        if (plugin.isCohostedMode()) return;
        final Player player = event.getPlayer();
        // Solo /mm commands do not require OP. Keeping the player non-op prevents
        // vanilla/admin commands from compromising the PB environment.
        if (plugin.isSoloMode() && player.isOp()) {
            player.setOp(false);
        }

        Bukkit.getScheduler().runTask(plugin, new Runnable() {
            @Override public void run() {
                if (!player.isOnline()) return;
                if (game.isLive()) {
                    // Mid-game joiners remain spectators, exactly as in the original lobby flow.
                    Location center = game.getCenter();
                    if (center != null) {
                        player.teleport(center.clone().add(0, 15, 0));
                        player.setGameMode(org.bukkit.GameMode.SPECTATOR);
                    }
                    return;
                }
                selector.sendToHub(player);
                selector.openGameSelector(player);
            }
        });
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onRespawn(PlayerRespawnEvent event) {
        if (game.isLive()) return;
        final Player player = event.getPlayer();
        Location center = game.getCenter();
        if (plugin.isCohostedMode()
                && (center == null || player.getWorld() != center.getWorld())) return;
        if (center != null && player.getWorld() == center.getWorld()) {
            // A player who died in Monster Maze still respawns in its original lobby.
            Location lobby = game.getLobbySpawn();
            if (lobby != null) event.setRespawnLocation(lobby);
            return;
        }
        Location spawn = selector.getHubSpawn();
        if (spawn != null) event.setRespawnLocation(spawn);
        Bukkit.getScheduler().runTaskLater(plugin, new Runnable() {
            @Override public void run() {
                if (!player.isOnline() || game.isLive()) return;
                selector.sendToHub(player);
                selector.openGameSelector(player);
            }
        }, 1L);
    }
}
