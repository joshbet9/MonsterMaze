package me.monstermaze.entity;

import me.monstermaze.MonsterMazePlugin;
import me.monstermaze.entity.MazeMobWaypoint.CardinalDirection;
import me.monstermaze.event.MonsterBumpPlayerEvent;
import me.monstermaze.game.GameManager;
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
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
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
 * Monster movement/bump controller for the 1.21 implementation.
 *
 * <p>Monster Maze owns the monster route. The entity itself is a real vanilla mob so the client
 * receives the correct 1.21 renderer, while vanilla autonomous goals are removed by UtilEnt.</p>
 */
public class MonsterManager {
    private static final float MOB_MOVE_SPEED = 0.8f;

    private final MonsterMazePlugin plugin;
    private final GameManager game;
    private MazeGenerator maze;
    private String mobType = "snowman";

    private final Map<LivingEntity, MazeMobWaypoint> ents = new HashMap<LivingEntity, MazeMobWaypoint>();
    private final Map<UUID, Long> bumpCooldown = new HashMap<UUID, Long>();
    private final Map<LivingEntity, Long> launched = new HashMap<LivingEntity, Long>();
    private final Map<LivingEntity, Long> frozen = new HashMap<LivingEntity, Long>();
    private final Random random = new Random();
    private BukkitTask tickTask;
    private BukkitTask spawnTask;
    private int moveTick;

    public MonsterManager(MonsterMazePlugin plugin, GameManager game) {
        this.plugin = plugin;
        this.game = game;
    }

    /** Set the visual/physical vanilla entity type used for newly spawned maze ghosts. */
    public void setMobType(String mobType) {
        if (mobType == null || mobType.trim().isEmpty()) this.mobType = "snowman";
        else this.mobType = mobType.trim().toLowerCase();
    }

    public String getMobType() {
        return mobType;
    }

    public void start(MazeGenerator maze) {
        clear();
        this.maze = maze;
        int starter = game.getMode() == MazeMode.MODERN ? 225 : 150;

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
                tickFrozen();
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
        frozen.clear();
        bumpCooldown.clear();
    }

    public void fillSpawn(int numToSpawn) {
        int spawned = spawnBatch(numToSpawn);
        plugin.getLogger().info("Spawned " + spawned + " maze monsters");
    }

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
        LivingEntity ent = UtilEnt.spawnGhostMob(loc, mobType);
        if (ent == null) return false;
        ent.setRemoveWhenFarAway(false);
        ent.setCanPickupItems(false);
        ent.setMaxHealth(4.0);
        ent.setHealth(4.0);
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
            LivingEntity ent = UtilEnt.spawnGhostMob(loc, mobType);
            if (ent == null) continue;
            ent.setRemoveWhenFarAway(false);
            ent.setCanPickupItems(false);
            ent.setMaxHealth(4.0);
            ent.setHealth(4.0);
            ent.setCustomNameVisible(false);
            ents.put(ent, new MazeMobWaypoint(ent.getLocation()));
        }
    }

    /** Difficulty scaling hook retained for parity with the 1.8 implementation. */
    public void increaseDifficulty() {
    }

    public void removeMonstersOn(SafePad pad) {
        if (pad == null) return;
        Iterator<Entry<LivingEntity, MazeMobWaypoint>> it = ents.entrySet().iterator();
        while (it.hasNext()) {
            Entry<LivingEntity, MazeMobWaypoint> e = it.next();
            LivingEntity ent = e.getKey();
            if (ent != null && ent.isValid() && pad.isOn(ent)) {
                launched.remove(ent);
                frozen.remove(ent);
                ent.remove();
                it.remove();
            }
        }
    }

    private void move() {
        if (maze == null) return;
        moveTick++;
        if ((moveTick & 1) == 0) return;

        Iterator<Entry<LivingEntity, MazeMobWaypoint>> it = ents.entrySet().iterator();
        while (it.hasNext()) {
            Entry<LivingEntity, MazeMobWaypoint> data = it.next();
            LivingEntity ent = data.getKey();
            MazeMobWaypoint wp = data.getValue();

            if (ent == null || !ent.isValid() || ent.isDead()) {
                it.remove();
                continue;
            }
            if (launched.containsKey(ent) || frozen.containsKey(ent)) continue;

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

    private Block getTarget(Block start, Block cur, BlockFace face) {
        if (cur == null) cur = start;
        while (isWaypoint(cur.getRelative(face)) && !isDisabledWaypoint(cur.getRelative(face))) {
            cur = cur.getRelative(face);
            int count = 0;
            if (face != BlockFace.NORTH && isWaypoint(cur.getRelative(BlockFace.NORTH)) && !isDisabledWaypoint(cur.getRelative(BlockFace.NORTH))) count++;
            if (face != BlockFace.SOUTH && isWaypoint(cur.getRelative(BlockFace.SOUTH)) && !isDisabledWaypoint(cur.getRelative(BlockFace.SOUTH))) count++;
            if (face != BlockFace.EAST && isWaypoint(cur.getRelative(BlockFace.EAST)) && !isDisabledWaypoint(cur.getRelative(BlockFace.EAST))) count++;
            if (face != BlockFace.WEST && isWaypoint(cur.getRelative(BlockFace.WEST)) && !isDisabledWaypoint(cur.getRelative(BlockFace.WEST))) count++;
            if (count > 1) break;
        }
        return cur.equals(start) ? null : cur;
    }

    private boolean isWaypoint(Block b) {
        return maze != null && maze.isPathRaw(b.getLocation());
    }

    private boolean isDisabledWaypoint(Block b) {
        return maze != null && !maze.isPath(b.getLocation()) && maze.isPathRaw(b.getLocation());
    }

    private void bump() {
        List<Player> players = game.getAlivePlayers();
        if (players.isEmpty() || ents.isEmpty()) return;

        int m = ents.size();
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
            if (!canBump(player) || game.isOnAnyPad(player)) continue;
            Location pl = player.getLocation();
            for (int i = 0; i < count; i++) {
                double dx = pl.getX() - mx[i];
                double dz = pl.getZ() - mz[i];
                if (dx * dx + dz * dz >= 1.0) continue;
                double dy = pl.getY() - my[i];
                if (dx * dx + dy * dy + dz * dz >= 1.0) continue;

                LivingEntity ent = mobs[i];
                markBump(player);

                me.monstermaze.kit.KitManager km = game.getKitManager();
                if (km != null && km.isBodyRushActive(player)) {
                    Vector away = ent.getLocation().toVector().subtract(player.getLocation().toVector());
                    away.setY(0);
                    if (away.lengthSquared() <= 1e-6) away = new Vector(1, 0, 0);
                    UtilAction.velocity(ent, away.normalize(), 1, true, 0, 0.8, 2, true);
                    launch(ent, ent.getVelocity());
                    km.consumeBodyRushUse(player);
                    player.getWorld().playSound(ent.getLocation(), org.bukkit.Sound.ENTITY_PLAYER_HURT, 1.2f, 0.8f);
                    break;
                }

                double floorY = game.getCenter().getY();
                double above = player.getLocation().getY() - floorY;
                if (above >= 0.0 && above < 0.9) {
                    Location up = player.getLocation().clone();
                    up.setY(floorY + 0.7);
                    player.teleport(up);
                }

                if (game.qolEnabled()) applyQolKnockback(player, ent);
                else {
                    Vector away = player.getLocation().toVector().subtract(ent.getLocation().toVector());
                    away.setY(0);
                    if (away.lengthSquared() <= 1e-6) away = new Vector(1, 0, 0);
                    UtilAction.velocity(player, away.normalize(), 1.0, false, 0, 0.75, 1.2, true);
                }

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
                if (dir.lengthSquared() > 1e-6) dir.normalize();
                else dir = null;
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
        frozen.remove(ent);
        UtilEnt.stopNavigation(ent);
        ent.setVelocity(velocity);
        launched.put(ent, System.currentTimeMillis());
    }

    public Iterable<LivingEntity> getMonsters() {
        return ents.keySet();
    }

    public void freeze(LivingEntity ent, long thawAt) {
        if (!ents.containsKey(ent)) return;
        launched.remove(ent);
        UtilEnt.stopNavigation(ent);
        frozen.put(ent, thawAt);
    }

    private void tickFrozen() {
        if (frozen.isEmpty()) return;
        long now = System.currentTimeMillis();
        Iterator<Entry<LivingEntity, Long>> it = frozen.entrySet().iterator();
        while (it.hasNext()) {
            Entry<LivingEntity, Long> e = it.next();
            LivingEntity ent = e.getKey();
            if (ent == null || !ent.isValid() || now >= e.getValue()) it.remove();
        }
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
                frozen.remove(ent);
                ents.remove(ent);
                continue;
            }
            boolean grounded = ent.isOnGround() && now - started > 500;
            boolean timeout = now - started > 1500;
            if (grounded || timeout) {
                it.remove();
                frozen.remove(ent);
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
