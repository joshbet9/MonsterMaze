package me.monstermaze.util;

import org.bukkit.Location;
import org.bukkit.entity.Creature;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Snowman;
import org.bukkit.event.entity.CreatureSpawnEvent;

/**
 * Paper 1.21 port of Mineplex UtilEnt.CreatureMoveFast.
 *
 * <p>Modern movement engine: vanilla {@link Mob#getPathfinder()} navigation. Hand-set velocity
 * glides fight these servers' ground physics (jitter/frozen/drifting), and the 1.8 NMS
 * ControllerMove is long gone — so we navigate the waypoint line instead. Speed is a
 * multiplier on the mob's base movement-speed attribute and is calibrated to match the 1.8
 * controller-move feel (see {@code MonsterManager}, passthrough ~0.8).
 *
 * <pre>
 * public static boolean CreatureMoveFast(Entity ent, Location target, float speed) {
 *     return CreatureMoveFast(ent, target, speed, true);
 * }
 * public static boolean CreatureMoveFast(Entity ent, Location target, float speed, boolean slow) {
 *     if (!(ent instanceof Mob)) return false;
 *     if (UtilMath.offsetSquared(ent.getLocation(), target) &lt; 0.01) return false;
 *     if (UtilMath.offsetSquared(ent.getLocation(), target) &lt; 4) speed = Math.min(speed, 1f);
 *     return ((Mob) ent).getPathfinder().moveTo(target, speed);
 * }
 * </pre>
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

    /** Safe-ground check standing on solid ground. */
    public static boolean isGrounded(Entity ent) {
        if (ent == null) return false;
        // Primary: Bukkit flag
        if (ent.isOnGround()) return true;
        // Fallback: block immediately below feet is solid
        try {
            org.bukkit.Location loc = ent.getLocation();
            org.bukkit.block.Block below = loc.getWorld().getBlockAt(
                    loc.getBlockX(), loc.getBlockY() - 1, loc.getBlockZ());
            return below.getType().isSolid();
        } catch (Throwable t) {
            return false;
        }
    }

    /**
     * Mimic Mineplex UtilEnt.vegetate(ent, true) on Paper: clear the mob's target and strip its
     * vanilla goal selectors (wander/stroll/look/attack) so it never drifts, pathfinds, or attacks
     * on its own. Movement afterwards is only driven by the CreatureMoveFast controller glide.
     *
     * <p>This mirrors the 1.8 build: it cleared {@code EntityCreature.goalSelector} and
     * {@code targetSelector} via reflection. Common targets stay TICKING (gravity, travel, this
     * glide's velocity) because AI itself is NOT disabled — {@code Mob#setAI(false)} would set the
     * NoAI brain flag which, on 1.21, also disables the entity's movement tick and would freeze
     * the monster in place.</p>
     */
    public static void vegetate(Entity ent) {
        if (ent == null) return;
        if (ent instanceof Creature) {
            try {
                ((Creature) ent).setTarget(null);
            } catch (Throwable ignored) {
            }
        }
        if (ent instanceof Mob) {
            clearVanillaGoals(ent);
        }
    }

    /**
     * Stop the mob's active vanilla navigation path (NMS {@code PathNavigation#stop} via
     * reflection). Used when Repulsor launches a monster: with the route still live, the
     * pathfinder keeps steering/stalling the mob in flight instead of letting it fly
     * ballistically. Paper's {@code Pathfinder} interface has no stop, so reach NMS.
     */
    public static void stopNavigation(Entity ent) {
        if (ent == null) return;
        try {
            Object handle = ent.getClass().getMethod("getHandle").invoke(ent);
            Object nav = handle.getClass().getMethod("getNavigation").invoke(handle);
            if (nav != null) {
                nav.getClass().getMethod("stop").invoke(nav);
            }
        } catch (Throwable t) {
            org.bukkit.Bukkit.getLogger().warning("[MonsterMaze] stopNavigation failed: " + t);
        }
    }

    /** Clear the NMS goal/target GoalSelectors (via reflection, like the 1.8 build). */
    private static void clearVanillaGoals(Entity ent) {
        try {
            for (String fieldName : new String[]{"goalSelector", "targetSelector"}) {
                java.lang.reflect.Field field = null;
                for (Class<?> c = ent.getClass(); c != null && c != Object.class; c = c.getSuperclass()) {
                    try {
                        field = c.getDeclaredField(fieldName);
                        break;
                    } catch (NoSuchFieldException ignore) {
                    }
                }
                if (field == null) continue;
                field.setAccessible(true);
                Object selector = field.get(ent);
                if (selector == null) continue;

                // Inside each GoalSelector, clear every Collection/Map of goals and flags
                // (availableGoals, lockedFlags, ...).
                for (java.lang.reflect.Field f : selector.getClass().getDeclaredFields()) {
                    if (!java.util.Collection.class.isAssignableFrom(f.getType())
                            && !java.util.Map.class.isAssignableFrom(f.getType())) continue;
                    f.setAccessible(true);
                    Object val;
                    try {
                        val = f.get(selector);
                    } catch (IllegalAccessException e) {
                        continue;
                    }
                    if (val instanceof java.util.Collection) {
                        ((java.util.Collection<?>) val).clear();
                    } else if (val instanceof java.util.Map) {
                        ((java.util.Map<?, ?>) val).clear();
                    }
                }
            }
        } catch (Throwable t) {
            org.bukkit.Bukkit.getLogger().warning("[MonsterMaze] clearVanillaGoals failed: " + t);
        }
    }

    /**
     * Spawn a maze monster: a normal Paper snowman with {@code setCollidable(false)} so it never
     * shoves or is shoved by other mobs (the 1.8 {@code AddonGhostSnowman} {@code ae()} override
     * behaviour); it still collides with blocks and obeys gravity.
     */
    public static Snowman spawnGhostSnowman(Location loc) {
        if (loc == null || loc.getWorld() == null) return null;
        try {
            Snowman ent = (Snowman) loc.getWorld().spawn(loc, Snowman.class,
                    CreatureSpawnEvent.SpawnReason.CUSTOM);
            if (ent == null) return null;
            ent.setCollidable(false);
            ent.setRemoveWhenFarAway(false);
            return ent;
        } catch (Throwable t) {
            org.bukkit.Bukkit.getLogger().warning("[MonsterMaze] spawnGhostSnowman failed: " + t.getMessage());
            return null;
        }
    }
}