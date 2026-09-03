package me.monstermaze.game;

import me.monstermaze.MonsterMazePlugin;
import org.bukkit.GameMode;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.entity.Player;

/** Re-opens arena block editing only for an explicitly enabled OP Creative build session. */
public final class BuildBypassListener implements Listener {
    public static final String METADATA = "monstermaze_build_bypass";

    public BuildBypassListener(MonsterMazePlugin plugin) {
        org.bukkit.Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    private boolean allowed(Player player) {
        return player != null && player.isOp()
                && player.getGameMode() == GameMode.CREATIVE
                && player.hasMetadata(METADATA);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onBlockBreak(BlockBreakEvent event) {
        event.setCancelled(!allowed(event.getPlayer()));
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onBlockPlace(BlockPlaceEvent event) {
        event.setCancelled(!allowed(event.getPlayer()));
    }
}
