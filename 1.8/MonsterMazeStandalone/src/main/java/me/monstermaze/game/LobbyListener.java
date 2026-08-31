package me.monstermaze.game;

import me.monstermaze.MonsterMazePlugin;
import me.monstermaze.world.VoidWorldManager;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerRespawnEvent;

/**
 * Always put players in the Monster Maze void lobby on join/respawn (when idle).
 */
public class LobbyListener implements Listener {

    private final MonsterMazePlugin plugin;
    private final GameManager game;
    private final VoidWorldManager voids;

    public LobbyListener(MonsterMazePlugin plugin, GameManager game, VoidWorldManager voids) {
        this.plugin = plugin;
        this.game = game;
        this.voids = voids;
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onJoin(PlayerJoinEvent event) {
        final Player player = event.getPlayer();
        // In solo mode the local player runs everything, so op them automatically
        // and point them at the (single) command they need.
        if (plugin.isSoloMode()) {
            player.setOp(true);
            Bukkit.getScheduler().runTask(plugin, new Runnable() {
                @Override
                public void run() {
                    if (!player.isOnline()) return;
                    player.sendMessage(ChatColor.GOLD + "[Solo] You are op'd. Type "
                            + ChatColor.WHITE + "/mm start" + ChatColor.GOLD
                            + " to begin, then click a kit.");
                }
            });
        }
        // Defer one tick so join completes cleanly
        Bukkit.getScheduler().runTask(plugin, new Runnable() {
            @Override
            public void run() {
                if (!player.isOnline()) return;
                if (game.isLive()) {
                    // Mid-game joiners become spectators at center
                    Location c = game.getCenter();
                    if (c != null) {
                        player.teleport(c.clone().add(0, 15, 0));
                        player.setGameMode(org.bukkit.GameMode.SPECTATOR);
                    }
                    return;
                }
                game.sendToLobby(player);
            }
        });
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onRespawn(PlayerRespawnEvent event) {
        if (game.isLive()) return;
        Location lobby = game.getLobbySpawn();
        if (lobby != null) {
            event.setRespawnLocation(lobby);
        }
    }
}
