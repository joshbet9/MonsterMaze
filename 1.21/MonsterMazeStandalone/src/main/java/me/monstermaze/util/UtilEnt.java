package me.monstermaze.util;

import me.monstermaze.entity.MonsterEntityController;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Creature;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;

/** Paper 1.21 entity utilities used by Monster Maze. */
public final class UtilEnt {
    private UtilEnt() {}

    public static boolean CreatureMoveFast(Entity ent, Location target, float speed) {
        return CreatureMoveFast(ent, target, speed, true);
    }

    public static boolean CreatureMoveFast(Entity ent, Location target, float speed, boolean slow) {
        if (!(ent instanceof Mob)) return false;
        double distSq = offsetSquared(ent.getLocation(), target);
        if (distSq < 0.01) return false;
        if (distSq < 4) speed = Math.min(speed, 1f);
        try {
            return ((Mob) ent).getPathfinder().moveTo(target, speed);
        } catch (Throwable t) {
            Bukkit.getLogger().warning("[MonsterMaze] CreatureMoveFast failed: " + t);
            return false;
        }
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
