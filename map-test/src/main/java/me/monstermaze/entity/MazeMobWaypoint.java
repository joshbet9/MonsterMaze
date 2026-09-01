package me.monstermaze.entity;

import org.bukkit.Location;

/**
 * Per-mob path state (mirrors original MazeMobWaypoint).
 */
public class MazeMobWaypoint {

    public enum CardinalDirection {
        NORTH, SOUTH, EAST, WEST, NULL
    }

    public Location Last;
    public Location Target;
    public CardinalDirection Direction = CardinalDirection.NULL;

    public MazeMobWaypoint(Location last) {
        this.Last = last;
        this.Target = null;
    }
}
