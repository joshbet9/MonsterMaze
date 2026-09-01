package me.monstermaze.nms;

import net.minecraft.server.v1_8_R3.EntitySnowman;
import net.minecraft.server.v1_8_R3.World;

/**
 * Snowman-backed squid skin for Monster Maze.
 * The real entity remains a Snowman, so it uses the same movement controller as every
 * other maze monster instead of vanilla squid swimming logic.
 */
public class AddonGhostSquid extends EntitySnowman {
    public AddonGhostSquid(World world) {
        super(world);
    }

    @Override
    public boolean ae() {
        return false;
    }
}
