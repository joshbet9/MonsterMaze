package me.monstermaze.kit;

import org.bukkit.ChatColor;
import org.bukkit.Material;

public enum KitType {
    JUMPER("Jumper", ChatColor.YELLOW + "Jumper", Material.FEATHER,
            new String[]{
                    ChatColor.GRAY + "You can jump " + ChatColor.YELLOW + "5 Times" + ChatColor.GRAY + ".",
                    ChatColor.GRAY + "Vanilla jumps while you have charges."
            }),
    SLOWBALL("Slowball", ChatColor.AQUA + "Slowballer", Material.SNOWBALL,
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
                    ChatColor.GRAY + "A mob hit always knocks you " + ChatColor.GOLD + "toward" + ChatColor.GRAY + " the next Safe Pad.",
                    ChatColor.DARK_GRAY + "(QOL mode only)"
            });

    public final String id;
    public final String display;
    public final Material icon;
    public final String[] description;

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

    /** True when this kit can be selected in a mode with QOL enabled (i.e. not Original). */
    public boolean qolOnly() {
        return this == MAVERICK;
    }

    public static java.util.List<KitType> available(boolean qol) {
        java.util.List<KitType> list = new java.util.ArrayList<KitType>();
        for (KitType k : values()) {
            if (!qol && k.qolOnly()) continue;
            list.add(k);
        }
        return list;
    }
}
