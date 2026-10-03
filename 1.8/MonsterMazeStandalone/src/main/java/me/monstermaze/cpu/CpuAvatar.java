package me.monstermaze.cpu;

import org.bukkit.Location;
import org.bukkit.entity.Player;

/**
 * Player-like server-side body for a Monster Maze CPU competitor.
 *
 * <p>The avatar owns Minecraft/NMS state. It deliberately exposes only the
 * small movement-control surface required by the CPU brain.
 */
public interface CpuAvatar {
    String id();
    Player player();

    void spawn(Location location);
    void apply(CpuAvatarAction action);
    void teleport(Location location);
    void destroy();

    boolean isSpawned();
}
