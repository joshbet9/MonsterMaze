package me.monstermaze.nms;

import net.minecraft.server.v1_8_R3.EntityZombie;
import net.minecraft.server.v1_8_R3.World;

/**
 * Ghosted zombie used for maze monsters. Overriding {@code ae()} to return false
 * excludes this mob from other entities' collision lists, so maze mobs pass through
 * each other (no mutual shoving) while still obeying blocks and gravity.
 */
public class AddonGhostZombie extends EntityZombie {

    public AddonGhostZombie(World world) {
        super(world);
    }

    @Override
    public boolean ae() {
        return false;
    }
}
