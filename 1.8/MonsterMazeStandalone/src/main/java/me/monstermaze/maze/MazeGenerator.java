package me.monstermaze.maze;

import me.monstermaze.MonsterMazePlugin;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

/**
 * No quartz shell — maze paths float in the void.
 * Each game only places/removes path blocks.
 */
public class MazeGenerator {

    private static final int MAZE_SIZE = 99;
    private static final int HALF = MAZE_SIZE / 2;
    private static final int BOX_PADDING = 4;
    private static final int BOX_HALF = HALF + BOX_PADDING;
    private static final int LOBBY_R = 4;

    private static final int LOBBY_Y_OFFSET = 100;

    private static final int SHELL_CLEAR_BELOW = 6;
    private static final int SHELL_CLEAR_ABOVE = 60;

    private final MonsterMazePlugin plugin;

    private Location center;
    private int[][] mazeData;

    /** Per-map maze palette. */
    private MazeBlockData theme = MazeBlockData.defaultTheme();

    /** Index of the maze pattern loaded for the current game. */
    private int patternIndex = -1;

    /**
     * Forced pattern for the next generation.
     *
     * -1 = random
     *  0 = Maze 1
     *  1 = Maze 2
     *  2 = Maze 3
     *
     * This is intentionally consumed when a maze is generated, so a forced
     * pattern applies to one game only.
     */
    private int forcedPattern = -1;

    private boolean mazeLive;

    private final List<BlockPos> mazeBlocks = new ArrayList<BlockPos>();
    private final List<Location> pathPoints = new ArrayList<Location>();
    private final Set<String> pathKeys = new HashSet<String>();
    private final Set<String> disabledWaypoints = new HashSet<String>();
    private final List<Location> spawnPoints = new ArrayList<Location>();
    private final List<Location> containmentGlass = new ArrayList<Location>();
    private final List<Location> centerSafeZone = new ArrayList<Location>();
    private final List<Location> centerSafeZonePaths = new ArrayList<Location>();
    private final List<Location> validSafePadSpawns = new ArrayList<Location>();

    private final Random random = new Random();

    public MazeGenerator(MonsterMazePlugin plugin) {
        this.plugin = plugin;
    }

    public boolean isMazeLive() {
        return mazeLive;
    }

    /**
     * Index (0, 1, 2) of the maze pattern currently loaded,
     * or -1 in the lobby/idle.
     */
    public int getPatternIndex() {
        return patternIndex;
    }

    /**
     * Get the pattern forced for the next maze.
     *
     * -1 = random
     *  0 = Maze 1
     *  1 = Maze 2
     *  2 = Maze 3
     */
    public int getForcedPattern() {
        return forcedPattern;
    }

    /**
     * Force a particular pattern for the next generated maze.
     *
     * @param pattern 0, 1, or 2 for a specific maze; -1 for random
     */
    public void setForcedPattern(int pattern) {
        if (pattern < -1 || pattern >= MazeLayouts.ALL_MAZES.length) {
            throw new IllegalArgumentException(
                    "Invalid forced maze pattern: " + pattern);
        }

        this.forcedPattern = pattern;
    }

    /** Set the maze palette used for the next generation. */
    public void setTheme(MazeBlockData theme) {
        this.theme = theme != null
                ? theme
                : MazeBlockData.defaultTheme();
    }

    public MazeBlockData getTheme() {
        return theme;
    }

    // -------------------- Lobby + cached shell --------------------

    public void buildLobby(Location origin) {
        if (mazeLive) {
            teardownMazePatternOnly();
        }

        this.center = origin.clone()
                .getBlock()
                .getLocation()
                .add(0.5, 0, 0.5);

        clearRuntimeLists();

        World world = center.getWorld();

        int cx = center.getBlockX();
        int cy = center.getBlockY();
        int cz = center.getBlockZ();

        int ly = cy + LOBBY_Y_OFFSET;

        for (int x = -LOBBY_R; x <= LOBBY_R; x++) {
            for (int z = -LOBBY_R; z <= LOBBY_R; z++) {
                world.getBlockAt(
                        cx + x,
                        ly - 1,
                        cz + z
                ).setType(Material.QUARTZ_BLOCK);
            }
        }

        world.getBlockAt(
                cx,
                ly - 1,
                cz
        ).setType(Material.EMERALD_BLOCK);

        for (int x = -LOBBY_R; x <= LOBBY_R; x++) {
            for (int z = -LOBBY_R; z <= LOBBY_R; z++) {

                if (Math.abs(x) != LOBBY_R
                        && Math.abs(z) != LOBBY_R) {
                    continue;
                }

                for (int y = 0; y <= 2; y++) {
                    world.getBlockAt(
                            cx + x,
                            ly + y,
                            cz + z
                    ).setTypeIdAndData(
                            Material.STAINED_GLASS.getId(),
                            (byte) 0,
                            false
                    );
                }
            }
        }

        mazeLive = false;

        plugin.getLogger().info(
                "Lobby ready (no shell - void only)");
    }

    // -------------------- Maze pattern only --------------------

    public void generateMaze() {
        generateMazeAsync(null);
    }

    /**
     * Only places path/center/spawn/barrier glass.
     */
    public void generateMazeAsync(final Runnable onDone) {

        if (center == null) {
            throw new IllegalStateException(
                    "No center - run /mm setcenter first");
        }

        teardownMazePatternOnly();
        clearRuntimeLists();
        mazeBlocks.clear();

        /*
         * Consume the forced pattern now.
         *
         * This means:
         *   /mm pattern 2
         *   /mm start
         *
         * generates Maze 2, then immediately returns the selection to random
         * for the following game.
         */
        int selectedPattern;

        if (forcedPattern >= 0
                && forcedPattern < MazeLayouts.ALL_MAZES.length) {

            selectedPattern = forcedPattern;

            plugin.getLogger().info(
                    "Using forced maze pattern "
                            + (selectedPattern + 1)
                            + " for this game.");

            forcedPattern = -1;

        } else {
            selectedPattern =
                    random.nextInt(MazeLayouts.ALL_MAZES.length);
        }

        this.patternIndex = selectedPattern;
        this.mazeData =
                MazeLayouts.ALL_MAZES[patternIndex];

        final World world = center.getWorld();

        final int cx = center.getBlockX();
        final int cy = center.getBlockY();
        final int cz = center.getBlockZ();

        final long t0 = System.currentTimeMillis();

        plugin.getLogger().info(
                "Placing maze pattern "
                        + (patternIndex + 1)
                        + "...");

        clearShellRegionAsync(
                world,
                cx,
                cy,
                cz,
                new Runnable() {
                    @Override
                    public void run() {

                        Bukkit.getScheduler().runTask(
                                plugin,
                                new Runnable() {

                                    int mz = 0;

                                    @Override
                                    public void run() {

                                        long slice =
                                                System.currentTimeMillis();

                                        while (mz < MAZE_SIZE
                                                && System.currentTimeMillis()
                                                - slice < 45) {

                                            buildMazeRow(
                                                    world,
                                                    cx,
                                                    cy,
                                                    cz,
                                                    mz);

                                            mz++;
                                        }

                                        if (mz < MAZE_SIZE) {

                                            Bukkit.getScheduler()
                                                    .runTaskLater(
                                                            plugin,
                                                            this,
                                                            1L);

                                            return;
                                        }

                                        rebuildPadSpawnList();

                                        mazeLive = true;

                                        plugin.getLogger().info(
                                                "Maze pattern ready in "
                                                        + (System.currentTimeMillis()
                                                        - t0)
                                                        + "ms. Pattern="
                                                        + (patternIndex + 1)
                                                        + " Paths="
                                                        + pathPoints.size()
                                                        + " blocks="
                                                        + mazeBlocks.size());

                                        if (onDone != null) {
                                            onDone.run();
                                        }
                                    }
                                });
                    }
                });
    }

    private void clearShellRegionAsync(
            final World world,
            final int cx,
            final int cy,
            final int cz,
            final Runnable onDone) {

        final int startX = cx - BOX_HALF;
        final int startZ = cz - BOX_HALF;

        final int endX = cx + BOX_HALF;
        final int endZ = cz + BOX_HALF;

        final int yLow = cy - SHELL_CLEAR_BELOW;
        final int yHigh = cy + SHELL_CLEAR_ABOVE;

        final int span =
                (endX - startX + 1)
                        * (endZ - startZ + 1);

        plugin.getLogger().info(
                "[MonsterMaze] Clearing shell region over maze ("
                        + span
                        + " cells)...");

        Bukkit.getScheduler().runTask(
                plugin,
                new Runnable() {

                    int index = 0;

                    @Override
                    public void run() {

                        long slice =
                                System.currentTimeMillis();

                        while (index < span
                                && System.currentTimeMillis()
                                - slice < 45) {

                            int x =
                                    startX
                                            + (index
                                            % (endX - startX + 1));

                            int z =
                                    startZ
                                            + (index
                                            / (endX - startX + 1));

                            for (int y = yLow; y <= yHigh; y++) {

                                Block b =
                                        world.getBlockAt(
                                                x,
                                                y,
                                                z);

                                if (b.getType() != Material.AIR) {
                                    b.setType(Material.AIR);
                                }
                            }

                            index++;
                        }

                        if (index < span) {

                            Bukkit.getScheduler()
                                    .runTaskLater(
                                            plugin,
                                            this,
                                            1L);

                        } else if (onDone != null) {

                            onDone.run();
                        }
                    }
                });
    }

    public void returnToLobby() {

        if (center == null) {
            return;
        }

        teardownMazePatternOnly();

        World world = center.getWorld();

        int cx = center.getBlockX();
        int cy = center.getBlockY();
        int cz = center.getBlockZ();

        int ly = cy + LOBBY_Y_OFFSET;

        for (int x = -LOBBY_R; x <= LOBBY_R; x++) {
            for (int z = -LOBBY_R; z <= LOBBY_R; z++) {

                setQuiet(
                        world.getBlockAt(
                                cx + x,
                                ly - 1,
                                cz + z),
                        Material.QUARTZ_BLOCK);
            }
        }

        setQuiet(
                world.getBlockAt(
                        cx,
                        ly - 1,
                        cz),
                Material.EMERALD_BLOCK);

        for (int x = -LOBBY_R; x <= LOBBY_R; x++) {
            for (int z = -LOBBY_R; z <= LOBBY_R; z++) {

                if (Math.abs(x) != LOBBY_R
                        && Math.abs(z) != LOBBY_R) {
                    continue;
                }

                for (int y = 0; y <= 2; y++) {

                    world.getBlockAt(
                            cx + x,
                            ly + y,
                            cz + z
                    ).setTypeIdAndData(
                            Material.STAINED_GLASS.getId(),
                            (byte) 0,
                            false);
                }
            }
        }

        mazeLive = false;
    }

    private void teardownMazePatternOnly() {

        if (!mazeBlocks.isEmpty()) {

            final List<BlockPos> toClear =
                    new ArrayList<BlockPos>(mazeBlocks);

            mazeBlocks.clear();

            containmentGlass.clear();

            clearRuntimeLists();

            mazeLive = false;

            Bukkit.getScheduler().runTask(
                    plugin,
                    new Runnable() {

                        int idx =
                                toClear.size() - 1;

                        @Override
                        public void run() {

                            long start =
                                    System.currentTimeMillis();

                            while (idx >= 0
                                    && System.currentTimeMillis()
                                    - start < 45) {

                                BlockPos bp =
                                        toClear.get(idx);

                                Block b =
                                        bp.world.getBlockAt(
                                                bp.x,
                                                bp.y,
                                                bp.z);

                                if (b.getType() != Material.AIR) {
                                    b.setType(Material.AIR);
                                }

                                idx--;
                            }

                            if (idx >= 0) {

                                Bukkit.getScheduler()
                                        .runTaskLater(
                                                plugin,
                                                this,
                                                1L);
                            }
                        }
                    });

        } else {

            containmentGlass.clear();
            clearRuntimeLists();
            mazeLive = false;
        }
    }

    private void clearLobbyGlassOnly() {

        if (center == null) {
            return;
        }

        World world = center.getWorld();

        int cx = center.getBlockX();
        int cy = center.getBlockY();
        int cz = center.getBlockZ();

        for (int x = -LOBBY_R; x <= LOBBY_R; x++) {
            for (int z = -LOBBY_R; z <= LOBBY_R; z++) {
                for (int y = -1; y <= 2; y++) {

                    Block b =
                            world.getBlockAt(
                                    cx + x,
                                    cy + y,
                                    cz + z);

                    if (b.getType() == Material.STAINED_GLASS
                            || b.getType() == Material.EMERALD_BLOCK
                            || (y == -1
                            && b.getType() == Material.QUARTZ_BLOCK)) {

                        b.setType(Material.AIR);
                    }
                }
            }
        }
    }

    public void generate(Location origin) {

        if (center == null) {
            this.center =
                    origin.clone()
                            .getBlock()
                            .getLocation()
                            .add(0.5, 0, 0.5);
        }

        generateMaze();
    }

    public void prepareRound() {
    }

    public void endRound() {
        returnToLobby();
    }

    public void cleanup() {
        returnToLobby();
    }

    public void dropContainment() {

        for (Location loc : containmentGlass) {

            Block b = loc.getBlock();

            if (b.getType() == Material.STAINED_GLASS) {
                b.setType(Material.AIR);
            }
        }

        containmentGlass.clear();
    }

    // -------------------- Build path row --------------------

    private void clearRuntimeLists() {
        pathPoints.clear();
        pathKeys.clear();
        disabledWaypoints.clear();
        spawnPoints.clear();
        containmentGlass.clear();
        centerSafeZone.clear();
        centerSafeZonePaths.clear();
        validSafePadSpawns.clear();
    }

    private void rebuildPadSpawnList() {

        validSafePadSpawns.clear();
        validSafePadSpawns.addAll(pathPoints);

        List<Location> filtered =
                new ArrayList<Location>();

        for (Location p : pathPoints) {

            boolean ok = true;

            for (Location s : spawnPoints) {

                if (offset2dSq(p, s) < 10 * 10) {
                    ok = false;
                    break;
                }
            }

            if (!ok) {
                continue;
            }

            for (Location g : containmentGlass) {

                if (offset2dSq(p, g) < 7 * 7) {
                    ok = false;
                    break;
                }
            }

            if (ok) {
                filtered.add(p.clone());
            }
        }

        List<Location> candidates =
                new ArrayList<Location>(filtered);

        List<Location> safeZones =
                new ArrayList<Location>();

        int numberOfSafeZones = 8;

        for (int i = 0;
             i < numberOfSafeZones && !candidates.isEmpty();
             i++) {

            List<Location> toBeAwayFrom =
                    new ArrayList<Location>(safeZones);

            toBeAwayFrom.add(center);

            Location toAdd =
                    getLocationAwayFromOtherLocations(
                            candidates,
                            toBeAwayFrom);

            safeZones.add(toAdd);

            List<Location> toRemove =
                    new ArrayList<Location>();

            for (Location c : candidates) {

                if (offset3dSq(toAdd, c) <= 6 * 6) {
                    toRemove.add(c);
                }
            }

            candidates.removeAll(toRemove);
        }

        validSafePadSpawns.clear();

        for (Location p : filtered) {

            boolean ok = true;

            for (Location z : safeZones) {

                if (offset2dSq(p, z) < 7 * 7) {
                    ok = false;
                    break;
                }
            }

            if (ok) {
                validSafePadSpawns.add(p);
            }
        }
    }

    private double offset2dSq(Location a, Location b) {

        double dx = a.getX() - b.getX();
        double dz = a.getZ() - b.getZ();

        return dx * dx + dz * dz;
    }

    private double offset3dSq(Location a, Location b) {

        double dx = a.getX() - b.getX();
        double dy = a.getY() - b.getY();
        double dz = a.getZ() - b.getZ();

        return dx * dx + dy * dy + dz * dz;
    }

    private Location getLocationAwayFromOtherLocations(
            List<Location> locations,
            List<Location> awayFrom) {

        Location bestLocation = null;
        double bestDist = -1;

        for (Location location : locations) {

            double closest = -1;

            for (Location away : awayFrom) {

                if (away == null
                        || away.getWorld() == null
                        || location.getWorld() == null
                        || !location.getWorld()
                        .getName()
                        .equals(away.getWorld().getName())) {

                    continue;
                }

                double dist =
                        offset3dSq(
                                away,
                                location);

                if (closest == -1 || dist < closest) {
                    closest = dist;
                }
            }

            if (closest == -1) {
                continue;
            }

            if (bestLocation == null
                    || closest > bestDist) {

                bestLocation = location;
                bestDist = closest;
            }
        }

        return bestLocation == null
                ? center.clone()
                : bestLocation.clone();
    }

    @SuppressWarnings("deprecation")
    private void buildMazeRow(
            World world,
            int cx,
            int cy,
            int cz,
            int mz) {

        for (int mx = 0;
             mx < MAZE_SIZE;
             mx++) {

            int val = mazeData[mz][mx];

            if (val == 0) {
                continue;
            }

            int wx = cx - HALF + mz;
            int wz = cz - HALF + mx;

            boolean isPath =
                    val == 1
                            || val == 2
                            || val == 5
                            || val == 6;

            boolean isCenter =
                    val == 3
                            || val == 4
                            || val == 5
                            || val == 6;

            boolean isBarrier =
                    val == 4
                            || val == 6;

            boolean isSpawn =
                    val == 2;

            if (isPath) {

                trackSet(
                        world.getBlockAt(
                                wx,
                                cy - 2,
                                wz),
                        theme.Middle.Type,
                        theme.Middle.Data);

                trackSet(
                        world.getBlockAt(
                                wx,
                                cy - 3,
                                wz),
                        theme.Bottom.Type,
                        theme.Bottom.Data);
            }

            if (isCenter) {

                trackSet(
                        world.getBlockAt(
                                wx,
                                cy - 1,
                                wz),
                        Material.STAINED_CLAY,
                        (byte) 5);

            } else if (isPath) {

                trackSet(
                        world.getBlockAt(
                                wx,
                                cy - 1,
                                wz),
                        theme.Top.Type,
                        theme.Top.Data);
            }

            if (isPath) {

                pathPoints.add(
                        new Location(
                                world,
                                wx + 0.5,
                                cy,
                                wz + 0.5));

                pathKeys.add(
                        wx + "," + wz);
            }

            if (isCenter) {

                Location cLoc =
                        new Location(
                                world,
                                wx + 0.5,
                                cy,
                                wz + 0.5);

                centerSafeZone.add(cLoc);

                if (val == 5 || val == 6) {

                    centerSafeZonePaths.add(cLoc);

                    disabledWaypoints.add(
                            wx + "," + wz);
                }
            }

            if (isSpawn) {

                trackSet(
                        world.getBlockAt(
                                wx,
                                cy - 1,
                                wz),
                        Material.REDSTONE_BLOCK,
                        (byte) 0);

                spawnPoints.add(
                        new Location(
                                world,
                                wx + 0.5,
                                cy,
                                wz + 0.5));
            }

            if (isBarrier) {

                for (int y = 0; y <= 2; y++) {

                    Block g =
                            world.getBlockAt(
                                    wx,
                                    cy + y,
                                    wz);

                    trackSet(
                            g,
                            Material.STAINED_GLASS,
                            (byte) 5);

                    containmentGlass.add(
                            g.getLocation());
                }
            }
        }
    }

    // -------------------- Path API --------------------

    public Location randomPadLocation() {
        return randomPadLocation(
                java.util.Collections.<Location>emptyList());
    }

    public Location randomPadLocation(List<Location> avoid) {

        List<Location> pool =
                validSafePadSpawns.isEmpty()
                        ? pathPoints
                        : validSafePadSpawns;

        if (pool.isEmpty()) {
            return center.clone();
        }

        if (avoid == null || avoid.isEmpty()) {
            return findFurthest(center, pool);
        }

        List<Location> best =
                new ArrayList<Location>();

        for (Location pos : pool) {

            boolean canAdd = true;

            for (Location a : avoid) {

                if (a != null
                        && offset3dSq(pos, a) < 40 * 40) {

                    canAdd = false;
                    break;
                }
            }

            if (canAdd) {
                best.add(pos);
            }
        }

        if (best.isEmpty()) {
            return findFurthest(center, pool);
        }

        return best
                .get(random.nextInt(best.size()))
                .clone();
    }

    private Location findFurthest(
            Location from,
            List<Location> list) {

        if (from == null
                || list == null
                || list.isEmpty()) {

            return center.clone();
        }

        double bestSq = -1;

        List<Location> best =
                new ArrayList<Location>();

        for (Location l : list) {

            double d =
                    from.distanceSquared(l);

            if (d > bestSq) {

                bestSq = d;

                best.clear();

                best.add(l);

            } else if (d == bestSq) {

                best.add(l);
            }
        }

        return best
                .get(random.nextInt(best.size()))
                .clone();
    }

    public Location getClosestPath(Location from) {

        Location best = center;
        double bestD = Double.MAX_VALUE;

        for (Location p : pathPoints) {

            double d =
                    p.distanceSquared(from);

            if (d < bestD) {

                bestD = d;
                best = p;
            }
        }

        return best.clone();
    }

    public boolean isPath(Location loc) {

        String key =
                loc.getBlockX()
                        + ","
                        + loc.getBlockZ();

        return pathKeys.contains(key)
                && !disabledWaypoints.contains(key);
    }

    public boolean isPathRaw(Location loc) {

        return pathKeys.contains(
                loc.getBlockX()
                        + ","
                        + loc.getBlockZ());
    }

    public void disableWaypoint(Location loc) {

        disabledWaypoints.add(
                loc.getBlockX()
                        + ","
                        + loc.getBlockZ());
    }

    public void enableWaypoint(Location loc) {

        disabledWaypoints.remove(
                loc.getBlockX()
                        + ","
                        + loc.getBlockZ());
    }

    public void disablePadArea(Location padCenter) {

        int cx = padCenter.getBlockX();
        int cz = padCenter.getBlockZ();

        for (int x = -2; x <= 2; x++) {
            for (int z = -2; z <= 2; z++) {

                disabledWaypoints.add(
                        (cx + x)
                                + ","
                                + (cz + z));
            }
        }
    }

    public void enablePadArea(Location padCenter) {

        int cx = padCenter.getBlockX();
        int cz = padCenter.getBlockZ();

        for (int x = -2; x <= 2; x++) {
            for (int z = -2; z <= 2; z++) {

                disabledWaypoints.remove(
                        (cx + x)
                                + ","
                                + (cz + z));
            }
        }
    }

    public List<Location> getCenterSafeZone() {
        return centerSafeZone;
    }

    public List<Location> getCenterSafeZonePaths() {
        return centerSafeZonePaths;
    }

    public List<Location> getValidSafePadSpawns() {
        return validSafePadSpawns;
    }

    public List<Location> getPathPoints() {
        return pathPoints;
    }

    public List<Location> getSpawnPoints() {
        return spawnPoints;
    }

    public Location getCenter() {
        return center == null
                ? null
                : center.clone();
    }

    public Location getLobbyCenter() {

        if (center == null) {
            return null;
        }

        return center.clone()
                .add(0, LOBBY_Y_OFFSET, 0);
    }

    public int getPlatformRadius() {
        return BOX_HALF;
    }

    @SuppressWarnings("deprecation")
    private void trackSet(
            Block block,
            Material mat,
            byte data) {

        mazeBlocks.add(
                new BlockPos(
                        block.getWorld(),
                        block.getX(),
                        block.getY(),
                        block.getZ()));

        block.setTypeIdAndData(
                mat.getId(),
                data,
                false);
    }

    private void setQuiet(
            Block block,
            Material mat) {

        if (block.getType() != mat) {
            block.setType(mat);
        }
    }

    private static class BlockPos {

        final World world;
        final int x;
        final int y;
        final int z;

        BlockPos(
                World world,
                int x,
                int y,
                int z) {

            this.world = world;
            this.x = x;
            this.y = y;
            this.z = z;
        }
    }
}
