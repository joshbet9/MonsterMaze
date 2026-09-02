package me.monstermaze.util;

import me.monstermaze.entity.MonsterEntityController;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Creature;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.util.Vector;

/** Paper 1.21 entity utilities used by Monster Maze. */
public final class UtilEnt {
    private UtilEnt() {}

    /**
     * Monster Maze movement is deliberately entity-type independent.
     *
     * The original 1.8 implementation spawned the same Snowman ghost for every monster skin and
     * drove it through ControllerMove with speed 1.4f. The 1.21 implementation must not delegate
     * movement to each vanilla mob's Navigation implementation: those navigators have different
     * collision/pathing rules and can leave some renderer mobs stationary or make them behave
     * differently. Instead we reproduce the old ControllerMove fallback mathematically: horizontal
     * direction toward the target, speed * 0.2 blocks/tick, every tick.
     */
    public static boolean CreatureMoveFast(Entity ent, Location target, float speed) {
        return CreatureMoveFast(ent, target, speed, true);
    }

    public static boolean CreatureMoveFast(Entity ent, Location target, float speed, boolean slow) {
        if (ent == null || target == null || ent.getWorld() != target.getWorld()) return false;
        double distSq = offsetSquared(ent.getLocation(), target);
        if (distSq < 0.01) return false;
        if (distSq < 4) speed = Math.min(speed, 1f);

        Location loc = ent.getLocation();
        Vector delta = target.toVector().subtract(loc.toVector());
        // CreatureMoveFast in Monster Maze is horizontal maze travel. Do not let a vanilla mob's
        // navigation decide how to climb/fall; the maze waypoint Y is already authoritative.
        delta.setY(0);
        if (delta.lengthSquared() < 1.0E-6) {
            ent.setVelocity(new Vector(0, ent.getVelocity().getY(), 0));
            return false;
        }

        double step = Math.min(speed * 0.2D, Math.sqrt(delta.getX() * delta.getX() + delta.getZ() * delta.getZ()));
        Vector velocity = delta.normalize().multiply(step);
        // Preserve the entity's vertical physics while taking complete ownership of horizontal
        // movement, exactly as the old Snowman ControllerMove did for the maze.
        velocity.setY(ent.getVelocity().getY());
        ent.setVelocity(velocity);

        float yaw = (float) Math.toDegrees(Math.atan2(-velocity.getX(), velocity.getZ()));
        ent.setRotation(yaw, ent.getLocation().getPitch());
        return true;
    }

    public static double offsetSquared(Location a, Location b) {
        if (a == null || b == null || a.getWorld() != b.getWorld()) return Double.MAX_VALUE;
        double dx = a.getX() - b.getX();
        double dy = a.getY() - b.getY();
        double dz = a.getZ() - b.getZ();
        return dx * dx + dy * dy + dz * dz;
    }

    public static boolean isGrounded(Entity ent) {
        if (ent == null) return false;
        if (ent.isOnGround()) return true;
        try {
            Location loc = ent.getLocation();
            return loc.getWorld().getBlockAt(loc.getBlockX(), loc.getBlockY() - 1, loc.getBlockZ()).getType().isSolid();
        } catch (Throwable t) {
            return false;
        }
    }

    /** Remove autonomous vanilla goals while leaving the entity usable by Monster Maze. */
    public static void vegetate(Entity ent) {
        if (ent == null) return;
        if (ent instanceof Creature) {
            try { ((Creature) ent).setTarget(null); } catch (Throwable ignored) { }
        }
        if (ent instanceof Mob) {
            Mob mob = (Mob) ent;
            mob.setAI(true);
            mob.setAware(true);
            try {
                Bukkit.getMobGoals().removeAllGoals(mob);
            } catch (Throwable t) {
                Bukkit.getLogger().warning("[MonsterMaze] Failed to remove vanilla mob goals: " + t);
            }
        }
    }

    public static void stopNavigation(Entity ent) {
        if (!(ent instanceof Mob)) return;
        try {
            ((Mob) ent).getPathfinder().stopPathfinding();
        } catch (Throwable t) {
            Bukkit.getLogger().warning("[MonsterMaze] stopNavigation failed: " + t);
        }
    }

    /** Spawn the selected renderer entity, then normalise it to the Monster Maze ghost contract. */
    public static LivingEntity spawnGhostMob(Location loc, String mobType) {
        if (loc == null || loc.getWorld() == null) return null;
        EntityType type = resolveMobType(mobType);
        if (type == null || !type.isAlive()) {
            Bukkit.getLogger().warning("[MonsterMaze] Unsupported mob type '" + mobType + "'");
            return null;
        }
        try {
            Entity entity = loc.getWorld().spawnEntity(loc, type);
            if (!(entity instanceof LivingEntity)) {
                entity.remove();
                return null;
            }
            LivingEntity living = (LivingEntity) entity;
            vegetate(living);
            MonsterEntityController.configure(living);
            return living;
        } catch (Throwable t) {
            Bukkit.getLogger().warning("[MonsterMaze] spawnGhostMob '" + mobType + "' failed: " + t);
            return null;
        }
    }

    private static EntityType resolveMobType(String mobType) {
        String id = mobType == null ? "" : mobType.trim().toLowerCase();
        if (id.isEmpty() || "snowman".equals(id) || "snow_golem".equals(id)) return EntityType.SNOW_GOLEM;
        if ("zombified_piglin".equals(id) || "pig_zombie".equals(id) || "zombie_pigman".equals(id)) return EntityType.ZOMBIFIED_PIGLIN;
        if ("eyeofender".equals(id)) return EntityType.ENDERMAN;
        try {
            return EntityType.valueOf(id.toUpperCase().replace('-', '_').replace(' ', '_'));
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    public static org.bukkit.entity.Snowman spawnGhostSnowman(Location loc) {
        LivingEntity ent = spawnGhostMob(loc, "snowman");
        return ent instanceof org.bukkit.entity.Snowman ? (org.bukkit.entity.Snowman) ent : null;
    }
}
