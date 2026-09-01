package me.monstermaze.nms;

import net.minecraft.server.v1_8_R3.EntitySnowman;
import net.minecraft.server.v1_8_R3.World;

/**
 * Snowman-backed villager skin for Monster Maze.
 * Mineplex used Snowman physics and movement underneath the visual mob type.
 */
public class AddonGhostVillager extends EntitySnowman {
    public AddonGhostVillager(World world) {
        super(world);
    }

    @Override
    public boolean ae() {
        return false;
    }
}
