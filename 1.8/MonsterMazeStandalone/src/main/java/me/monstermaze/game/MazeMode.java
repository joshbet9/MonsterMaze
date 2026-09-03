package me.monstermaze.game;

import org.bukkit.ChatColor;

/**
 * Game modes. ORIGINAL is a 1:1 recreation of the source (no gameplay QOL).
 * The other modes layer QOL fixes + their own tuning on top.
 */
public enum MazeMode {
    ORIGINAL("Original", ChatColor.GRAY,
            "1:1 recreation of the original source. No gameplay QOL changes."),
    SPEED("Speed", ChatColor.GOLD,
            "QOL fixes; Jumper has 3 jumps that reset to 3 on every Safe Pad."),
    MODERN("Modern", ChatColor.LIGHT_PURPLE,
            "QOL fixes; Jumper has 3 leaps that reset to 3 on every Safe Pad; faster timer (35s -> 15s over 10 stages) and +20% starter mobs."),
    LAGLESS("Lagless", ChatColor.AQUA,
            "Experimental: fixed 500-mob pool at start, no per-stage spawns; mobs speed up every 5 stages for difficulty. Timer matches Modern.");

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
