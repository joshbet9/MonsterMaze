package me.monstermaze.maze;

import org.bukkit.Location;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;

/**
 * Builds a shortest-path distance field over the maze's actual walkable cells.
 * The field is rebuilt only when the active Safe Pad changes, so HUD updates
 * and Stage 1 guidance do not repeatedly perform a full maze search.
 */
public final class MazePathfinder {
    private final Map<String, Location> nodes = new HashMap<String, Location>();
    private final Map<String, Integer> distanceToTarget = new HashMap<String, Integer>();
    private final Map<String, String> nextTowardTarget = new HashMap<String, String>();
    private boolean ready;
    private Location targetCenter;

    public void clear() {
        nodes.clear();
        distanceToTarget.clear();
        nextTowardTarget.clear();
        targetCenter = null;
        ready = false;
    }

    public void rebuild(List<Location> pathPoints, Location target) {
        clear();
        if (pathPoints == null || pathPoints.isEmpty() || target == null || target.getWorld() == null) return;
        targetCenter = target.clone();

        for (Location point : pathPoints) {
            if (point != null && point.getWorld() != null && point.getWorld().equals(target.getWorld())) {
                nodes.put(key(point), point.clone());
            }
        }
        if (nodes.isEmpty()) return;

        Queue<String> queue = new ArrayDeque<String>();
        final int padCenterX = target.getBlockX();
        final int padCenterZ = target.getBlockZ();

        // Treat the full 5x5 Safe Pad footprint as the destination, not its centre.
        for (Map.Entry<String, Location> entry : nodes.entrySet()) {
            Location point = entry.getValue();
            if (Math.abs(point.getBlockX() - padCenterX) <= 2
                    && Math.abs(point.getBlockZ() - padCenterZ) <= 2) {
                distanceToTarget.put(entry.getKey(), Integer.valueOf(0));
                queue.add(entry.getKey());
            }
        }

        // Defensive fallback if no path node overlaps the pad footprint.
        if (queue.isEmpty()) {
            String targetKey = key(target);
            if (!nodes.containsKey(targetKey)) {
                Location nearest = null;
                double nearestDistance = Double.MAX_VALUE;
                for (Location point : nodes.values()) {
                    double d = horizontalDistanceSquared(point, target);
                    if (d < nearestDistance) {
                        nearestDistance = d;
                        nearest = point;
                    }
                }
                if (nearest == null) return;
                targetKey = key(nearest);
            }
            distanceToTarget.put(targetKey, Integer.valueOf(0));
            queue.add(targetKey);
        }
        final int[][] offsets = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};

        while (!queue.isEmpty()) {
            String current = queue.remove();
            Location point = nodes.get(current);
            int distance = distanceToTarget.get(current).intValue();
            int x = point.getBlockX();
            int z = point.getBlockZ();

            for (int[] offset : offsets) {
                String neighbour = (x + offset[0]) + "," + (z + offset[1]);
                if (!nodes.containsKey(neighbour) || distanceToTarget.containsKey(neighbour)) continue;
                distanceToTarget.put(neighbour, Integer.valueOf(distance + 1));
                nextTowardTarget.put(neighbour, current);
                queue.add(neighbour);
            }
        }
        ready = true;
    }

    public boolean isReady() {
        return ready && !distanceToTarget.isEmpty();
    }

    /** Returns shortest-path distance to the edge of the active 5x5 Safe Pad. */
    public double distanceInBlocks(Location from) {
        if (!isReady() || from == null || from.getWorld() == null) return Double.POSITIVE_INFINITY;

        if (targetCenter != null && from.getWorld().equals(targetCenter.getWorld())) {
            double dx = from.getX() - targetCenter.getX();
            double dz = from.getZ() - targetCenter.getZ();
            if (Math.abs(dx) < 2.5 && Math.abs(dz) < 2.5) return 0.0;
        }

        double routeDistance = bestRouteDistance(from);
        if (Double.isInfinite(routeDistance) || Double.isNaN(routeDistance)) {
            return Double.POSITIVE_INFINITY;
        }

        // Grid distances are measured between block centres; subtract the half-block
        // from the first outside cell centre to the physical edge of the pad.
        return Math.max(0.0, routeDistance - 0.5);
    }

    /**
     * Estimates route distance by minimizing path steps plus horizontal offset.
     * Prefer nearby cells first; only scan the full route graph if the player is
     * outside the local maze track (for example, after being knocked away).
     */
    private double bestRouteDistance(Location from) {
        int px = from.getBlockX();
        int pz = from.getBlockZ();
        double bestDistance = Double.MAX_VALUE;

        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                String candidate = (px + dx) + "," + (pz + dz);
                Integer steps = distanceToTarget.get(candidate);
                if (steps == null) continue;
                Location point = nodes.get(candidate);
                double d = steps.doubleValue() + Math.sqrt(horizontalDistanceSquared(point, from));
                if (d < bestDistance) bestDistance = d;
            }
        }
        if (bestDistance < Double.MAX_VALUE) return bestDistance;

        for (Map.Entry<String, Location> entry : nodes.entrySet()) {
            if (!distanceToTarget.containsKey(entry.getKey())) continue;
            Location point = entry.getValue();
            if (!point.getWorld().equals(from.getWorld())) continue;
            double d = distanceToTarget.get(entry.getKey()).doubleValue()
                    + Math.sqrt(horizontalDistanceSquared(point, from));
            if (d < bestDistance) bestDistance = d;
        }
        return bestDistance == Double.MAX_VALUE ? Double.POSITIVE_INFINITY : bestDistance;
    }

    /**
     * Returns sparse route markers starting near the player and proceeding along the
     * shortest route to the current Safe Pad. maxNodes bounds particle work per viewer.
     */
    public List<Location> trailTowardTarget(Location from, int maxNodes, int interval) {
        if (!isReady() || maxNodes <= 0) return Collections.emptyList();
        String current = nearestReachableNode(from);
        if (current == null) return Collections.emptyList();
        int stride = Math.max(1, interval);
        List<Location> markers = new ArrayList<Location>();
        int step = 0;
        while (current != null && step < maxNodes) {
            if (step > 0 && (step % stride) == 0) {
                Location marker = nodes.get(current).clone().add(0, 0.25, 0);
                markers.add(marker);
            }
            if (Integer.valueOf(0).equals(distanceToTarget.get(current))) break;
            current = nextTowardTarget.get(current);
            step++;
        }
        return markers;
    }

    private String nearestReachableNode(Location from) {
        if (!isReady() || from == null || from.getWorld() == null) return null;
        int px = from.getBlockX();
        int pz = from.getBlockZ();
        String best = null;
        double bestDistance = Double.MAX_VALUE;

        // Most players are standing directly on a tracked maze cell; check the local area first.
        for (int radius = 0; radius <= 2 && best == null; radius++) {
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != radius) continue;
                    String candidate = (px + dx) + "," + (pz + dz);
                    if (!distanceToTarget.containsKey(candidate)) continue;
                    Location point = nodes.get(candidate);
                    double d = horizontalDistanceSquared(point, from);
                    if (d < bestDistance) {
                        bestDistance = d;
                        best = candidate;
                    }
                }
            }
        }
        if (best != null) return best;

        // Fallback for players launched away from the track.
        for (Map.Entry<String, Location> entry : nodes.entrySet()) {
            if (!distanceToTarget.containsKey(entry.getKey())) continue;
            Location point = entry.getValue();
            if (!point.getWorld().equals(from.getWorld())) continue;
            double d = horizontalDistanceSquared(point, from);
            if (d < bestDistance) {
                bestDistance = d;
                best = entry.getKey();
            }
        }
        return best;
    }

    private static double horizontalDistanceSquared(Location a, Location b) {
        double dx = a.getX() - b.getX();
        double dz = a.getZ() - b.getZ();
        return dx * dx + dz * dz;
    }

    private static String key(Location point) {
        return point.getBlockX() + "," + point.getBlockZ();
    }
}
