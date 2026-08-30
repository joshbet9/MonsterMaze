package me.monstermaze.entity;

import me.monstermaze.MonsterMazePlugin;
import me.monstermaze.entity.MazeMobWaypoint.CardinalDirection;
import me.monstermaze.game.GameManager;
import me.monstermaze.event.MonsterBumpPlayerEvent;
import me.monstermaze.game.GameState;
import me.monstermaze.game.MazeMode;
import me.monstermaze.game.SafePad;
import me.monstermaze.kit.KitType;
import me.monstermaze.maze.MazeGenerator;
import me.monstermaze.util.UtilAction;
import me.monstermaze.util.UtilEnt;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Snowman;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Random;
import java.util.UUID;

/**
 * Monster movement/bump matching original Maze.java:
 * - getTarget() walks a cardinal line to the next intersection
 * - no U-turn when alternatives exist
 * - CreatureMoveFast-style navigation at MOB_MOVE_SPEED
 * - bump: range < 1, knockback trajectory str 1 / y 0.75 / maxY 1.2, 4 dmg, 1s CD
 */
public class MonsterManager {

    private final MonsterMazePlugin plugin;
    private final GameManager game;
    private MazeGenerator maze;

    private final Map<LivingEntity, MazeMobWaypoint> ents = new HashMap<LivingEntity, MazeMobWaypoint>();
    private final Map<UUID, Long> bumpCooldown = new HashMap<UUID, Long>();
    private final Random random = new Random();
    private BukkitTask tickTask;
    private BukkitTask spawnTask;

    /** PERF: monsters re-decide navigation at 10Hz; actual movement stays 20Hz and smooth. */
    private int moveTick;

    /** Entities launched by Repulsor – removed when grounded / timed out. */
    private final Map<LivingEntity, Long> launched = new HashMap<LivingEntity, Long>();

    public MonsterManager(MonsterMazePlugin plugin, GameManager game) {
        this.plugin = plugin;
        this.game = game;
    }

    public void start(MazeGenerator maze) {
        clear();
        this.maze = maze;
        int starter = game.getMode() == MazeMode.MODERN ? 225 : 150;

        // PERF: stagger the initial monster spawn (~25 per tick) instead of firing one
        // 150-225 monster spawn-packet burst to every client at once (start-of-match spike).
        // beginLive() runs ~70 ticks after start(), so the batches finish well before LIVE.
        final int[] spawned = {0};
        spawnTask = Bukkit.getScheduler().runTaskTimer(plugin, new Runnable() {
            @Override
            public void run() {
                if (game.getState() != GameState.STARTING && game.getState() != GameState.LIVE) {
                    if (spawnTask != null) spawnTask.cancel();
                    return;
                }
                if (spawned[0] >= starter) {
                    if (spawnTask != null) spawnTask.cancel();
                    return;
                }
                spawned[0] += spawnBatch(Math.min(25, starter - spawned[0]));
            }
        }, 1L, 1L);

        tickTask = Bukkit.getScheduler().runTaskTimer(plugin, new Runnable() {
            @Override
            public void run() {
                if (!game.isLive()) return;
                move();
                bump();
                tickLaunched();
            }
        }, 1L, 1L);
    }

    public void stop() {
        if (tickTask != null) {
            tickTask.cancel();
            tickTask = null;
        }
        if (spawnTask != null) {
            spawnTask.cancel();
            spawnTask = null;
        }
        clear();
        maze = null;
    }

    public void clear() {
        for (LivingEntity ent : new ArrayList<LivingEntity>(ents.keySet())) {
            if (ent != null && ent.isValid()) ent.remove();
        }
        ents.clear();
        launched.clear();
        bumpCooldown.clear();
    }

    public void fillSpawn(int numToSpawn) {
        int spawned = spawnBatch(numToSpawn);
        plugin.getLogger().info("Spawned " + spawned + " maze monsters");
    }

    /** Spawn up to {@code count} monsters at valid maze positions; returns how many spawned. */
    private int spawnBatch(int count) {
        if (maze == null || count <= 0) return 0;
        Location center = maze.getCenter();
        List<Location> paths = maze.getPathPoints();
        if (paths.isEmpty()) return 0;

        int spawned = 0;
        int guard = 0;
        while (spawned < count && guard++ < count * 5) {
            Location loc = paths.get(random.nextInt(paths.size())).clone();
            if (loc.distanceSquared(center) < 7.5 * 7.5) continue;
            if (spawnOne(loc)) spawned++;
        }
        return spawned;
    }

    private boolean spawnOne(Location loc) {
        Snowman ent = UtilEnt.spawnGhostSnowman(loc);
        if (ent == null) return false;
        ent.setRemoveWhenFarAway(false);
        ent.setCanPickupItems(false);
        ent.setMaxHealth(4.0);
        ent.setHealth(4.0);
        UtilEnt.vegetate(ent);
        ent.setCustomNameVisible(false);
        ents.put(ent, new MazeMobWaypoint(ent.getLocation()));
        return true;
    }

    public void spawnMore(int count) {
        if (maze == null) return;
        List<Location> spawns = maze.getSpawnPoints();
        List<Location> pool = spawns.isEmpty() ? maze.getPathPoints() : spawns;
        if (pool.isEmpty()) return;

        for (int i = 0; i < count; i++) {
            Location loc = pool.get(random.nextInt(pool.size())).clone();
            Snowman ent = UtilEnt.spawnGhostSnowman(loc);
            if (ent == null) continue;
            ent.setRemoveWhenFarAway(false);
            ent.setCanPickupItems(false);
            ent.setMaxHealth(4.0);
            ent.setHealth(4.0);
            UtilEnt.vegetate(ent);
            ents.put(ent, new MazeMobWaypoint(ent.getLocation()));
        }
    }

    public void increaseDifficulty() {
    }

    public void removeMonstersOn(SafePad pad) {
        if (pad == null) return;
        Iterator<Entry<LivingEntity, MazeMobWaypoint>> it = ents.entrySet().iterator();
        while (it.hasNext()) {
            Entry<LivingEntity, MazeMobWaypoint> e = it.next();
            LivingEntity en = e.getKey();
            if (en != null && en.isValid() && pad.isOn(en)) {
                launched.remove(en);
                en.remove();
                it.remove();
            }
        }
    }

    private void move() {
        if (maze == null) return;
        moveTick++;
        if ((moveTick & 1) == 0) return; // PERF: re-decide navigation at 10Hz only

        Iterator<Entry<LivingEntity, MazeMobWaypoint>> it = ents.entrySet().iterator();
        while (it.hasNext()) {
            Entry<LivingEntity, MazeMobWaypoint> data = it.next();
            LivingEntity ent = data.getKey();
            MazeMobWaypoint wp = data.getValue();

            if (ent == null || !ent.isValid() || ent.isDead()) {
                it.remove();
                continue;
            }

            if (launched.containsKey(ent)) continue;

            if (wp.Target == null || ent.getLocation().getY() < wp.Target.getBlockY()) {
                Location loc = maze.getClosestPath(ent.getLocation());
                ent.teleport(loc);
                wp.Target = loc;
            }

            if (offset2d(ent.getLocation(), wp.Target) < 0.4) {
                ArrayList<Block> nextBlock = new ArrayList<Block>();

                Block north = getTarget(ent.getLocation().getBlock(), null, BlockFace.NORTH);
                Block south = getTarget(ent.getLocation().getBlock(), null, BlockFace.SOUTH);
                Block east = getTarget(ent.getLocation().getBlock(), null, BlockFace.EAST);
                Block west = getTarget(ent.getLocation().getBlock(), null, BlockFace.WEST);

                if (north != null) nextBlock.add(north);
                if (south != null) nextBlock.add(south);
                if (east != null) nextBlock.add(east);
                if (west != null) nextBlock.add(west);

                if (nextBlock.isEmpty()) {
                    it.remove();
                    ent.remove();
                    continue;
                }

                if (nextBlock.size() > 1 && wp.Direction != CardinalDirection.NULL) {
                    if (wp.Direction == CardinalDirection.NORTH) nextBlock.remove(south);
                    else if (wp.Direction == CardinalDirection.SOUTH) nextBlock.remove(north);
                    else if (wp.Direction == CardinalDirection.WEST) nextBlock.remove(east);
                    else if (wp.Direction == CardinalDirection.EAST) nextBlock.remove(west);
                }

                if (nextBlock.isEmpty()) {
                    it.remove();
                    ent.remove();
                    continue;
                }

                Block chosen = nextBlock.get(random.nextInt(nextBlock.size()));
                Location nextLoc = chosen.getLocation();
                wp.Target = nextLoc.clone().add(0.5, 0, 0.5);

                if (north != null && nextLoc.equals(north.getLocation())) wp.Direction = CardinalDirection.NORTH;
                else if (south != null && nextLoc.equals(south.getLocation())) wp.Direction = CardinalDirection.SOUTH;
                else if (east != null && nextLoc.equals(east.getLocation())) wp.Direction = CardinalDirection.EAST;
                else if (west != null && nextLoc.equals(west.getLocation())) wp.Direction = CardinalDirection.WEST;
            }

            UtilEnt.CreatureMoveFast(ent, wp.Target, MOB_MOVE_SPEED);
        }
    }

    /**
     * Pathfinder speed multiplier calibrated against the 1.8 build's controller-move feel
     * (base snowman attribute scaled down; build 1's 1.4 read as "much quicker", so we
     * start at 0.8 and will tune in-session until the two servers feel identical).
     */
    private static final float MOB_MOVE_SPEED = 0.8f;

    private Block getTarget(Block start, Block cur, BlockFace face) {
        if (cur == null) cur = start;

        while (isWaypoint(cur.getRelative(face)) && !isDisabledWaypoint(cur.getRelative(face))) {
            cur = cur.getRelative(face);

            int count = 0;
            if (face != BlockFace.NORTH && isWaypoint(cur.getRelative(BlockFace.NORTH))
                    && !isDisabledWaypoint(cur.getRelative(BlockFace.NORTH))) count++;
            if (face != BlockFace.SOUTH && isWaypoint(cur.getRelative(BlockFace.SOUTH))
                    && !isDisabledWaypoint(cur.getRelative(BlockFace.SOUTH))) count++;
            if (face != BlockFace.EAST && isWaypoint(cur.getRelative(BlockFace.EAST))
                    && !isDisabledWaypoint(cur.getRelative(BlockFace.EAST))) count++;
            if (face != BlockFace.WEST && isWaypoint(cur.getRelative(BlockFace.WEST))
                    && !isDisabledWaypoint(cur.getRelative(BlockFace.WEST))) count++;

            if (count > 1) break;
        }

        if (cur.equals(start)) return null;
        return cur;
    }

    private boolean isWaypoint(Block b) {
        return maze != null && maze.isPathRaw(b.getLocation());
    }

    private boolean isDisabledWaypoint(Block b) {
        if (maze == null) return false;
        return !maze.isPath(b.getLocation()) && maze.isPathRaw(b.getLocation());
    }

    private void bump() {
        List<Player> players = game.getAlivePlayers();
        if (players.isEmpty()) return;

        // PERF: snapshot monster positions into flat arrays ONCE per tick (avoids re-walking
        // the entity map and repeated Location.distance() sqrt calls for every player). The 2D
        // prefilter is exact: if dx^2+dz^2 >= 1.0 the 3D distance is necessarily >= 1.0
        // (dy^2 >= 0), so the precise 3D < 1.0 range check only runs for monsters near a player.
        int m = ents.size();
        if (m == 0) return;
        LivingEntity[] mobs = new LivingEntity[m];
        double[] mx = new double[m];
        double[] my = new double[m];
        double[] mz = new double[m];
        int count = 0;
        for (LivingEntity ent : ents.keySet()) {
            if (ent == null || !ent.isValid() || launched.containsKey(ent)) continue;
            Location loc = ent.getLocation();
            mobs[count] = ent;
            mx[count] = loc.getX();
            my[count] = loc.getY();
            mz[count] = loc.getZ();
            count++;
        }

        for (Player player : players) {
            if (!canBump(player)) continue;
            if (game.isOnAnyPad(player)) continue;

            Location pl = player.getLocation();
            double px = pl.getX();
            double py = pl.getY();
            double pz = pl.getZ();

            for (int i = 0; i < count; i++) {
                double dx = px - mx[i];
                double dz = pz - mz[i];
                if (dx * dx + dz * dz >= 1.0) continue; // 2D prefilter

                double dy = py - my[i];
                double distSq = dx * dx + dy * dy + dz * dz;
                if (distSq >= 1.0) continue; // exact 3D range: was sqrt(distSq) < 1.0

                LivingEntity ent = mobs[i];
                markBump(player);

                // Anti-bonk, ping-independent: lift the player ABOVE the maze floor before applying
                // the single velocity packet, so the client never applies ground friction to the
                // launch and eats the horizontal knock. Catching anyone near the floor (not just
                // isOnGround) also covers spam-jump / ground-slam players bouncing just above it.
                {
                    double floorY = game.getCenter().getY();
                    double above = player.getLocation().getY() - floorY;
                    if (above >= 0.0 && above < 0.9) {
                        Location up = player.getLocation().clone();
                        up.setY(floorY + 0.7);
                        player.teleport(up);
                    }
                }

                // Source knock: aim away from the monster (along the hit direction) at str 1.0 with
                // a modest vertical pop. A single velocity packet is "set, not add", so it launches
                // identically on any ping — re-asserting velocity over ticks stacked on high-latency
                // clients, and a teleport-ride removed air control, so we ship the raw source knock.
                if (game.qolEnabled()) {
                    applyQolKnockback(player, ent);
                } else {
                    Vector away = player.getLocation().toVector().subtract(ent.getLocation().toVector());
                    away.setY(0);
                    if (away.lengthSquared() <= 1e-6) away = new Vector(1, 0, 0);
                    UtilAction.velocity(player, away.normalize(), 1.0, false, 0, 0.75, 1.2, true);
                }

                // The source plays no custom knock sound on a monster hit (it only sends a swing
                // animation), so we apply the damage and let the vanilla hurt sound play alone.
                player.damage(4.0);
                Bukkit.getPluginManager().callEvent(new MonsterBumpPlayerEvent(player));

                break;
            }
        }
    }

    private Vector applyQolKnockback(Player player, LivingEntity ent) {
        Vector dir = null;
        if (game.getKitManager().getKit(player) == KitType.MAVERICK) {
            Location target = game.getMobKnockTarget();
            if (target != null) {
                dir = target.toVector().subtract(player.getLocation().toVector());
                dir.setY(0);
                if (dir.lengthSquared() > 1e-6) {
                    dir = dir.normalize();
                } else {
                    dir = null;
                }
            } else {
                dir = null;
            }
        }
        if (dir == null) {
            dir = player.getLocation().toVector().subtract(ent.getLocation().toVector());
            dir.setY(0);
            if (dir.lengthSquared() <= 1e-6) {
                dir = player.getLocation().getDirection().multiply(-1);
                dir.setY(0);
                if (dir.lengthSquared() <= 1e-6) dir = new Vector(1, 0, 0);
            }
        }
        Vector out = dir.normalize();
        UtilAction.velocity(player, out, 1.0, false, 0, 0.75, 1.2, true);
        return out;
    }

    private boolean canBump(Player player) {
        Long last = bumpCooldown.get(player.getUniqueId());
        return last == null || System.currentTimeMillis() - last >= 1000L;
    }

    private void markBump(Player player) {
        bumpCooldown.put(player.getUniqueId(), System.currentTimeMillis());
    }

    public void launch(LivingEntity ent, Vector velocity) {
        if (!ents.containsKey(ent)) return;
        // Kill any active navigation BEFORE flinging: a live pathfind continues to drive the
        // mob mid-air (vanilla will keep steering/stalling along the old route instead of flying
        // ballistically), which reads as "repulsor behaves differently with the mobs" and lets a
        // launched mob drift back onto the caster and bump them. With the path stopped the flight
        // is pure ballistic, matching the 1.8 controller-glide cast.
        UtilEnt.stopNavigation(ent);
        ent.setVelocity(velocity);
        launched.put(ent, System.currentTimeMillis());
    }

    public Iterable<LivingEntity> getMonsters() {
        return ents.keySet();
    }

    private void tickLaunched() {
        long now = System.currentTimeMillis();
        Iterator<Entry<LivingEntity, Long>> it = launched.entrySet().iterator();
        while (it.hasNext()) {
            Entry<LivingEntity, Long> e = it.next();
            LivingEntity ent = e.getKey();
            long started = e.getValue();

            if (ent == null || !ent.isValid()) {
                it.remove();
                ents.remove(ent);
                continue;
            }

            boolean grounded = ent.isOnGround() && now - started > 500;
            boolean timeout = now - started > 1500;
            if (grounded || timeout) {
                it.remove();
                ents.remove(ent);
                ent.remove();
            }
        }
    }

    private static double offset2d(Location a, Location b) {
        double dx = a.getX() - b.getX();
        double dz = a.getZ() - b.getZ();
        return Math.sqrt(dx * dx + dz * dz);
    }
}