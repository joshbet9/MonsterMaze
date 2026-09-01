package me.monstermaze.util;

import me.monstermaze.nms.AddonGhostOcelot;
import me.monstermaze.nms.AddonGhostPigZombie;
import me.monstermaze.nms.AddonGhostSnowman;
import me.monstermaze.nms.AddonGhostSquid;
import me.monstermaze.nms.AddonGhostVillager;
import me.monstermaze.nms.AddonGhostZombie;
import net.minecraft.server.v1_8_R3.EntityInsentient;
import net.minecraft.server.v1_8_R3.GenericAttributes;
import net.minecraft.server.v1_8_R3.World;
import net.minecraft.server.v1_8_R3.WorldServer;
import org.bukkit.Location;
import org.bukkit.craftbukkit.v1_8_R3.CraftWorld;
import org.bukkit.entity.Creature;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.entity.CreatureSpawnEvent;

import java.lang.reflect.Field;
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
        // Creature-gate-removed
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
            // a(double, double, double, double) â€” speed is double in NMS
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
        // Creature-gate-removed

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
     * Mineplex UtilEnt.isGrounded â€“ standing on solid ground (not just isOnGround edge cases).
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
        // Creature-gate-removed
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

    private static Map<Class, Integer> classToId;
    private static Map<Integer, Class> idToClass;
    private static Map<String, Class> nameToClass;
    private static Map<Class, String> classToName;
    private static Map<String, Integer> nameToId;
    private static boolean typeMapsResolved;

    /**
     * Locate the vanilla {@code EntityTypes} registry maps once by probing each private
     * {@code Map} field with the known vanilla {@code EntitySnowman} key/value pairs. This
     * is far more robust than matching obfuscated generic types and matches how the snowman
     * was originally registered.
     */
    private static void resolveEntityTypeMaps() {
        if (typeMapsResolved) return;
        typeMapsResolved = true;
        try {
            Class<?> entityTypes = Class.forName("net.minecraft.server.v1_8_R3.EntityTypes");
            Class<?> snowmanClass = Class.forName("net.minecraft.server.v1_8_R3.EntitySnowman");
            int snowmanId = 97;

            for (Field f : entityTypes.getDeclaredFields()) {
                if (!Map.class.isAssignableFrom(f.getType())) continue;
                f.setAccessible(true);
                Object v;
                try {
                    v = f.get(null);
                } catch (Exception e) {
                    continue;
                }
                if (!(v instanceof Map)) continue;
                Map m = (Map) v;
                try {
                    if (m.get(snowmanClass) instanceof Integer && classToId == null) classToId = (Map) m;
                    if (m.get(Integer.valueOf(snowmanId)) == snowmanClass && idToClass == null) idToClass = (Map) m;
                    if (m.get("SnowMan") == snowmanClass && nameToClass == null) nameToClass = (Map) m;
                    if (m.get(snowmanClass) instanceof String && classToName == null) classToName = (Map) m;
                    if (m.get("SnowMan") instanceof Integer && nameToId == null) nameToId = (Map) m;
                } catch (Exception ignored) {
                }
            }
        } catch (Throwable t) {
            org.bukkit.Bukkit.getLogger().warning("[MonsterMaze] resolveEntityTypeMaps failed: " + t);
        }
    }

    /** Remap one vanilla mob id/name pair to the given ghost class so clients render it. */
    public static void registerGhostType(Class<?> ghostClass, int id, String name) {
        resolveEntityTypeMaps();
        if (idToClass == null || classToId == null) return;
        try {
            idToClass.put(id, ghostClass);
            classToId.put(ghostClass, id);
            if (nameToClass != null) nameToClass.put(name, ghostClass);
            if (classToName != null) classToName.put(ghostClass, name);
            if (nameToId != null) nameToId.put(name, id);
            org.bukkit.Bukkit.getLogger().info("[MonsterMaze] ghost '" + name + "' mapped to entity type id " + id);
        } catch (Throwable t) {
            org.bukkit.Bukkit.getLogger().warning("[MonsterMaze] registerGhostType '" + name + "' failed: " + t);
        }
    }

    /** Register every ghosted maze-monster type so the client renders the correct skins. */
    public static void registerGhostTypes() {
        registerGhostType(AddonGhostSnowman.class, 97, "SnowMan");
        registerGhostType(AddonGhostZombie.class, 54, "Zombie");
        registerGhostType(AddonGhostSquid.class, 94, "Squid");
        registerGhostType(AddonGhostOcelot.class, 98, "Ozelot");
        registerGhostType(AddonGhostPigZombie.class, 57, "PigZombie");
        registerGhostType(AddonGhostVillager.class, 120, "Villager");
    }

    /**
     * Uniform movement-speed attribute for every maze mob, so ocelots/villagers/squid/etc.
     * move at the same pace as the baseline snowman regardless of their native (much higher)
     * base speed. {@code CreatureMoveFast} drives via the NMS ControllerMove, which multiplies
     * by this attribute, so normalising it here keeps all mob types behaving identically.
     */
    private static final double UNIFORM_MOB_SPEED = 0.2D;

    /**
     * Spawn a ghosted maze monster of the given configured mob type. The ghost subclass
     * {@code ae()} returns false, so it never shoves or is shoved by other mobs, while it
     * still obeys blocks, gravity and the waypoint {@link #CreatureMoveFast} driving. All
     * mob types use identical logic â€” the configured type is purely a skin.
     */
    public static LivingEntity spawnGhostMob(Location loc, String mobType) {
        if (loc == null || loc.getWorld() == null) return null;
        Class<? extends EntityInsentient> ghost = ghostClassFor(mobType);
        if (ghost == null) return null;
        try {
            WorldServer nmsWorld = ((CraftWorld) loc.getWorld()).getHandle();
            EntityInsentient handle = ghost.getConstructor(World.class).newInstance(nmsWorld);
            handle.setPositionRotation(loc.getX(), loc.getY(), loc.getZ(), loc.getYaw(), loc.getPitch());
            // Uniform movement speed so all mob skins traverse the maze at the same pace.
            try {
                handle.getAttributeInstance(GenericAttributes.MOVEMENT_SPEED).setValue(UNIFORM_MOB_SPEED);
            } catch (Throwable ignored) {
            }
            // Spawn through the CUSTOM pipeline so onCreatureSpawn allows it.
            if (!nmsWorld.addEntity(handle, CreatureSpawnEvent.SpawnReason.CUSTOM)) {
                return null;
            }
            LivingEntity ent = (LivingEntity) handle.getBukkitEntity();
            ent.setRemoveWhenFarAway(false);
            // Prevent daylight-burning mobs (e.g. zombies) from igniting at round start.
            ent.addPotionEffect(new org.bukkit.potion.PotionEffect(
                    org.bukkit.potion.PotionEffectType.FIRE_RESISTANCE,
                    Integer.MAX_VALUE, 0, false, false));
            return ent;
        } catch (Throwable t) {
            org.bukkit.Bukkit.getLogger().warning("[MonsterMaze] spawnGhostMob '" + mobType + "' failed: " + t.getMessage());
            return null;
        }
    }

    /** Map a configured mob name to its ghost NMS class. Unknown values fall back to snowman. */
    private static Class<? extends EntityInsentient> ghostClassFor(String mobType) {
        if (mobType == null) mobType = "";
        String t = mobType.trim().toLowerCase().replace(" ", "_");
        if (t.equals("zombie")) return AddonGhostZombie.class;
        if (t.equals("squid")) return AddonGhostSquid.class;
        if (t.equals("ocelot") || t.equals("cat")) return AddonGhostOcelot.class;
        if (t.equals("zombie_pigman") || t.equals("pigman") || t.equals("pig_zombie")) {
            return AddonGhostPigZombie.class;
        }
        if (t.equals("villager")) return AddonGhostVillager.class;
        return AddonGhostSnowman.class;
    }
}
