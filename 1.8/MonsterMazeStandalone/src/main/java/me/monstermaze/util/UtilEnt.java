package me.monstermaze.util;

import me.monstermaze.nms.AddonGhostSnowman;
import net.minecraft.server.v1_8_R3.WorldServer;
import org.bukkit.Location;
import org.bukkit.craftbukkit.v1_8_R3.CraftWorld;
import org.bukkit.entity.Creature;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Snowman;
import org.bukkit.event.entity.CreatureSpawnEvent;

import java.lang.reflect.Method;
import java.util.Map;

/**
 * Port of Mineplex UtilEnt.CreatureMoveFast for Spigot 1.8.8 (NMS via reflection).
 *
 * <pre>
 * public static boolean CreatureMoveFast(Entity ent, Location target, float speed) {
 *     return CreatureMoveFast(ent, target, speed, true);
 * }
 * public static boolean CreatureMoveFast(Entity ent, Location target, float speed, boolean slow) {
 *     if (!(ent instanceof Creature)) return false;
 *     if (UtilMath.offsetSquared(ent.getLocation(), target) &lt; 0.01) return false;
 *     if (UtilMath.offsetSquared(ent.getLocation(), target) &lt; 4) speed = Math.min(speed, 1f);
 *     EntityCreature ec = ((CraftCreature)ent).getHandle();
 *     ec.getControllerMove().a(target.getX(), target.getY(), target.getZ(), speed);
 *     return true;
 * }
 * </pre>
 */
public final class UtilEnt {
    private UtilEnt() {}

    private static Method getHandle;
    private static Method getControllerMove;
    private static Method controllerMoveA;
    private static boolean resolved;
    private static boolean available;

    private static void resolve() {
        if (resolved) return;
        resolved = true;
        try {
            String ver = org.bukkit.Bukkit.getServer().getClass().getPackage().getName().split("\\.")[3];
            Class<?> craftCreature = Class.forName("org.bukkit.craftbukkit." + ver + ".entity.CraftCreature");
            getHandle = craftCreature.getMethod("getHandle");
            Class<?> entityCreature = Class.forName("net.minecraft.server." + ver + ".EntityCreature");
            getControllerMove = entityCreature.getMethod("getControllerMove");
            Class<?> controllerMove = Class.forName("net.minecraft.server." + ver + ".ControllerMove");
            // a(double, double, double, double) — speed is double in NMS
            controllerMoveA = controllerMove.getMethod("a", double.class, double.class, double.class, double.class);
            available = true;
        } catch (Throwable t) {
            available = false;
            org.bukkit.Bukkit.getLogger().warning("[MonsterMaze] CreatureMoveFast NMS unavailable: " + t.getMessage());
        }
    }

    public static boolean CreatureMoveFast(Entity ent, Location target, float speed) {
        return CreatureMoveFast(ent, target, speed, true);
    }

    public static boolean CreatureMoveFast(Entity ent, Location target, float speed, boolean slow) {
        if (!(ent instanceof Creature)) return false;

        double distSq = offsetSquared(ent.getLocation(), target);
        if (distSq < 0.01) return false;
        if (distSq < 4) speed = Math.min(speed, 1f);

        resolve();
        if (!available) {
            // Fallback: teleport-slide (should not be needed on 1.8.8 Spigot)
            Location loc = ent.getLocation();
            org.bukkit.util.Vector dir = target.toVector().subtract(loc.toVector());
            if (dir.lengthSquared() < 1e-6) return false;
            dir.normalize().multiply(Math.min(speed * 0.2, dir.length()));
            Location next = loc.clone().add(dir);
            next.setYaw(loc.getYaw());
            next.setPitch(loc.getPitch());
            ent.teleport(next);
            return true;
        }

        try {
            Object handle = getHandle.invoke(ent);
            Object controller = getControllerMove.invoke(handle);
            controllerMoveA.invoke(controller, target.getX(), target.getY(), target.getZ(), (double) speed);
            return true;
        } catch (Throwable t) {
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

    /** Clear pathfinding target / stop. Optional helper. */
    /**
     * Mineplex UtilEnt.isGrounded – standing on solid ground (not just isOnGround edge cases).
     */
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
     * Mimic Mineplex UtilEnt.vegetate(ent, true): fully disable the mob's vanilla AI
     * (pathfinder + target goals) so it cannot wander, drift diagonally, or walk onto
     * safe pads on its own. Movement afterwards is only driven by CreatureMoveFast.
     */
    public static void vegetate(Entity ent) {
        if (ent == null) return;
        if (ent instanceof Creature) {
            try {
                ((Creature) ent).setTarget(null);
            } catch (Throwable ignored) {
            }
        }

        resolve();
        if (!available) return;

        try {
            Object handle = getHandle.invoke(ent);
            clearGoals(handle, "goalSelector");
            clearGoals(handle, "targetSelector");
        } catch (Throwable t) {
            org.bukkit.Bukkit.getLogger().warning("[MonsterMaze] vegetate NMS failed: " + t.getMessage());
        }
    }

    /** Clear every collection-typed field of a PathfinderGoalSelector (removes all goals). */
    private static void clearGoals(Object entity, String selectorField) {
        java.lang.reflect.Field field = null;
        for (Class<?> c = entity.getClass(); c != null; c = c.getSuperclass()) {
            try {
                field = c.getDeclaredField(selectorField);
                break;
            } catch (NoSuchFieldException ignore) {
            }
        }
        if (field == null) return;
        field.setAccessible(true);
        Object selector;
        try {
            selector = field.get(entity);
        } catch (IllegalAccessException e) {
            return;
        }
        if (selector == null) return;

        for (java.lang.reflect.Field f : selector.getClass().getDeclaredFields()) {
            if (java.util.Collection.class.isAssignableFrom(f.getType())) {
                f.setAccessible(true);
                Object val;
                try {
                    val = f.get(selector);
                } catch (IllegalAccessException e) {
                    continue;
                }
                if (val instanceof java.util.Collection) {
                    ((java.util.Collection<?>) val).clear();
                }
            }
        }
    }

    /**
     * Register {@link AddonGhostSnowman} so the client renders it as a snowman.
     *
     * <p>{@code PacketPlayOutSpawnEntityLiving} sends {@code EntityTypes.a(entity.getClass())}
     * (class -&gt; id) as the type id. The client only knows vanilla numeric ids, so we map
     * {@code AddonGhostSnowman} back to the parent {@code Snowman} id (97). Maps are located by
     * probing each private {@code Map} field with the known vanilla "EntitySnowman" key/value,
     * which is far more robust than matching obfuscated generic types.</p>
     */
    @SuppressWarnings("unchecked")
    public static void registerGhostSnowmanEntityType() {
        try {
            Class<?> entityTypes = Class.forName("net.minecraft.server.v1_8_R3.EntityTypes");
            Class<?> snowmanClass = Class.forName("net.minecraft.server.v1_8_R3.EntitySnowman");
            int snowmanId = 97;

            Map<Class, Integer> classToId = null;
            Map<Integer, Class> idToClass = null;
            Map<String, Class> nameToClass = null;
            Map<Class, String> classToName = null;
            Map<String, Integer> nameToId = null;

            for (java.lang.reflect.Field f : entityTypes.getDeclaredFields()) {
                if (!java.util.Map.class.isAssignableFrom(f.getType())) continue;
                f.setAccessible(true);
                Object v;
                try {
                    v = f.get(null);
                } catch (Exception e) {
                    continue;
                }
                if (!(v instanceof java.util.Map)) continue;
                java.util.Map m = (java.util.Map) v;
                try {
                    if (m.get(snowmanClass) instanceof Integer && classToId == null)
                        classToId = (Map<Class, Integer>) m;
                    if (m.get(Integer.valueOf(snowmanId)) == snowmanClass && idToClass == null)
                        idToClass = (Map<Integer, Class>) m;
                    if (m.get("SnowMan") == snowmanClass && nameToClass == null)
                        nameToClass = (Map<String, Class>) m;
                    if (m.get(snowmanClass) instanceof String && classToName == null)
                        classToName = (Map<Class, String>) m;
                    if (m.get("SnowMan") instanceof Integer && nameToId == null)
                        nameToId = (Map<String, Integer>) m;
                } catch (Exception ignored) {
                }
            }

            if (idToClass != null) idToClass.put(snowmanId, (Class) AddonGhostSnowman.class);
            if (classToId != null) classToId.put((Class) AddonGhostSnowman.class, snowmanId);
            if (classToName != null) classToName.put((Class) AddonGhostSnowman.class, "Snowman");
            if (nameToClass != null) nameToClass.put("Snowman", (Class) AddonGhostSnowman.class);
            if (nameToId != null) nameToId.put("Snowman", snowmanId);

            org.bukkit.Bukkit.getLogger().info("[MonsterMaze] ghost snowman mapped to entity type id " + snowmanId);
        } catch (Throwable t) {
            org.bukkit.Bukkit.getLogger().warning("[MonsterMaze] registerGhostSnowmanEntityType failed: " + t);
        }
    }

    /**
     * Spawn a ghosted maze monster: {@link AddonGhostSnowman} (its {@code ae()} returns false,
     * so it never shoves or is shoved by other mobs) spawned through the proper CUSTOM pipeline
     * so it renders and is server-tracking like any vanilla mob.
     */
    public static Snowman spawnGhostSnowman(Location loc) {
        if (loc == null || loc.getWorld() == null) return null;
        try {
            WorldServer nmsWorld = ((CraftWorld) loc.getWorld()).getHandle();
            AddonGhostSnowman handle = new AddonGhostSnowman(nmsWorld);
            handle.setPositionRotation(loc.getX(), loc.getY(), loc.getZ(), loc.getYaw(), loc.getPitch());
            // Spawn through the CUSTOM pipeline so onCreatureSpawn allows it. In Spigot this
            // already registers the entity with the EntityTracker (so a spawn packet is sent);
            // do NOT call tracker.track() again afterwards or it throws "already tracked".
            if (!nmsWorld.addEntity(handle, CreatureSpawnEvent.SpawnReason.CUSTOM)) {
                org.bukkit.Bukkit.getLogger().warning("[MonsterMaze] spawnGhostSnowman: addEntity returned false");
                return null;
            }
            Snowman ent = (Snowman) handle.getBukkitEntity();
            ent.setRemoveWhenFarAway(false);
            return ent;
        } catch (Throwable t) {
            org.bukkit.Bukkit.getLogger().warning("[MonsterMaze] spawnGhostSnowman failed: " + t.getMessage());
            return null;
        }
    }
}
