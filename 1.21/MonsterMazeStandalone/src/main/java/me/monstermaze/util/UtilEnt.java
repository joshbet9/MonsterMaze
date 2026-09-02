package me.monstermaze.util;

import me.monstermaze.entity.MonsterEntityController;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Creature;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.util.Vector;

/** Paper 1.21 entity utilities used by Monster Maze. */
public final class UtilEnt {
    private UtilEnt() {}

    /** Entity-type-independent Monster Maze movement. */
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
        delta.setY(0);
        double distance = delta.length();
        if (distance < 1.0E-6) return false;

        // The old 1.21 implementation hard-coded 0.07 blocks here, ignoring the controller
        // speed completely. MonsterManager supplies the controller value, so movement must scale
        // from that value. With the current 0.8 controller this gives 0.14 blocks per movement
        // call, matching the intended Snowman-ghost movement scale rather than vanilla AI.
        double step = Math.min(speed * 0.175D, distance);
        Vector movement = delta.normalize().multiply(step);
        Location next = loc.clone().add(movement);
        next.setY(loc.getY());
        next.setYaw((float) Math.toDegrees(Math.atan2(-movement.getX(), movement.getZ())));
        next.setPitch(loc.getPitch());
        ent.setVelocity(new Vector(0, 0, 0));
        ent.teleport(next);
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

    public static void vegetate(Entity ent) {
        if (ent == null) return;
        if (ent instanceof Creature) {
            try { ((Creature) ent).setTarget(null); } catch (Throwable ignored) { }
        }
        if (ent instanceof Mob) {
            Mob mob = (Mob) ent;
            try { Bukkit.getMobGoals().removeAllGoals(mob); }
            catch (Throwable t) { Bukkit.getLogger().warning("[MonsterMaze] Failed to remove vanilla mob goals: " + t); }
            mob.setAI(false);
            mob.setAware(true);
        }
    }

    public static void stopNavigation(Entity ent) {
        if (!(ent instanceof Mob)) return;
        try { ((Mob) ent).getPathfinder().stopPathfinding(); }
        catch (Throwable t) { Bukkit.getLogger().warning("[MonsterMaze] stopNavigation failed: " + t); }
    }

    public static LivingEntity spawnGhostMob(Location loc, String mobType) {
        if (loc == null || loc.getWorld() == null) return null;
        EntityType type = resolveMobType(mobType);
        if (type == null || !type.isAlive()) {
            Bukkit.getLogger().warning("[MonsterMaze] Unsupported mob type '" + mobType + "'");
            return null;
        }
        try {
            // Explicit CUSTOM is important because map worlds intentionally reject every natural
            // creature spawn. Monster Maze owns these entities; the renderer mob is only the skin.
            Entity entity = loc.getWorld().spawnEntity(loc, type, CreatureSpawnEvent.SpawnReason.CUSTOM);
            if (!(entity instanceof LivingEntity)) { entity.remove(); return null; }
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
        try { return EntityType.valueOf(id.toUpperCase().replace('-', '_').replace(' ', '_')); }
        catch (IllegalArgumentException ignored) { return null; }
    }

    public static org.bukkit.entity.Snowman spawnGhostSnowman(Location loc) {
        LivingEntity ent = spawnGhostMob(loc, "snowman");
        return ent instanceof org.bukkit.entity.Snowman ? (org.bukkit.entity.Snowman) ent : null;
    }
}
