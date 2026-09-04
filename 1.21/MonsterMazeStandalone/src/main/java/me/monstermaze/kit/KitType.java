package me.monstermaze.kit;

import me.monstermaze.game.MazeMode;
import org.bukkit.ChatColor;
import org.bukkit.Material;

public enum KitType {
    JUMPER("Jumper", ChatColor.YELLOW + "Jumper", Material.FEATHER),
    SLOWBALL("Slowball", ChatColor.AQUA + "Slowballer", Material.SNOWBALL),
    BODY_BUILDER("Body Builder", ChatColor.RED + "Body Builder", Material.APPLE),
    REPULSOR("Repulsor", ChatColor.GREEN + "Repulsor", Material.COAL),
    MAVERICK("Maverick", ChatColor.GOLD + "Maverick", Material.ARROW);

    public final String id;
    public final String display;
    public final Material icon;

    private static final String[] JUMPER_ORIGINAL = {
            ChatColor.GRAY + "You have " + ChatColor.YELLOW + "5 charged jumps" + ChatColor.GRAY + ".",
            ChatColor.GRAY + "Jump normally while charges remain."
    };
    private static final String[] JUMPER_ENHANCED = {
            ChatColor.GRAY + "You have " + ChatColor.YELLOW + "3 charged jumps" + ChatColor.GRAY + ".",
            ChatColor.GRAY + "Safe Pads restore your charges."
    };
    private static final String[] SLOWBALL_BASE = {
            ChatColor.GRAY + "Throw snowballs to " + ChatColor.AQUA + "slow other players" + ChatColor.GRAY + ".",
            ChatColor.GRAY + "Snowballs regenerate up to 16 over time."
    };
    private static final String[] SLOWBALL_ENHANCED = {
            ChatColor.GRAY + "Throw snowballs to " + ChatColor.AQUA + "slow other players" + ChatColor.GRAY + ".",
            ChatColor.GRAY + "Snowballs regenerate up to 16 over time.",
            ChatColor.GRAY + "Q: " + ChatColor.AQUA + "Cryo Blitz" + ChatColor.GRAY + " freezes nearby monsters."
    };
    private static final String[] BODY_BASE = {
            ChatColor.GRAY + "Gain " + ChatColor.RED + "1 heart" + ChatColor.GRAY + " of max health",
            ChatColor.GRAY + "when first to a Safe Pad, up to 15 hearts."
    };
    private static final String[] BODY_ENHANCED = {
            ChatColor.GRAY + "Gain " + ChatColor.RED + "1 heart" + ChatColor.GRAY + " of max health",
            ChatColor.GRAY + "when first to a Safe Pad, up to 15 hearts.",
            ChatColor.GRAY + "Q: " + ChatColor.RED + "Body Rush" + ChatColor.GRAY + " deflects monster hits."
    };
    private static final String[] REPULSOR_DESCRIPTION = {
            ChatColor.GRAY + "Launch nearby monsters " + ChatColor.GREEN + "away from you" + ChatColor.GRAY + ".",
            ChatColor.GRAY + "3 charges per run, with a 6 block range."
    };
    private static final String[] MAVERICK_DESCRIPTION = {
            ChatColor.GRAY + "Monster hits launch you " + ChatColor.GOLD + "toward the next Safe Pad" + ChatColor.GRAY + "."
    };

    KitType(String id, String display, Material icon) {
        this.id = id;
        this.display = display;
        this.icon = icon;
    }

    public static KitType byName(String name) {
        if (name == null) return null;
        for (KitType k : values()) {
            if (k.name().equalsIgnoreCase(name) || k.id.equalsIgnoreCase(name)) return k;
        }
        return null;
    }

    public boolean qolOnly() { return this == MAVERICK; }

    /** Returns only the abilities actually available to this kit in the selected mode. */
    public String[] description(MazeMode mode) {
        if (mode == null || mode == MazeMode.ORIGINAL) {
            switch (this) {
                case JUMPER: return JUMPER_ORIGINAL;
                case SLOWBALL: return SLOWBALL_BASE;
                case BODY_BUILDER: return BODY_BASE;
                case REPULSOR: return REPULSOR_DESCRIPTION;
                case MAVERICK: return MAVERICK_DESCRIPTION;
                default: return new String[0];
            }
        }
        switch (this) {
            case JUMPER: return JUMPER_ENHANCED;
            case SLOWBALL: return SLOWBALL_ENHANCED;
            case BODY_BUILDER: return BODY_ENHANCED;
            case REPULSOR: return REPULSOR_DESCRIPTION;
            case MAVERICK: return MAVERICK_DESCRIPTION;
            default: return new String[0];
        }
    }

    public static java.util.List<KitType> available(MazeMode mode) {
        if (mode == null) mode = MazeMode.ORIGINAL;
        java.util.List<KitType> list = new java.util.ArrayList<KitType>();
        boolean enhanced = mode != MazeMode.ORIGINAL;
        for (KitType k : values()) if (enhanced || !k.qolOnly()) list.add(k);
        return list;
    }

    /** @deprecated Use available(MazeMode). */
    @Deprecated
    public static java.util.List<KitType> available(boolean enhanced) {
        return available(enhanced ? MazeMode.MODERN : MazeMode.ORIGINAL);
    }
}
