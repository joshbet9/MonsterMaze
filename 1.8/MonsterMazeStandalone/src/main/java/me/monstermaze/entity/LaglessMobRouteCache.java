package me.monstermaze.entity;

import me.monstermaze.maze.MazeGenerator;
import me.monstermaze.util.UtilEnt;
import org.bukkit.Location;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.LivingEntity;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

/**
 * Cached movement controller used exclusively by the 1.8 Lagless mode.
 *
 * The maze topology is built once for the active maze. Each mob then receives a
 * compact direction tape generated from that topology. Runtime movement does not
 * scan maze blocks or choose a random exit at intersections.
 *
 * Direction selection deliberately works for any number of exits:
 * - 1 exit: forced (including a dead-end U-turn)
 * - 2 exits: choose between the available exits, excluding reverse when possible
 * - 3/4 exits: choose from all non-reverse exits
 *
 * The route is independent of the Safe Pad: mobs are never routed toward it.
 */
public final class LaglessMobRouteCache {
    private static final int ROUTE_LENGTH = 4096;
    private static final double LOOKAHEAD = 6.0D;

    private final MazeGenerator maze;
    private final Set<Long> topology = new HashSet<Long>();
    private final Map<UUID, MobRoute> routes = new HashMap<UUID, MobRoute>();
    private final Random random = new Random();

    public LaglessMobRouteCache(MazeGenerator maze) {
        this.maze = maze;
        buildTopology();
    }

    /** Build the shared walkable-cell topology once for this maze. */
    private void buildTopology() {
        topology.clear();
        List<Location> paths = maze.getPathPoints();
        for (Location loc : paths) {
            if (loc != null && maze.isPath(loc)) {
                topology.add(key(loc.getBlockX(), loc.getBlockZ()));
            }
        }
    }

    public void forget(LivingEntity entity) {
        if (entity != null) routes.remove(entity.getUniqueId());
    }

    public void clear() {
        routes.clear();
        topology.clear();
    }

    /**
     * Move one Lagless mob. Returns false when the mob cannot currently be routed.
     * The caller remains responsible for launched/frozen entities.
     */
    public boolean move(LivingEntity entity, float speed) {
        if (entity == null || !entity.isValid() || entity.isDead()) return false;

        Location loc = entity.getLocation();
        long cell = key(loc.getBlockX(), loc.getBlockZ());
        MobRoute route = routes.get(entity.getUniqueId());

        if (route == null) {
            route = createRoute(loc);
            if (route == null) return false;
            routes.put(entity.getUniqueId(), route);
        }

        if (!topology.contains(cell)) {
            // External kit movement can throw a mob off the cached route. Re-sync once
            // it is back on a maze cell instead of allowing the route cache to fight it.
            Location nearest = maze.getClosestPath(loc);
            if (nearest == null) return false;
            entity.teleport(nearest);
            loc = nearest;
            cell = key(loc.getBlockX(), loc.getBlockZ());
            route = createRoute(loc);
            if (route == null) return false;
            routes.put(entity.getUniqueId(), route);
        }

        if (route.lastCell != cell) {
            route.lastCell = cell;
            route.index++;
            if (route.index >= route.directions.length) {
                route = createRoute(loc);
                if (route == null) return false;
                routes.put(entity.getUniqueId(), route);
            }
        }

        byte direction = route.directions[route.index];
        Location target = loc.clone();
        switch (direction) {
            case 0: target.add(0, 0, -LOOKAHEAD); break; // north
            case 1: target.add(LOOKAHEAD, 0, 0); break;  // east
            case 2: target.add(0, 0, LOOKAHEAD); break;  // south
            case 3: target.add(-LOOKAHEAD, 0, 0); break; // west
            default: return false;
        }

        // Keep the existing NMS CreatureMoveFast implementation, but give it a
        // deliberately distant look-ahead target. This avoids the old near-target
        // corner cap while the cached route controls exactly when a turn occurs.
        return UtilEnt.CreatureMoveFast(entity, target, speed);
    }

    private MobRoute createRoute(Location start) {
        if (start == null) return null;

        int x = start.getBlockX();
        int z = start.getBlockZ();
        long startKey = key(x, z);
        if (!topology.contains(startKey)) return null;

        byte[] directions = new byte[ROUTE_LENGTH];
        int previous = -1;

        for (int i = 0; i < directions.length; i++) {
            int[] choices = new int[4];
            int count = 0;

            for (int direction = 0; direction < 4; direction++) {
                if (!hasNeighbour(x, z, direction)) continue;
                if (previous >= 0 && direction == opposite(previous)) continue;
                choices[count++] = direction;
            }

            // At a dead end the only legal exit is the reverse direction. This is
            // intentionally a fallback rather than a special movement mode.
            if (count == 0) {
                for (int direction = 0; direction < 4; direction++) {
                    if (hasNeighbour(x, z, direction)) choices[count++] = direction;
                }
            }

            if (count == 0) return null;

            int chosen = choices[random.nextInt(count)];
            directions[i] = (byte) chosen;
            previous = chosen;
            x += dx(chosen);
            z += dz(chosen);
        }

        return new MobRoute(directions, startKey);
    }

    private boolean hasNeighbour(int x, int z, int direction) {
        return topology.contains(key(x + dx(direction), z + dz(direction)));
    }

    private static int dx(int direction) {
        switch (direction) {
            case 1: return 1;
            case 3: return -1;
            default: return 0;
        }
    }

    private static int dz(int direction) {
        switch (direction) {
            case 0: return -1;
            case 2: return 1;
            default: return 0;
        }
    }

    private static int opposite(int direction) {
        return (direction + 2) & 3;
    }

    private static long key(int x, int z) {
        return (((long) x) << 32) ^ (z & 0xffffffffL);
    }

    private static final class MobRoute {
        private final byte[] directions;
        private int index;
        private long lastCell;

        private MobRoute(byte[] directions, long startCell) {
            this.directions = directions;
            this.index = 0;
            this.lastCell = startCell;
        }
    }
}
