package me.monstermaze.game;

import me.monstermaze.MonsterMazePlugin;
import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerGameModeChangeEvent;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * Prevent player-side administrative/gameplay commands from compromising Solo PBs.
 *
 * Solo players do not need OP for /mm, so the Solo environment deliberately
 * removes OP and blocks commands that could alter the run or world state.
 */
public class SoloIntegrityListener implements Listener {

    private final MonsterMazePlugin plugin;
    private final Set<String> blockedCommands = new HashSet<String>(Arrays.asList(
            "gamemode", "defaultgamemode", "gmc", "gms", "gma", "gmsp",
            "tp", "teleport", "give", "item", "summon", "setblock", "fill", "clone",
            "effect", "enchant", "experience", "xp", "attribute", "data", "execute",
            "time", "weather", "difficulty", "kill", "spawnpoint", "setworldspawn",
            "locate", "place", "ride", "damage", "clear", "advancement", "recipe",
            "op", "deop"
    ));

    public SoloIntegrityListener(MonsterMazePlugin plugin) {
        this.plugin = plugin;
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onGameModeChange(PlayerGameModeChangeEvent event) {
        if (!plugin.isSoloMode()) return;

        // Spectator is intentionally allowed because the game can use it after death.
        // Creative and Adventure would provide capabilities that invalidate PBs.
        if (event.getNewGameMode() == GameMode.CREATIVE || event.getNewGameMode() == GameMode.ADVENTURE) {
            event.setCancelled(true);
            event.getPlayer().sendMessage(ChatColor.RED + "Solo Mode: that game mode is disabled to protect PB integrity.");
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        if (!plugin.isSoloMode()) return;

        String message = event.getMessage();
        if (message == null || message.length() <= 1) return;

        String command = message.substring(1).trim().split("\\s+", 2)[0]
                .toLowerCase(Locale.ENGLISH);
        if (command.indexOf(':') >= 0) {
            command = command.substring(command.lastIndexOf(':') + 1);
        }

        if (blockedCommands.contains(command)) {
            event.setCancelled(true);
            event.getPlayer().sendMessage(ChatColor.RED + "Solo Mode: that command is disabled to protect PB integrity.");
        }
    }
}
