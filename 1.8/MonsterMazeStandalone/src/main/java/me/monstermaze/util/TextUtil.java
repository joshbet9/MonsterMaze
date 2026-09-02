package me.monstermaze.util;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;

/**
 * 1.8-compatible title + action-bar helpers via reflection (no NMS compile dep).
 */
public final class TextUtil {
    private TextUtil() {}

    public static void title(Player player, String title, String subtitle, int fadeIn, int stay, int fadeOut) {
        try {
            String ver = Bukkit.getServer().getClass().getPackage().getName().split("\\.")[3];
            Class<?> packetClass = Class.forName("net.minecraft.server." + ver + ".PacketPlayOutTitle");
            Class<?> enumClass = Class.forName("net.minecraft.server." + ver + ".PacketPlayOutTitle$EnumTitleAction");
            Class<?> chatClass = Class.forName("net.minecraft.server." + ver + ".IChatBaseComponent");
            Class<?> chatSerializer = Class.forName("net.minecraft.server." + ver + ".IChatBaseComponent$ChatSerializer");
            Class<?> craftPlayer = Class.forName("org.bukkit.craftbukkit." + ver + ".entity.CraftPlayer");

            Method a = chatSerializer.getMethod("a", String.class);
            Object titleComp = a.invoke(null, "{\"text\":\"" + escape(title) + "\"}");
            Object subComp = a.invoke(null, "{\"text\":\"" + escape(subtitle) + "\"}");

            Object enumTimes = enumClass.getField("TIMES").get(null);
            Object enumTitle = enumClass.getField("TITLE").get(null);
            Object enumSub = enumClass.getField("SUBTITLE").get(null);

            Constructor<?> cons = packetClass.getConstructor(enumClass, chatClass, int.class, int.class, int.class);
            Object packetTimes = cons.newInstance(enumTimes, null, fadeIn, stay, fadeOut);
            Object packetTitle = cons.newInstance(enumTitle, titleComp, fadeIn, stay, fadeOut);
            Object packetSub = cons.newInstance(enumSub, subComp, fadeIn, stay, fadeOut);

            Object handle = craftPlayer.getMethod("getHandle").invoke(player);
            Object conn = handle.getClass().getField("playerConnection").get(handle);
            Method send = conn.getClass().getMethod("sendPacket", Class.forName("net.minecraft.server." + ver + ".Packet"));
            send.invoke(conn, packetTimes);
            send.invoke(conn, packetTitle);
            send.invoke(conn, packetSub);
        } catch (Throwable t) {
            if (title != null && !title.isEmpty()) player.sendMessage(title);
            if (subtitle != null && !subtitle.isEmpty()) player.sendMessage(subtitle);
        }
    }

    public static void titleAll(String title, String subtitle, int fadeIn, int stay, int fadeOut) {
        for (Player p : Bukkit.getOnlinePlayers()) {
            title(p, title, subtitle, fadeIn, stay, fadeOut);
        }
    }

    public static void actionBar(Player player, String message) {
        // TEMPORARY DIAGNOSTIC: disable 1.8 action-bar packet sending entirely.
        // This isolates the expensive reflective NMS action-bar path used by the per-tick kit UI.
        return;
    }

    private static String escape(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    public static String c(String s) {
        return ChatColor.translateAlternateColorCodes('&', s);
    }
}
