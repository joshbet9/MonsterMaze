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
 * The important speed invariant is that this controller still uses the original
 * CreatureMoveFast speed input (1.4 * stage multiplier). The old controller's
 * apparent average speed was lower because its target was the next turn/intersection
 * and CreatureMoveFast deliberately capped the final approach when that target was
 * within two blocks. The first cached implementation used a moving six-block
 * lookahead, which prevented that approach phase from happening and was therefore
 * genuinely faster even with the same 1.4 input.
 *
 * This cache reproduces that target geometry without scanning maze blocks at runtime:
 * each route cell stores the direction chosen from the cached topology, and the
 * runtime target is the final cell before that direction changes. Thus straight
 * corridors continue to the next turn, corners/dead ends become targets, and the
 * existing near-target speed cap remains active exactly where it was before.
 *
 * Direction selection works for any number of exits:
 * - 1 exit: forced (including a dead-end U-turn)
 * - 2 exits: choose between available exits, excluding reverse when possible
 * - 3/4 exits: choose from all non-reverse exits
 *
 * Safe Pads remain dynamic. Permanent topology is cached, while live path state is
 * checked when a route is generated and on every movement tick for the mob's cell.
 */
public final class LaglessMobRouteCache {
    private static final int ROUTE_LENGTH = 4096;
    private static final int MAX_CATCHUP_CELLS = 8;

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

        // A fast mob can cross more than one maze cell between ticks. Advance the
        // cached route until its current cell catches the mob's actual cell. If the
        // mob was knocked or otherwise moved off-route, regenerate from its actual
        // cell instead of applying the wrong direction at a corner.
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

        // The original movement controller targeted the next turn/intersection,
        // rather than a point that moved with the mob. Because the direction tape is
        // cached, we can recover that same target without touching maze blocks:
        // continue along the current direction until the cached direction changes.
        int targetX = cellX;
        int targetZ = cellZ;
        int scan = route.index;
        while (scan < route.directions.length && route.directions[scan] == direction) {
            targetX += dx(direction);
            targetZ += dz(direction);
            scan++;
        }

        Location target = mazeLocation(targetX, targetZ);

        // Deliberately keep the original 1.4 * stageMultiplier input. The old
        // controller's near-target cap is part of its speed profile; changing the
        // baseline to 1.0 would make the long-corridor portion slower than Mineplex.
        return UtilEnt.CreatureMoveFast(entity, target, speed);
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
