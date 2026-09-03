package me.monstermaze.world;

import me.monstermaze.MonsterMazePlugin;
import me.monstermaze.game.MazeMode;
import me.monstermaze.kit.KitType;
import me.monstermaze.stats.LeaderboardManager;
import me.monstermaze.stats.RunRecorder;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.server.ServerCommandEvent;

/** Modern equivalent of the 1.8 /mm exportpbs command without changing the main command executor. */
public final class SoloPBCommandListener implements Listener {

    private final MonsterMazePlugin plugin;

    public SoloPBCommandListener(MonsterMazePlugin plugin) {
        this.plugin = plugin;
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerCommand(PlayerCommandPreprocessEvent event) {
        String command = event.getMessage();
        if (command.startsWith("/")) command = command.substring(1);
        if (isExportCommand(command)) {
            exportPBs(event.getPlayer());
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onServerCommand(ServerCommandEvent event) {
        if (!isExportCommand(event.getCommand())) return;
        if (event.getSender() instanceof Player) exportPBs((Player) event.getSender());
        else event.getSender().sendMessage(ChatColor.RED + "Players only.");
        event.setCancelled(true);
    }

    private boolean isExportCommand(String raw) {
        String[] args = raw.trim().split("\\s+");
        return args.length >= 2 && args[0].equalsIgnoreCase("mm")
                && (args[1].equalsIgnoreCase("exportpbs")
                || args[1].equalsIgnoreCase("exportpb")
                || args[1].equalsIgnoreCase("export"));
    }

    private void exportPBs(Player player) {
        if (!plugin.isSoloMode()) {
            player.sendMessage(ChatColor.RED + "PB export is only available in Solo Mode.");
            return;
        }

        LeaderboardManager lb = plugin.getLeaderboards();
        RunRecorder recorder = plugin.getRunRecorder();
        int exported = 0;

        for (MazeMode mode : MazeMode.values()) {
            for (int pattern = 0; pattern < LeaderboardManager.PATTERN_COUNT; pattern++) {
                for (KitType kit : KitType.values()) {
                    int stage = lb.getKitPB(mode, pattern, player.getUniqueId(), kit.id);
                    if (stage < 1) continue;
                    if (recorder.recordHistorical(player, mode, pattern, kit.id, stage)) exported++;
                }
            }
        }

        if (exported == 0) {
            player.sendMessage(ChatColor.YELLOW + "No stored personal bests found to export.");
        } else {
            player.sendMessage(ChatColor.GREEN + "Exported " + exported
                    + " stored personal best(s) for submission.");
            player.sendMessage(ChatColor.GRAY + "Run the Solo submitter to send them to Discord.");
        }
    }
}
