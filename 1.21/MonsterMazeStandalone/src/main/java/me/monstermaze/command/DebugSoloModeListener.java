package me.monstermaze.command;

import me.monstermaze.MonsterMazePlugin;
import me.monstermaze.game.GameManager;
import org.bukkit.ChatColor;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;

public class DebugSoloModeListener implements Listener {
    private final MonsterMazePlugin plugin;
    public DebugSoloModeListener(MonsterMazePlugin plugin) { this.plugin = plugin; }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        String[] parts = event.getMessage().trim().split("\\s+");
        if (parts.length < 2 || !parts[0].equalsIgnoreCase("/mm") || !parts[1].equalsIgnoreCase("solomode")) return;
        event.setCancelled(true);
        if (!plugin.isDebug()) { event.getPlayer().sendMessage(ChatColor.RED + "Debug commands are disabled on this server."); return; }
        if (parts.length < 3 || !(parts[2].equalsIgnoreCase("true") || parts[2].equalsIgnoreCase("false"))) {
            event.getPlayer().sendMessage(ChatColor.YELLOW + "Usage: /mm solomode <true|false>");
            event.getPlayer().sendMessage(ChatColor.AQUA + "Current: " + ChatColor.WHITE + plugin.isSoloMode());
            return;
        }
        GameManager gm = plugin.getGameManager();
        if (gm != null && gm.isRunning()) { event.getPlayer().sendMessage(ChatColor.RED + "Change solo mode when no game is running."); return; }
        boolean enabled = Boolean.parseBoolean(parts[2]);
        plugin.setSoloMode(enabled);
        event.getPlayer().sendMessage(ChatColor.GREEN + "Solo mode set to " + ChatColor.WHITE + enabled + ChatColor.GREEN + ".");
    }
}
