package me.monstermaze.util;

import org.bukkit.Location;
import org.bukkit.entity.Creature;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;

/**
 * Paper 1.21 entity utilities used by Monster Maze.
 *
 * <p>The 1.8 build used a custom Snowman whose network entity registration was changed so the
 * client rendered it as the selected monster. That NMS registration trick does not have a direct
 * 1.21 equivalent. The modern equivalent is to spawn the actual vanilla entity type, then strip
 * its autonomous goals and let Monster Maze drive all movement itself.</p>
 */
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
            org.bukkit.Bukkit.getLogger().warning("[MonsterMaze] CreatureMoveFast failed: " + t);
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

    /** Remove autonomous vanilla goals while leaving AI/pathfinding available to Monster Maze. */
    public static void vegetate(Entity ent) {
        if (ent == null) return;
        if (ent instanceof Creature) {
            try { ((Creature) ent).setTarget(null); } catch (Throwable ignored) { }
        }
        if (ent instanceof Mob) {
            ((Mob) ent).setAI(true);
            ((Mob) ent).setAware(true);
            clearVanillaGoals(ent);
        }
    }

    public static void stopNavigation(Entity ent) {
        if (ent == null) return;
        try {
            Object handle = ent.getClass().getMethod("getHandle").invoke(ent);
            Object nav = handle.getClass().getMethod("getNavigation").invoke(handle);
            if (nav != null) nav.getClass().getMethod("stop").invoke(nav);
        } catch (Throwable t) {
            org.bukkit.Bukkit.getLogger().warning("[MonsterMaze] stopNavigation failed: " + t);
        }
    }

    private static void clearVanillaGoals(Entity ent) {
        try {
            Object handle = ent.getClass().getMethod("getHandle").invoke(ent);
            for (String fieldName : new String[]{"goalSelector", "targetSelector"}) {
                java.lang.reflect.Field field = null;
                for (Class<?> c = handle.getClass(); c != null && c != Object.class; c = c.getSuperclass()) {
                    try {
                        field = c.getDeclaredField(fieldName);
                        break;
                    } catch (NoSuchFieldException ignore) { }
                }
                if (field == null) continue;
                field.setAccessible(true);
                Object selector = field.get(handle);
                if (selector == null) continue;
                for (java.lang.reflect.Field f : selector.getClass().getDeclaredFields()) {
                    if (!java.util.Collection.class.isAssignableFrom(f.getType())
                            && !java.util.Map.class.isAssignableFrom(f.getType())) continue;
                    f.setAccessible(true);
                    Object val = f.get(selector);
                    if (val instanceof java.util.Collection) ((java.util.Collection<?>) val).clear();
                    else if (val instanceof java.util.Map) ((java.util.Map<?, ?>) val).clear();
                }
            }
        } catch (Throwable t) {
            org.bukkit.Bukkit.getLogger().warning("[MonsterMaze] clearVanillaGoals failed: " + t);
        }
    }

    /**
     * Modern Monster Maze ghost: the real vanilla entity type is sent to the client, but its
     * autonomous behaviour is stripped so Monster Maze remains authoritative over movement.
     */
    public static LivingEntity spawnGhostMob(Location loc, String mobType) {
        if (loc == null || loc.getWorld() == null) return null;
        EntityType type = resolveMobType(mobType);
        if (type == null || !type.isAlive()) {
            org.bukkit.Bukkit.getLogger().warning("[MonsterMaze] Unsupported mob type '" + mobType + "'");
            return null;
        }
        try {
            Entity entity = loc.getWorld().spawnEntity(loc, type);
            if (!(entity instanceof LivingEntity)) {
                entity.remove();
                return null;
            }
            LivingEntity living = (LivingEntity) entity;
            living.setRemoveWhenFarAway(false);
            living.setCanPickupItems(false);
            living.setCollidable(false);
            vegetate(living);
            return living;
        } catch (Throwable t) {
            org.bukkit.Bukkit.getLogger().warning("[MonsterMaze] spawnGhostMob '" + mobType + "' failed: " + t);
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

    /** Retained only for older 1.21 callers that explicitly request the Snow Golem renderer. */
    public static org.bukkit.entity.Snowman spawnGhostSnowman(Location loc) {
        LivingEntity ent = spawnGhostMob(loc, "snowman");
        return ent instanceof org.bukkit.entity.Snowman ? (org.bukkit.entity.Snowman) ent : null;
    }
}
