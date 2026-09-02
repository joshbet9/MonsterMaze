package me.monstermaze.util;

import me.monstermaze.nms.AddonGhostSnowman;
import net.minecraft.server.v1_8_R3.EntityCreature;
import net.minecraft.server.v1_8_R3.EntityInsentient;
import net.minecraft.server.v1_8_R3.World;
import net.minecraft.server.v1_8_R3.WorldServer;
import net.minecraft.server.v1_8_R3.ControllerMove;
import org.bukkit.Location;
import org.bukkit.craftbukkit.v1_8_R3.CraftWorld;
import org.bukkit.craftbukkit.v1_8_R3.entity.CraftCreature;
import org.bukkit.entity.Creature;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Snowman;
import org.bukkit.event.entity.CreatureSpawnEvent;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;

/** Port of the Mineplex Monster Maze entity utilities for Spigot 1.8.8. */
public final class UtilEnt {
    private UtilEnt() {}

    private static String selectedGhostMobType = "snowman";

    /*
     * These are retained only as a compatibility fallback. Monster Maze mobs are
     * CraftCreature instances, so the hot movement path below uses direct NMS calls
     * and does not pay the reflection cost every tick.
     */
    private static Method getHandle;
    private static Method getControllerMove;
    private static Method controllerMoveA;
    private static boolean resolved;
    private static boolean available;

    public static void setSelectedGhostMobType(String mobType) {
        selectedGhostMobType = mobType == null || mobType.trim().isEmpty() ? "snowman" : mobType.trim().toLowerCase();
    }

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
            controllerMoveA = controllerMove.getMethod("a", double.class, double.class, double.class, double.class);
            available = true;
        } catch (Throwable t) {
            available = false;
            org.bukkit.Bukkit.getLogger().warning("[MonsterMaze] CreatureMoveFast NMS unavailable: " + t.getMessage());
        }
    }

    public static boolean CreatureMoveFast(Entity ent, Location target, float speed) { return CreatureMoveFast(ent, target, speed, true); }

    public static boolean CreatureMoveFast(Entity ent, Location target, float speed, boolean slow) {
        if (ent == null || target == null) return false;
        double distSq = offsetSquared(ent.getLocation(), target);
        if (distSq < 0.01) return false;
        if (distSq < 4) speed = Math.min(speed, 1f);

        /*
         * Hot path: Monster Maze mobs are CraftCreature instances backed by
         * EntityCreature. Calling the NMS methods directly avoids three reflective
         * Method.invoke calls for every mob on every server tick.
         */
        if (ent instanceof CraftCreature) {
            try {
                EntityCreature handle = ((CraftCreature) ent).getHandle();
                ControllerMove controller = handle.getControllerMove();
                controller.a(target.getX(), target.getY(), target.getZ(), (double) speed);
                return true;
            } catch (Throwable ignored) {
                // Fall through to the existing compatibility path.
            }
        }

        resolve();
        if (!available) {
            Location loc = ent.getLocation();
            org.bukkit.util.Vector dir = target.toVector().subtract(loc.toVector());
            if (dir.lengthSquared() < 1e-6) return false;
            dir.normalize().multiply(Math.min(speed * 0.2, dir.length()));
            Location next = loc.clone().add(dir);
            next.setYaw(loc.getYaw()); next.setPitch(loc.getPitch());
            ent.teleport(next);
            return true;
        }
        try {
            Object handle = getHandle.invoke(ent);
            Object controller = getControllerMove.invoke(handle);
            controllerMoveA.invoke(controller, target.getX(), target.getY(), target.getZ(), (double) speed);
            return true;
        } catch (Throwable t) { return false; }
    }

    public static double offsetSquared(Location a, Location b) {
        if (a == null || b == null || a.getWorld() != b.getWorld()) return Double.MAX_VALUE;
        double dx = a.getX() - b.getX(), dy = a.getY() - b.getY(), dz = a.getZ() - b.getZ();
        return dx * dx + dy * dy + dz * dz;
    }

    public static boolean isGrounded(Entity ent) {
        if (ent == null) return false;
        if (ent.isOnGround()) return true;
        try {
            Location loc = ent.getLocation();
            return loc.getWorld().getBlockAt(loc.getBlockX(), loc.getBlockY() - 1, loc.getBlockZ()).getType().isSolid();
        } catch (Throwable t) { return false; }
    }

    public static void vegetate(Entity ent) {
        if (ent == null) return;
        try { if (ent instanceof Creature) ((Creature) ent).setTarget(null); } catch (Throwable ignored) { }
        resolve();
        if (!available) return;
        try {
            Object handle = getHandle.invoke(ent);
            clearGoals(handle, "goalSelector"); clearGoals(handle, "targetSelector");
        } catch (Throwable t) { org.bukkit.Bukkit.getLogger().warning("[MonsterMaze] vegetate NMS failed: " + t.getMessage()); }
    }

    private static void clearGoals(Object entity, String selectorField) {
        Field field = null;
        for (Class<?> c = entity.getClass(); c != null; c = c.getSuperclass()) {
            try { field = c.getDeclaredField(selectorField); break; } catch (NoSuchFieldException ignored) { }
        }
        if (field == null) return;
        field.setAccessible(true);
        try {
            Object selector = field.get(entity);
            if (selector == null) return;
            for (Field f : selector.getClass().getDeclaredFields()) {
                if (!java.util.Collection.class.isAssignableFrom(f.getType())) continue;
                f.setAccessible(true);
                Object value = f.get(selector);
                if (value instanceof java.util.Collection) ((java.util.Collection) value).clear();
            }
        } catch (Throwable ignored) { }
    }

    private static Map<Class, Integer> classToId;
    private static Map<Class, String> classToName;
    private static boolean typeMapsResolved;

    private static void resolveEntityTypeMaps() {
        if (typeMapsResolved) return;
        typeMapsResolved = true;
        try {
            Class<?> entityTypes = Class.forName("net.minecraft.server.v1_8_R3.EntityTypes");
            Class<?> snowmanClass = Class.forName("net.minecraft.server.v1_8_R3.EntitySnowman");
            for (Field f : entityTypes.getDeclaredFields()) {
                if (!Map.class.isAssignableFrom(f.getType())) continue;
                f.setAccessible(true);
                Object value; try { value = f.get(null); } catch (Throwable ignored) { continue; }
                if (!(value instanceof Map)) continue;
                Map map = (Map) value;
                try {
                    if (map.get(snowmanClass) instanceof Integer && classToId == null) classToId = map;
                    if (map.get(snowmanClass) instanceof String && classToName == null) classToName = map;
                } catch (Throwable ignored) { }
            }
        } catch (Throwable t) { org.bukkit.Bukkit.getLogger().warning("[MonsterMaze] resolveEntityTypeMaps failed: " + t.getMessage()); }
    }

    public static void registerGhostType(String mobType) {
        resolveEntityTypeMaps();
        if (classToId == null) return;
        MobTypes.MobType mob = MobTypes.byId(mobType);
        if (mob == null) mob = MobTypes.byId("snowman");
        try {
            classToId.put(AddonGhostSnowman.class, mob.entityId);
            if (classToName != null) classToName.put(AddonGhostSnowman.class, mob.registryName);
        } catch (Throwable t) { org.bukkit.Bukkit.getLogger().warning("[MonsterMaze] registerGhostType '" + mob.id + "' failed: " + t); }
    }

    public static void registerGhostTypes() { registerGhostType("snowman"); }

    public static LivingEntity spawnGhostMob(Location loc, String mobType) {
        if (loc == null || loc.getWorld() == null) return null;
        try {
            registerGhostType(mobType);
            WorldServer nmsWorld = ((CraftWorld) loc.getWorld()).getHandle();
            EntityInsentient handle = AddonGhostSnowman.class.getConstructor(World.class).newInstance(nmsWorld);
            handle.setPositionRotation(loc.getX(), loc.getY(), loc.getZ(), loc.getYaw(), loc.getPitch());
            if (!nmsWorld.addEntity(handle, CreatureSpawnEvent.SpawnReason.CUSTOM)) return null;
            LivingEntity ent = (LivingEntity) handle.getBukkitEntity();
            ent.setRemoveWhenFarAway(false);
            return ent;
        } catch (Throwable t) {
            org.bukkit.Bukkit.getLogger().warning("[MonsterMaze] spawnGhostMob '" + mobType + "' failed: " + t.getMessage());
            return null;
        }
    }

    /** Backwards-compatible API used by the existing 1.8 MonsterManager. */
    public static Snowman spawnGhostSnowman(Location loc) {
        LivingEntity ent = spawnGhostMob(loc, selectedGhostMobType);
        return ent instanceof Snowman ? (Snowman) ent : null;
    }
}
