package me.monstermaze.world;

import me.monstermaze.MonsterMazePlugin;
import me.monstermaze.game.GameState;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.command.CommandSender;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.server.ServerCommandEvent;

/** Adds /mm map and /mm maps without changing the existing command executor. */
public final class MapCommandListener implements Listener {
    private final MonsterMazePlugin plugin;

    public MapCommandListener(MonsterMazePlugin plugin) {
        this.plugin = plugin;
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerCommand(PlayerCommandPreprocessEvent event) {
        String command = event.getMessage();
        if (command.startsWith("/")) command = command.substring(1);
        if (handle(event.getPlayer(), command)) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onServerCommand(ServerCommandEvent event) {
        if (handle(event.getSender(), event.getCommand())) event.setCancelled(true);
    }

    private boolean handle(CommandSender sender, String raw) {
        String[] args = raw.trim().split("\\s+");
        if (args.length == 0 || !args[0].equalsIgnoreCase("mm")) return false;
        if (args.length < 2 || (!args[1].equalsIgnoreCase("map") && !args[1].equalsIgnoreCase("maps"))) return false;

        MapManager maps = plugin.getMapManager();
        if (args[1].equalsIgnoreCase("maps")) {
            sender.sendMessage(ChatColor.GOLD + "=== Monster Maze Maps ===");
            for (String map : maps.knownMaps()) {
                String marker = map.equalsIgnoreCase(maps.getActiveMap())
                        ? ChatColor.GREEN + " > " : ChatColor.GRAY + "   ";
                sender.sendMessage(marker + map);
            }
            return true;
        }

        if (args.length < 3) {
            sender.sendMessage(ChatColor.AQUA + "Current map: " + ChatColor.WHITE + maps.getActiveMap());
            sender.sendMessage(ChatColor.GRAY + "Usage: /mm map <name>");
            sender.sendMessage(ChatColor.GRAY + "Available: " + String.join(", ", maps.knownMaps()));
            return true;
        }
        if (!sender.hasPermission("monstermaze.admin")) {
            sender.sendMessage(ChatColor.RED + "No permission.");
            return true;
        }
        if (plugin.getGameManager().getState() != GameState.IDLE
                && plugin.getGameManager().getState() != GameState.ENDING) {
            sender.sendMessage(ChatColor.RED + "Change maps when no game is running.");
            return true;
        }

        String map = args[2].toLowerCase();
        if (!maps.setActiveMap(map)) {
            sender.sendMessage(ChatColor.RED + "Unknown map '" + map + "'. Available: "
                    + String.join(", ", maps.knownMaps()));
            return true;
        }

        Location center = maps.defaultCenter();
        if (center == null) {
            sender.sendMessage(ChatColor.RED + "Map '" + map
                    + "' has no available world. Is its world folder installed?");
            return true;
        }

        plugin.getGameManager().getMonsterManager().setMobType(maps.activeMob());
        plugin.getGameManager().setCenter(center);
        sender.sendMessage(ChatColor.GREEN + "Active map set to " + map + ".");
        sender.sendMessage(ChatColor.GRAY + "World: " + center.getWorld().getName()
                + " | Center: " + center.getBlockX() + ", " + center.getBlockY() + ", " + center.getBlockZ());
        return true;
    }
}
