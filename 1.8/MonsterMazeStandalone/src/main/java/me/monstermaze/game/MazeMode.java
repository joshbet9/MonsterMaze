package me.monstermaze.game;

import org.bukkit.ChatColor;

/**
 * Game modes. ORIGINAL is a 1:1 recreation of the source.
 * The other modes provide alternate gameplay tuning and mechanics.
 */
public enum MazeMode {
    ORIGINAL("Original", ChatColor.GRAY,
            "The original Monster Maze experience, recreated as faithfully as possible."),
    SPEED("Speed", ChatColor.GOLD,
            "Modern gameplay with the original timer and monster spawning."),
    MODERN("Modern", ChatColor.LIGHT_PURPLE,
            "Enhanced gameplay with faster stages, more monsters, improved Jumper charges, and expanded kit abilities."),
    LAGLESS("Lagless", ChatColor.AQUA,
            "Designed to reduce server load with a fixed monster pool and steadily increasing monster speed.");

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
