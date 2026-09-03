package me.monstermaze.maze;

import org.bukkit.Material;

/**
 * Per-map maze palette, mirroring Mineplex's {@code MazeBlockData}.
 *
 * <p>A maze path cell is built as a 1x3 stack below the walk surface:
 * <pre>
 *   [Top]    walk surface  (y-1)
 *   [Middle] support        (y-2)
 *   [Bottom] base           (y-3)
 * </pre>
 * On Mineplex this came from the map's {@code B1=id,data} (top),
 * {@code B2} (middle) and {@code B3} (bottom) tags. Our maps don't ship those
 * tags, so the theme is configured per-map in {@code config.yml} (and editable).
 */
public class MazeBlockData {

    public static class MazeBlock {
        public final Material Type;
        public final byte Data;

        public MazeBlock(Material type, byte data) {
            this.Type = type;
            this.Data = data;
        }

        public MazeBlock(Material type) {
            this(type, (byte) 0);
        }
    }

    public final MazeBlock Top;
    public final MazeBlock Middle;
    public final MazeBlock Bottom;

    public MazeBlockData(MazeBlock top, MazeBlock middle, MazeBlock bottom) {
        this.Top = top;
        this.Middle = middle;
        this.Bottom = bottom;
    }

    /** Default theme used by the original void arena: quartz top/mid over stone base. */
    public static MazeBlockData defaultTheme() {
        return new MazeBlockData(
                new MazeBlock(Material.QUARTZ_BLOCK),
                new MazeBlock(Material.QUARTZ_BLOCK, (byte) 1),
                new MazeBlock(Material.STONE));
    }

    /** Build a theme from raw id/data triples (handles -1/null as "not set"). */
    public static MazeBlockData from(Material top, byte topData, Material mid, byte midData,
                                     Material bottom, byte bottomData, MazeBlockData fallback) {
        Material t = top != null ? top : fallback.Top.Type;
        byte td = topData >= 0 ? topData : fallback.Top.Data;
        Material m = mid != null ? mid : fallback.Middle.Type;
        byte md = midData >= 0 ? midData : fallback.Middle.Data;
        Material b = bottom != null ? bottom : fallback.Bottom.Type;
        byte bd = bottomData >= 0 ? bottomData : fallback.Bottom.Data;
        return new MazeBlockData(new MazeBlock(t, td), new MazeBlock(m, md), new MazeBlock(b, bd));
    }
}
