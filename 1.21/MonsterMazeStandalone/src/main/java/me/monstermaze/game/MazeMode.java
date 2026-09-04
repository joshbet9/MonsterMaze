package me.monstermaze.game;

import org.bukkit.ChatColor;

/**
 * Game modes. ORIGINAL is a 1:1 recreation of the source.
 * The other modes provide alternate gameplay tuning and mechanics.
 */
public enum MazeMode {
    ORIGINAL("Original", ChatColor.GRAY,
            "The original Monster Maze experience, recreated as faithfully as possible."),
    MODERN("Modern", ChatColor.LIGHT_PURPLE,
            "Enhanced gameplay with faster stages, more monsters, and expanded kit abilities."),
    CLASSIC("Classic", ChatColor.RED,
            "Modern gameplay without the speed boost, keeping the standard player movement speed.");

    public final String id;
    public final ChatColor color;
    public final String description;

    MazeMode(String id, ChatColor color, String description) {
        this.id = id;
        this.color = color;
        this.description = description;
    }

    public static MazeMode byName(String name) {
        if (name == null) return null;
        for (MazeMode m : values()) {
            if (m.name().equalsIgnoreCase(name) || m.id.equalsIgnoreCase(name)) return m;
        }
        return null;
    }
}
