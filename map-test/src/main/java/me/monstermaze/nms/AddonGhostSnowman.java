package me.monstermaze.nms;

import net.minecraft.server.v1_8_R3.EntitySnowman;
import net.minecraft.server.v1_8_R3.World;

/**
 * Ghosted snowman used for maze monsters.
 *
 * <p>Vanilla entity-vs-entity push lives in {@code EntityLiving.bL()}, which gathers
 * nearby entities through a predicate that filters on {@code other.ae()}. Returning
 * false here excludes this mob from everyone else's collision list, so the mobs pass
 * straight through one another (never shove each other off block centers) while still
 * colliding with blocks and obeying gravity (so they sit on the maze, not in the void).
 * This replicates Mineplex's server-core {@code setGhost(true)} patch on vanilla Spigot
 * by overriding the compiled method directly.</p>
 */
public class AddonGhostSnowman extends EntitySnowman {

    public AddonGhostSnowman(World world) {
        super(world);
        // Same size/params as a normal Snowman (width 0.7, height 1.9 from the parent).
    }

    @Override
    public boolean ae() {
        // "Is this entity a collision participant?" -> false disables mob-mob push.
        return false;
    }
}
