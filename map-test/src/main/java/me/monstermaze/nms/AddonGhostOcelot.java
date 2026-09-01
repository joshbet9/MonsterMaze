package me.monstermaze.nms;

import net.minecraft.server.v1_8_R3.EntityOcelot;
import net.minecraft.server.v1_8_R3.World;

/**
 * Ghosted ocelot used for maze monsters. Overriding {@code ae()} to return false
 * excludes this mob from other entities' collision lists, so maze mobs pass through
 * each other (no mutual shoving) while still obeying blocks and gravity.
 */
public class AddonGhostOcelot extends EntityOcelot {

    public AddonGhostOcelot(World world) {
        super(world);
    }

    @Override
    public boolean ae() {
        return false;
    }
}
