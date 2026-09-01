package me.monstermaze.nms;

import net.minecraft.server.v1_8_R3.EntitySnowman;
import net.minecraft.server.v1_8_R3.World;

/**
 * Snowman-backed pig-zombie skin for Monster Maze.
 * The underlying entity remains a Snowman, matching Mineplex's mob architecture.
 */
public class AddonGhostPigZombie extends EntitySnowman {
    public AddonGhostPigZombie(World world) {
        super(world);
    }

    @Override
    public boolean ae() {
        return false;
    }
}
