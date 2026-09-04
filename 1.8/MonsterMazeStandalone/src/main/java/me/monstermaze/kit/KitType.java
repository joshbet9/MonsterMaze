package me.monstermaze.kit;

import me.monstermaze.game.MazeMode;
import org.bukkit.ChatColor;
import org.bukkit.Material;

public enum KitType {
    JUMPER("Jumper", ChatColor.YELLOW + "Jumper", Material.FEATHER,
            new String[]{
                    ChatColor.GRAY + "You can jump " + ChatColor.YELLOW + "5 Times" + ChatColor.GRAY + ".",
                    ChatColor.GRAY + "Vanilla jumps while you have charges."
            }),
    SLOWBALL("Slowball", ChatColor.AQUA + "Slowballer", Material.SNOW_BALL,
            new String[]{
                    ChatColor.GRAY + "Throw snowballs to " + ChatColor.AQUA + "Slow" + ChatColor.GRAY + " players.",
                    ChatColor.GRAY + "Regenerates up to 16 every 2 seconds."
            }),
    BODY_BUILDER("Body Builder", ChatColor.RED + "Body Builder", Material.APPLE,
            new String[]{
                    ChatColor.GRAY + "Max Health increases by " + ChatColor.RED + "One Heart",
                    ChatColor.GRAY + "when first to a Safe Pad. Max 15 hearts."
            }),
    REPULSOR("Repulsor", ChatColor.GREEN + "Repulsor", Material.COAL,
            new String[]{
                    ChatColor.YELLOW + "Click" + ChatColor.GRAY + " with Coal to use " + ChatColor.GREEN + "Repulse" + ChatColor.GRAY + ".",
                    ChatColor.GRAY + "Launch monsters within 6 blocks (3 charges)."
            }),
    MAVERICK("Maverick", ChatColor.GOLD + "Maverick", Material.ARROW,
            new String[]{
                    ChatColor.GRAY + "A mob hit always knocks you " + ChatColor.GOLD + "toward" + ChatColor.GRAY + " the next Safe Pad."
            });

    public final String id;
    public final String display;
    public final Material icon;
    public String[] description;

    private static final String[] JUMPER_ORIGINAL = {
            ChatColor.GRAY + "You have " + ChatColor.YELLOW + "5 charged jumps" + ChatColor.GRAY + ".",
            ChatColor.GRAY + "Jump normally while charges remain."
    };
    private static final String[] JUMPER_MODERN = {
            ChatColor.GRAY + "You have " + ChatColor.YELLOW + "3 charged jumps" + ChatColor.GRAY + ".",
            ChatColor.GRAY + "Safe Pads restore your charges."
    };
    private static final String[] SLOWBALL_ORIGINAL = {
            ChatColor.GRAY + "Throw snowballs to " + ChatColor.AQUA + "slow other players" + ChatColor.GRAY + ".",
            ChatColor.GRAY + "Snowballs regenerate up to 16 over time."
    };
    private static final String[] SLOWBALL_MODERN = {
            ChatColor.GRAY + "Throw snowballs to " + ChatColor.AQUA + "slow other players" + ChatColor.GRAY + ".",
            ChatColor.GRAY + "Snowballs regenerate up to 16 over time.",
            ChatColor.GRAY + "Q: " + ChatColor.AQUA + "Cryo Blitz" + ChatColor.GRAY + " freezes nearby monsters."
    };
    private static final String[] BODY_ORIGINAL = {
            ChatColor.GRAY + "Gain " + ChatColor.RED + "1 heart" + ChatColor.GRAY + " of max health",
            ChatColor.GRAY + "when first to a Safe Pad, up to 15 hearts."
    };
    private static final String[] BODY_MODERN = {
            ChatColor.GRAY + "Gain " + ChatColor.RED + "1 heart" + ChatColor.GRAY + " of max health",
            ChatColor.GRAY + "when first to a Safe Pad, up to 15 hearts.",
            ChatColor.GRAY + "Body Rush: " + ChatColor.RED + "deflect monster hits" + ChatColor.GRAY + "."
    };
    private static final String[] REPULSOR_DESCRIPTION = {
            ChatColor.GRAY + "Launch nearby monsters " + ChatColor.GREEN + "away from you" + ChatColor.GRAY + ".",
            ChatColor.GRAY + "3 charges per run, with a 6 block range."
    };
    private static final String[] MAVERICK_DESCRIPTION = {
            ChatColor.GRAY + "Monster hits launch you " + ChatColor.GOLD + "toward the next Safe Pad" + ChatColor.GRAY + "."
    };

    KitType(String id, String display, Material icon, String[] description) {
        this.id = id;
        this.display = display;
        this.icon = icon;
        this.description = description;
    }

    public static KitType byName(String name) {
        if (name == null) return null;
        for (KitType k : values()) {
            if (k.name().equalsIgnoreCase(name) || k.id.equalsIgnoreCase(name)) return k;
        }
        return null;
    }

    /** True when this kit can be selected in a mode with non-Original kit abilities. */
    public boolean qolOnly() {
        return this == MAVERICK;
    }

    /** Refresh the player-facing kit lore for the active mode. */
    public static void updateDescriptions(MazeMode mode) {
        boolean original = mode == MazeMode.ORIGINAL;
        JUMPER.description = original ? JUMPER_ORIGINAL : JUMPER_MODERN;
        SLOWBALL.description = original ? SLOWBALL_ORIGINAL : SLOWBALL_MODERN;
        BODY_BUILDER.description = original ? BODY_ORIGINAL : BODY_MODERN;
        REPULSOR.description = REPULSOR_DESCRIPTION;
        MAVERICK.description = MAVERICK_DESCRIPTION;
    }

    public static java.util.List<KitType> available(boolean qol) {
        java.util.List<KitType> list = new java.util.ArrayList<KitType>();
        for (KitType k : values()) {
            if (!qol && k.qolOnly()) continue;
            list.add(k);
        }
        updateDescriptions(qol ? MazeMode.MODERN : MazeMode.ORIGINAL);
        return list;
    }
}
