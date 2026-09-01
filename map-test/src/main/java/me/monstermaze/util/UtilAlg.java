package me.monstermaze.util;

import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.util.Vector;

/**
 * Minimal port of Mineplex UtilAlg pieces used by Monster Maze.
 */
public final class UtilAlg {
    private UtilAlg() {}

    /** Direction from a to b (not normalized). */
    public static Vector getTrajectory(Entity from, Entity to) {
        return getTrajectory(from.getLocation().toVector(), to.getLocation().toVector());
    }

    public static Vector getTrajectory(Location from, Location to) {
        return getTrajectory(from.toVector(), to.toVector());
    }

    public static Vector getTrajectory(Vector from, Vector to) {
        return to.clone().subtract(from);
    }

    /** Horizontal trajectory (Y = 0). */
    public static Vector getTrajectory2d(Entity from, Entity to) {
        Vector v = getTrajectory(from, to);
        v.setY(0);
        return v;
    }

    public static Vector getTrajectory2d(Location from, Location to) {
        Vector v = getTrajectory(from, to);
        v.setY(0);
        return v;
    }
}
