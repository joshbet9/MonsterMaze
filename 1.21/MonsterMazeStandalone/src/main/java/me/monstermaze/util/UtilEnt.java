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

        // Monster Maze owns movement; this is deliberately independent of vanilla mob AI.
        // The step is scaled from the same controller speed used by the working 1.8 port.
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

    /**
     * Remove vanilla goals while deliberately leaving the entity's movement/physics system alive.
     * This mirrors the 1.8 ghost entities: Monster Maze supplies all routing itself.
     */
    public static void vegetate(Entity ent) {
        if (ent == null) return;
        if (ent instanceof Creature) {
            try { ((Creature) ent).setTarget(null); } catch (Throwable ignored) { }
        }
        if (ent instanceof Mob) {
            Mob mob = (Mob) ent;
            try { Bukkit.getMobGoals().removeAllGoals(mob); }
            catch (Throwable t) { Bukkit.getLogger().warning("[MonsterMaze] Failed to remove vanilla mob goals: " + t); }
        }
    }

    public static void stopNavigation(Entity ent) {
        if (!(ent instanceof Mob)) return;
        try { ((Mob) ent).getPathfinder().stopPathfinding(); }
        catch (Throwable t) { Bukkit.getLogger().warning("[MonsterMaze] stopNavigation failed: " + t); }
    }

    /**
     * Spawn a Monster Maze ghost using the exact physical architecture of the working 1.8 port:
     * every maze monster is a Snow Golem underneath. The configured mob type is retained only as
     * a logical/client skin identifier. This prevents native Enderman, Piglin, Ocelot, Squid, etc.
     * AI and spawning behaviour from leaking into the game.
     */
    public static LivingEntity spawnGhostMob(Location loc, String mobType) {
        if (loc == null || loc.getWorld() == null) return null;
        try {
            Entity entity = loc.getWorld().spawnEntity(
                    loc,
                    EntityType.SNOW_GOLEM,
                    CreatureSpawnEvent.SpawnReason.CUSTOM);
            if (!(entity instanceof LivingEntity)) {
                entity.remove();
                return null;
            }

            LivingEntity living = (LivingEntity) entity;
            living.setMetadata(MonsterEntityController.MONSTER_SKIN_METADATA,
                    new org.bukkit.metadata.FixedMetadataValue(
                            me.monstermaze.MonsterMazePlugin.getInstance(),
                            normalizeSkin(mobType)));
            vegetate(living);
            MonsterEntityController.configure(living);
            return living;
        } catch (Throwable t) {
            Bukkit.getLogger().warning("[MonsterMaze] spawnGhostMob '" + mobType + "' failed: " + t);
            return null;
        }
    }

    /** Return the logical vanilla mob name used as the client skin/disguise. */
    private static String normalizeSkin(String mobType) {
        String id = mobType == null ? "" : mobType.trim().toLowerCase();
        if (id.isEmpty() || "snow_golem".equals(id)) return "snowman";
        if ("pig_zombie".equals(id) || "zombie_pigman".equals(id)) return "zombified_piglin";
        if ("eye_of_ender".equals(id)) return "eyeofender";
        return id;
    }

    public static org.bukkit.entity.Snowman spawnGhostSnowman(Location loc) {
        LivingEntity ent = spawnGhostMob(loc, "snowman");
        return ent instanceof org.bukkit.entity.Snowman ? (org.bukkit.entity.Snowman) ent : null;
    }
}
