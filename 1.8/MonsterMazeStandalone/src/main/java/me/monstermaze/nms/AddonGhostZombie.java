package me.monstermaze.nms;

import net.minecraft.server.v1_8_R3.EntitySnowman;
import net.minecraft.server.v1_8_R3.World;

/**
 * Snowman-backed zombie skin for Monster Maze.
 * Mineplex's maze zombie was a disguised Snowman, so it does not use zombie AI or sunlight.
 */
public class AddonGhostZombie extends EntitySnowman {
    public AddonGhostZombie(World world) {
        super(world);
    }

    @Override
    public boolean ae() {
        return false;
    }
}
