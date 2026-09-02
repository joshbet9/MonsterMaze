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

    public MapThemeApplier(MonsterMazePlugin plugin) { this.plugin = plugin; }

    public void start() {
        if (task != null) return;
        task = Bukkit.getScheduler().runTaskTimer(plugin, new Runnable() {
            @Override public void run() {
                applyIfReady();
                applyCenterThemeIfNeeded();
            }
        }, 1L, 1L);
    }

    public void stop() {
        if (task != null) { task.cancel(); task = null; }
    }

    /** Force a re-application after a SafePad has restored its pre-pad blocks. */
    public void refresh() {
        lastPattern = -1;
        lastMap = "";
        applyIfReady();
        applyCenterThemeIfNeeded();
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
            Block top = world.getBlockAt(x, y - 1, z);
            Block middle = world.getBlockAt(x, y - 2, z);
            Block bottom = world.getBlockAt(x, y - 3, z);

            if (!isProtectedPadBlock(top)) top.setType(theme.top, false);
            middle.setType(theme.middle, false);
            bottom.setType(theme.bottom, false);
        }

        lastPattern = pattern;
        lastMap = map;
        plugin.getLogger().info("Applied map theme '" + map + "' to maze pattern " + (pattern + 1) + ".");
    }

    /**
     * Center deterioration directly restores a path cell from the temporary quartz material.
     * Re-apply only the small center-path subset every tick so that the final restoration uses
     * the real map palette without scanning/replacing the entire maze every tick.
     */
    private void applyCenterThemeIfNeeded() {
        if (plugin.getGameManager() == null) return;
        MazeGenerator maze = plugin.getGameManager().getMazeGenerator();
        if (maze == null || !maze.isMazeLive()) return;

        MazeBlockData theme = plugin.getMapManager().activeTheme();
        List<Location> centerPaths = maze.getCenterSafeZonePaths();
        for (Location path : centerPaths) {
            World world = path.getWorld();
            if (world == null) continue;
            Block top = world.getBlockAt(path.getBlockX(), path.getBlockY() - 1, path.getBlockZ());
            if (!isProtectedPadBlock(top) && top.getType() != theme.top) {
                top.setType(theme.top, false);
            }
        }
    }

    private boolean isProtectedPadBlock(Block block) {
        Material type = block.getType();
        return type == Material.BEACON
                || type == Material.LIME_TERRACOTTA
                || type == Material.YELLOW_TERRACOTTA
                || type == Material.ORANGE_TERRACOTTA
                || type == Material.RED_TERRACOTTA
                || type == Material.LIME_CONCRETE
                || type == Material.IRON_BLOCK
                || type == Material.QUARTZ_STAIRS
                || type == Material.CHISELED_QUARTZ_BLOCK;
    }
}
