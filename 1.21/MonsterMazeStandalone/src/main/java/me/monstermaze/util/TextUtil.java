package me.monstermaze.util;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;

/**
 * Title + action-bar helpers (Paper 1.21 Bukkit API). ViaVersion translates the
 * underlying packets so 1.8.8 clients receive the same titles/action bars.
 */
public final class TextUtil {
    private TextUtil() {}

    public static void title(Player player, String title, String subtitle, int fadeIn, int stay, int fadeOut) {
        player.sendTitle(title, subtitle, fadeIn, stay, fadeOut);
    }

    public static void titleAll(String title, String subtitle, int fadeIn, int stay, int fadeOut) {
        for (Player p : Bukkit.getOnlinePlayers()) {
            title(p, title, subtitle, fadeIn, stay, fadeOut);
        }
    }

    public static void actionBar(Player player, String message) {
        player.sendActionBar(message);
    }

    public static String c(String s) {
        return ChatColor.translateAlternateColorCodes('&', s);
    }
}