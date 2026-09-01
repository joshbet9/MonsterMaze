package me.monstermaze.command;

import me.monstermaze.MonsterMazePlugin;
import me.monstermaze.game.BuildBypassListener;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.server.ServerCommandEvent;

/** Routes modern-only utility commands without changing the existing MMCommand API. */
public final class MMUtilityCommandListener implements Listener {
    private final MonsterMazePlugin plugin;
    private final MMDebugCommand debug;

    public MMUtilityCommandListener(MonsterMazePlugin plugin) {
        this.plugin = plugin;
        this.debug = new MMDebugCommand(plugin);
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerCommand(PlayerCommandPreprocessEvent event) {
        String command = event.getMessage();
        if (command.startsWith("/")) command = command.substring(1);
        String[] args = command.trim().split("\\s+");
        if (args.length < 2 || !args[0].equalsIgnoreCase("mm")) return;

        if (args[1].equalsIgnoreCase("debug")) {
            if (!event.getPlayer().hasPermission("monstermaze.admin")) {
                event.getPlayer().sendMessage(ChatColor.RED + "No permission.");
            } else {
                debug.onCommand(event.getPlayer(), null, "mm", slice(args, 2));
            }
            event.setCancelled(true);
            return;
        }

        if (args[1].equalsIgnoreCase("build") || args[1].equalsIgnoreCase("buildmode")) {
            toggleBuild(event.getPlayer());
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onServerCommand(ServerCommandEvent event) {
        String[] args = event.getCommand().trim().split("\\s+");
        if (args.length < 2 || !args[0].equalsIgnoreCase("mm")) return;
        if (args[1].equalsIgnoreCase("debug")) {
            if (!event.getSender().hasPermission("monstermaze.admin")) event.getSender().sendMessage(ChatColor.RED + "No permission.");
            else debug.onCommand(event.getSender(), null, "mm", slice(args, 2));
            event.setCancelled(true);
        } else if (args[1].equalsIgnoreCase("build") || args[1].equalsIgnoreCase("buildmode")) {
            if (event.getSender() instanceof Player) toggleBuild((Player) event.getSender());
            else event.getSender().sendMessage(ChatColor.RED + "Players only.");
            event.setCancelled(true);
        }
    }

    private void toggleBuild(Player player) {
        if (!player.hasPermission("monstermaze.admin")) {
            player.sendMessage(ChatColor.RED + "No permission.");
            return;
        }
        if (!player.isOp()) {
            player.sendMessage(ChatColor.RED + "You must be OP to use build bypass.");
            return;
        }
        if (player.hasMetadata(BuildBypassListener.METADATA)) {
            player.removeMetadata(BuildBypassListener.METADATA, plugin);
            player.sendMessage(ChatColor.YELLOW + "Monster Maze build bypass " + ChatColor.RED + "DISABLED" + ChatColor.YELLOW + ".");
        } else {
            player.setMetadata(BuildBypassListener.METADATA, new FixedMetadataValue(plugin, true));
            player.sendMessage(ChatColor.YELLOW + "Monster Maze build bypass " + ChatColor.GREEN + "ENABLED" + ChatColor.YELLOW + ". Use Creative to edit the active arena.");
        }
    }

    private String[] slice(String[] args, int from) {
        if (from >= args.length) return new String[0];
        String[] out = new String[args.length - from];
        System.arraycopy(args, from, out, 0, out.length);
        return out;
    }
}
