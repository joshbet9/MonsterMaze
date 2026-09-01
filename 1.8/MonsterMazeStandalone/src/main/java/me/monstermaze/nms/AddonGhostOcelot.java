package me.monstermaze.nms;

import net.minecraft.server.v1_8_R3.EntitySnowman;
import net.minecraft.server.v1_8_R3.World;

/**
 * Snowman-backed ocelot skin for Monster Maze.
 *
 * <p>Mineplex used a real Snowman for maze monsters and changed only the client-side
 * entity type. This subclass keeps Snowman movement/AI/physics while EntityTypes maps
 * it to the ocelot id for the spawn packet.</p>
 */
public class AddonGhostOcelot extends EntitySnowman {
    public AddonGhostOcelot(World world) {
        super(world);
    }

    @Override
    public boolean ae() {
        return false;
    }
}
