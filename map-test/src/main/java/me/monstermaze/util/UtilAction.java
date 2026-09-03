package me.monstermaze.util;

import org.bukkit.entity.Entity;
import org.bukkit.util.Vector;

/**
 * Exact port of mineplex.core.common.util.UtilAction (velocity overloads used by Maze).
 *
 * Call from Maze.bump():
 * velocity(player, UtilAlg.getTrajectory(ent, player), 1, false, 0, 0.75, 1.2, true);
 */
public final class UtilAction {
    private UtilAction() {}

    public static void velocity(Entity ent, Vector vec) {
        velocity(ent, vec, vec.length(), false, 0, 0, vec.length(), false);
    }

    public static void velocity(Entity ent, double str, double yAdd, double yMax, boolean groundBoost) {
        velocity(ent, ent.getLocation().getDirection(), str, false, 0, yAdd, yMax, groundBoost);
    }

    public static void velocity(Entity ent, Vector vec, double str, boolean ySet, double yBase,
                                double yAdd, double yMax, boolean groundBoost) {
        if (ent == null || vec == null) {
            return;
        }

        // Work on a copy so callers' vectors are not mutated unexpectedly
        vec = new Vector(vec.getX(), vec.getY(), vec.getZ());

        if (Double.isNaN(vec.getX()) || Double.isNaN(vec.getY()) || Double.isNaN(vec.getZ()) || vec.length() == 0) {
            zeroVelocity(ent);
            return;
        }

        // YSet
        if (ySet) {
            vec.setY(yBase);
        }

        // Modify
        vec.normalize();
        vec.multiply(str);

        // YAdd
        vec.setY(vec.getY() + yAdd);

        // Limit
        if (vec.getY() > yMax) {
            vec.setY(yMax);
        }

        // Ground boost AFTER yMax clamp (same as Mineplex)
        if (groundBoost) {
            if (UtilEnt.isGrounded(ent)) {
                vec.setY(vec.getY() + 0.2);
            }
        }

        // EntityVelocityChangeEvent skipped (no Mineplex event bus dependency)
        // Velocity
        ent.setFallDistance(0);
        ent.setVelocity(vec);
    }

    public static void zeroVelocity(Entity ent) {
        if (ent == null) return;
        Vector vec = new Vector(0, 0, 0);
        ent.setFallDistance(0);
        ent.setVelocity(vec);
    }
}
