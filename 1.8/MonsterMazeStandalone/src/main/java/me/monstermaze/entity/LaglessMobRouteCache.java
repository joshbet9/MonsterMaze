package me.monstermaze.entity;

import me.monstermaze.maze.MazeGenerator;
import me.monstermaze.util.UtilEnt;
import org.bukkit.Location;
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
 * Safe Pads are dynamic, so cached topology represents the permanent maze geometry
 * while route generation checks the live path state before selecting each cell.
 */
public final class LaglessMobRouteCache {
    private static final int ROUTE_LENGTH = 4096;
    private static final int MAX_CATCHUP_CELLS = 8;
    private static final double LOOKAHEAD = 6.0D;

    private final MazeGenerator maze;
    private final Set<Long> topology = new HashSet<Long>();
    private final Map<UUID, MobRoute> routes = new HashMap<UUID, MobRoute>();
    private final Random random = new Random();

    public LaglessMobRouteCache(MazeGenerator maze) {
        this.maze = maze;
        buildTopology();
    }

    /** Build the permanent walkable-cell topology once for this maze. */
    private void buildTopology() {
        topology.clear();
        List<Location> paths = maze.getPathPoints();
        for (Location loc : paths) {
            if (loc != null && maze.isPathRaw(loc)) {
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
        int cellX = loc.getBlockX();
        int cellZ = loc.getBlockZ();
        long cell = key(cellX, cellZ);

        // A newly spawned Safe Pad can disable a cell after the shared topology was
        // built. Mobs on that cell must be removed immediately rather than teleported
        // off the pad or left fighting a stale cached route.
        if (!maze.isPath(loc)) {
            forget(entity);
            entity.remove();
            return false;
        }

        if (!topology.contains(cell)) {
            Location nearest = maze.getClosestPath(loc);
            if (nearest == null) return false;
            entity.teleport(nearest);
            loc = nearest;
            cellX = loc.getBlockX();
            cellZ = loc.getBlockZ();
            cell = key(cellX, cellZ);
        }

        MobRoute route = routes.get(entity.getUniqueId());
        if (route == null) {
            route = createRoute(loc);
            if (route == null) return false;
            routes.put(entity.getUniqueId(), route);
        }

        // A fast mob can cross more than one block between ticks. Advance the cached
        // tape until its current route cell catches up with the mob's actual cell.
        // If the mob was knocked or otherwise moved off its cached route, regenerate
        // from its current cell instead of applying the wrong direction at a corner.
        if (route.cellX != cellX || route.cellZ != cellZ) {
            boolean caughtUp = false;
            int x = route.cellX;
            int z = route.cellZ;
            int index = route.index;
            for (int i = 0; i < MAX_CATCHUP_CELLS && index < route.directions.length; i++) {
                int direction = route.directions[index];
                x += dx(direction);
                z += dz(direction);
                index++;
                if (x == cellX && z == cellZ) {
                    route.cellX = x;
                    route.cellZ = z;
                    route.index = index;
                    caughtUp = true;
                    break;
                }
            }
            if (!caughtUp) {
                route = createRoute(loc);
                if (route == null) return false;
                routes.put(entity.getUniqueId(), route);
            }
        }

        if (route.index >= route.directions.length) {
            route = createRoute(loc);
            if (route == null) return false;
            routes.put(entity.getUniqueId(), route);
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

        // Lagless's intended baseline is 1.0, with the existing stage multiplier
        // providing the 1.0 -> 1.2 -> 1.4 ... progression. The old 1.4 base was
        // appropriate for the target-based controller, but made continuous cached
        // movement substantially faster because it no longer spent time decelerating
        // into each waypoint.
        return UtilEnt.CreatureMoveFast(entity, target, speed / 1.4f);
    }

    private MobRoute createRoute(Location start) {
        if (start == null) return null;

        int x = start.getBlockX();
        int z = start.getBlockZ();
        long startKey = key(x, z);
        if (!topology.contains(startKey) || !maze.isPath(start)) return null;

        byte[] directions = new byte[ROUTE_LENGTH];
        int previous = -1;

        for (int i = 0; i < directions.length; i++) {
            int[] choices = new int[4];
            int count = 0;

            for (int direction = 0; direction < 4; direction++) {
                if (!hasActiveNeighbour(x, z, direction)) continue;
                if (previous >= 0 && direction == opposite(previous)) continue;
                choices[count++] = direction;
            }

            // At a dead end the only legal exit is the reverse direction. This is
            // intentionally a fallback rather than a special movement mode.
            if (count == 0) {
                for (int direction = 0; direction < 4; direction++) {
                    if (hasActiveNeighbour(x, z, direction)) choices[count++] = direction;
                }
            }

            if (count == 0) return null;

            int chosen = choices[random.nextInt(count)];
            directions[i] = (byte) chosen;
            previous = chosen;
            x += dx(chosen);
            z += dz(chosen);
        }

        return new MobRoute(directions, start.getBlockX(), start.getBlockZ());
    }

    private boolean hasActiveNeighbour(int x, int z, int direction) {
        int nx = x + dx(direction);
        int nz = z + dz(direction);
        return topology.contains(key(nx, nz)) && maze.isPath(mazeLocation(nx, nz));
    }

    private Location mazeLocation(int x, int z) {
        Location center = maze.getCenter().clone();
        center.setX(x + 0.5D);
        center.setZ(z + 0.5D);
        return center;
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
        private int cellX;
        private int cellZ;

        private MobRoute(byte[] directions, int cellX, int cellZ) {
            this.directions = directions;
            this.index = 0;
            this.cellX = cellX;
            this.cellZ = cellZ;
        }
    }
}
