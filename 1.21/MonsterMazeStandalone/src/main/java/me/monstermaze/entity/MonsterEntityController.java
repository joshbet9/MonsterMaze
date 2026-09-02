package me.monstermaze.entity;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Slime;
import org.bukkit.entity.Zombie;
import org.bukkit.util.BoundingBox;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;

/**
 * Central Monster Maze entity normalisation.
 *
 * <p>The 1.8 implementation used a Snowman-backed ghost, so every disguised monster inherited
 * the same Snowman gameplay dimensions. 1.21 uses real entity types for the client renderer, so
 * their native dimensions/attributes must be normalised back to the Monster Maze ghost contract.</p>
 */
public final class MonsterEntityController {
    /** 1.8 EntitySnowman gameplay width. */
    public static final double HITBOX_WIDTH = 0.7D;
    /** 1.8 EntitySnowman gameplay height. */
    public static final double HITBOX_HEIGHT = 1.9D;
    /** Common movement attribute used by the Monster Maze controller. */
    public static final double MOVEMENT_SPEED = 0.1D;

    private static boolean resolved;
    private static Method getHandle;
    private static Constructor<?> aabbConstructor;
    private static Method setBoundingBox;

    private MonsterEntityController() { }

    /** Apply all Monster Maze invariants to a newly spawned renderer entity. */
    public static void configure(LivingEntity entity) {
        if (entity == null) return;

        entity.setRemoveWhenFarAway(false);
        entity.setCanPickupItems(false);
        entity.setCollidable(false);
        entity.setSilent(true);
        entity.setVisualFire(false);
        entity.setFireTicks(0);
        entity.setFallDistance(0);

        if (entity instanceof Mob) {
            Mob mob = (Mob) entity;
            mob.setAI(true);
            mob.setAware(true);
        }

        if (entity instanceof Zombie) {
            Zombie zombie = (Zombie) entity;
            zombie.setAdult();
            zombie.setConversionTime(-1);
        }

        if (entity instanceof Slime) {
            ((Slime) entity).setSize(1);
        }

        AttributeInstance movement = null;
        try {
            movement = entity.getAttribute(Attribute.MOVEMENT_SPEED);
        } catch (Throwable ignored) { }
        if (movement != null) {
            try { movement.setBaseValue(MOVEMENT_SPEED); } catch (Throwable ignored) { }
        }

        normalizeHitbox(entity);
    }

    /** Re-apply invariants that vanilla entity ticking may try to undo. */
    public static void tick(LivingEntity entity) {
        if (entity == null || !entity.isValid()) return;
        entity.setFireTicks(0);
        entity.setVisualFire(false);
        if (entity instanceof Zombie) ((Zombie) entity).setAdult();
        normalizeHitbox(entity);
    }

    /**
     * Force the NMS bounding box to the 1.8 Snowman dimensions. Bukkit exposes the box for
     * inspection but not mutation, so this is isolated reflection against the stable Mojang-
     * mapped 1.21 server classes. If a future server changes it, gameplay still falls back to
     * the explicit Monster Maze proximity checks.
     */
    public static void normalizeHitbox(LivingEntity entity) {
        if (entity == null || entity.getWorld() == null) return;
        resolveReflection(entity);
        if (getHandle == null || aabbConstructor == null || setBoundingBox == null) return;

        try {
            Object handle = getHandle.invoke(entity);
            Location loc = entity.getLocation();
            Object box = aabbConstructor.newInstance(
                    loc.getX() - HITBOX_WIDTH / 2.0D,
                    loc.getY(),
                    loc.getZ() - HITBOX_WIDTH / 2.0D,
                    loc.getX() + HITBOX_WIDTH / 2.0D,
                    loc.getY() + HITBOX_HEIGHT,
                    loc.getZ() + HITBOX_WIDTH / 2.0D);
            setBoundingBox.invoke(handle, box);
        } catch (Throwable ignored) {
            // Do not spam the server log every tick if Paper changes its internals.
        }
    }

    private static void resolveReflection(LivingEntity entity) {
        if (resolved) return;
        resolved = true;
        try {
            Class<?> craftEntity = Class.forName("org.bukkit.craftbukkit.entity.CraftEntity");
            getHandle = craftEntity.getMethod("getHandle");
            Class<?> aabb = Class.forName("net.minecraft.world.phys.AABB");
            aabbConstructor = aabb.getConstructor(double.class, double.class, double.class,
                    double.class, double.class, double.class);
            Class<?> nmsEntity = Class.forName("net.minecraft.world.entity.Entity");
            setBoundingBox = nmsEntity.getMethod("setBoundingBox", aabb);
        } catch (Throwable first) {
            try {
                String craftVersion = Bukkit.getServer().getClass().getPackage().getName();
                Class<?> craftEntity = Class.forName(craftVersion + ".entity.CraftEntity");
                getHandle = craftEntity.getMethod("getHandle");
                Class<?> aabb = Class.forName("net.minecraft.world.phys.AABB");
                aabbConstructor = aabb.getConstructor(double.class, double.class, double.class,
                        double.class, double.class, double.class);
                Class<?> nmsEntity = Class.forName("net.minecraft.world.entity.Entity");
                setBoundingBox = nmsEntity.getMethod("setBoundingBox", aabb);
            } catch (Throwable ignored) {
                getHandle = null;
                aabbConstructor = null;
                setBoundingBox = null;
            }
        }
    }

    public static BoundingBox expectedBox(Location location) {
        if (location == null) return new BoundingBox();
        return new BoundingBox(
                location.getX() - HITBOX_WIDTH / 2.0D,
                location.getY(),
                location.getZ() - HITBOX_WIDTH / 2.0D,
                location.getX() + HITBOX_WIDTH / 2.0D,
                location.getY() + HITBOX_HEIGHT,
                location.getZ() + HITBOX_WIDTH / 2.0D);
    }
}
