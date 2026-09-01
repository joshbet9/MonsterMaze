package me.monstermaze.world;

import me.monstermaze.MonsterMazePlugin;
import me.monstermaze.maze.MazeBlockData;
import me.monstermaze.maze.MazeGenerator;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.scheduler.BukkitTask;

import java.util.List;

/** Applies the active map palette to the generated 1.21 maze. */
public final class MapThemeApplier {

    private final MonsterMazePlugin plugin;
    private BukkitTask task;
    private int lastPattern = -1;
    private String lastMap = "";

    public MapThemeApplier(MonsterMazePlugin plugin) {
        this.plugin = plugin;
    }

    public void start() {
        if (task != null) return;
        task = Bukkit.getScheduler().runTaskTimer(plugin, new Runnable() {
            @Override public void run() { applyIfReady(); }
        }, 1L, 1L);
    }

    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
    }

    private void applyIfReady() {
        if (plugin.getGameManager() == null) return;
        MazeGenerator maze = plugin.getGameManager().getMazeGenerator();
        if (maze == null || !maze.isMazeLive()) return;

        int pattern = maze.getPatternIndex();
        String map = plugin.getMapManager().getActiveMap();
        if (pattern == lastPattern && map.equals(lastMap)) return;

        MazeBlockData theme = plugin.getMapManager().activeTheme();
        List<Location> paths = maze.getPathPoints();
        if (paths.isEmpty()) return;

        for (Location path : paths) {
            World world = path.getWorld();
            if (world == null) continue;

            int x = path.getBlockX();
            int y = path.getBlockY();
            int z = path.getBlockZ();

            // Path locations represent player feet. The generated walk surface
            // is y-1, followed by two support layers at y-2 and y-3.
            Block top = world.getBlockAt(x, y - 1, z);
            Block middle = world.getBlockAt(x, y - 2, z);
            Block bottom = world.getBlockAt(x, y - 3, z);

            // Safe-pad construction can run before this task. Preserve its
            // special surface/decorative blocks rather than recolouring them.
            if (!isProtectedTop(top)) {
                top.setType(theme.top, false);
            }
            middle.setType(theme.middle, false);
            bottom.setType(theme.bottom, false);
        }

        lastPattern = pattern;
        lastMap = map;
        plugin.getLogger().info("Applied map theme '" + map + "' to maze pattern " + (pattern + 1) + ".");
    }

    private boolean isProtectedTop(Block block) {
        Material type = block.getType();
        return type == Material.LIME_TERRACOTTA
                || type == Material.LIME_CONCRETE
                || type == Material.IRON_BLOCK
                || type == Material.QUARTZ_STAIRS
                || type == Material.CHISELED_QUARTZ_BLOCK;
    }
}
