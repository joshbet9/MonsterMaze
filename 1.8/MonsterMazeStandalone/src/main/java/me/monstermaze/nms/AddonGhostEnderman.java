package me.monstermaze.nms;

import net.minecraft.server.v1_8_R3.EntitySnowman;
import net.minecraft.server.v1_8_R3.World;

/** Snowman-backed maze monster rendered to clients as an Enderman. */
public class AddonGhostEnderman extends EntitySnowman {
    public AddonGhostEnderman(World world) {
        super(world);
    }

    @Override
    public boolean ae() {
        return false;
    }
}
