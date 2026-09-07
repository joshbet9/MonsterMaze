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
 * Route selection is cached so the runtime movement path does not need to scan
 * the maze. Speed is deliberately independent of the distance to a turn: the
 * controller sends a constant-speed movement command in the currently cached
 * cardinal direction, and the route tape changes that direction when the mob
 * enters the next maze cell.
 *
 * The baseline is intentionally 1.0, matching the speed used by the old
 * near-target phase. The existing MonsterManager stage multiplier is then
 * applied once, so speed changes only every five stages and remains constant
 * between those changes.
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
    private static final float BASE_SPEED = 1.0f;
    private static final int LOOKAHEAD_CELLS = 3;

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
    public boolean move(LivingEntity entity, float speedMultiplier) {
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

        // Do not target the next turn. A turn target recreates the exact distance-based
        // slowdown we are removing from Lagless. Instead, give NMS a point several
        // blocks ahead in the cached cardinal direction. The dedicated constant-speed
        // helper never applies a near-target speed cap, so the command's speed is
        // determined solely by the stage multiplier.
        int targetX = cellX + dx(direction) * LOOKAHEAD_CELLS;
        int targetZ = cellZ + dz(direction) * LOOKAHEAD_CELLS;
        Location target = mazeLocation(targetX, targetZ);

        float speed = BASE_SPEED * speedMultiplier;
        return UtilEnt.CreatureMoveConstant(entity, target, speed);
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
