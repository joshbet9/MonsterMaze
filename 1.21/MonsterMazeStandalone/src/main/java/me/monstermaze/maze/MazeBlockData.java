package me.monstermaze.maze;

import org.bukkit.Material;

/**
 * Per-map maze palette for the modern implementation.
 *
 * <p>In 1.8 the three entries corresponded to the walk surface and the two
 * support layers below it. Modern Minecraft has no legacy block-data byte,
 * so the palette stores the resolved Material only.</p>
 */
public final class MazeBlockData {

    public final Material top;
    public final Material middle;
    public final Material bottom;

    public MazeBlockData(Material top, Material middle, Material bottom) {
        this.top = top;
        this.middle = middle;
        this.bottom = bottom;
    }

    public static MazeBlockData defaultTheme() {
        return new MazeBlockData(
                Material.QUARTZ_BLOCK,
                Material.CHISELED_QUARTZ_BLOCK,
                Material.STONE
        );
    }
}
