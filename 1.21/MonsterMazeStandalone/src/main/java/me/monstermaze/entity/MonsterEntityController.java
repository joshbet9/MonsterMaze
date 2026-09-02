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

/** Central Monster Maze entity normalisation. */
public final class MonsterEntityController {
    /** 1.8 EntitySnowman gameplay width. */
    public static final double HITBOX_WIDTH = 0.7D;
    /** 1.8 EntitySnowman gameplay height. */
    public static final double HITBOX_HEIGHT = 1.9D;
    /** 1.8 EntitySnowman movement attribute. */
    public static final double MOVEMENT_SPEED = 0.2D;

    private static boolean resolved;
    private static Method getHandle;
    private static Constructor<?> aabbConstructor;
    private static Method setBoundingBox;

    private MonsterEntityController() { }

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
            try { zombie.setShouldBurnInDay(false); } catch (Throwable ignored) { }
        }

        if (entity instanceof Slime) ((Slime) entity).setSize(1);

        setMovementSpeed(entity);
        normalizeHitbox(entity);
    }

    public static void tick(LivingEntity entity) {
        if (entity == null || !entity.isValid()) return;
        entity.setFireTicks(0);
        entity.setVisualFire(false);
        if (entity instanceof Zombie) {
            Zombie zombie = (Zombie) entity;
            zombie.setAdult();
            try { zombie.setShouldBurnInDay(false); } catch (Throwable ignored) { }
        }
        setMovementSpeed(entity);
        normalizeHitbox(entity);
    }

    private static void setMovementSpeed(LivingEntity entity) {
        try {
            AttributeInstance movement = entity.getAttribute(Attribute.MOVEMENT_SPEED);
            if (movement != null) movement.setBaseValue(MOVEMENT_SPEED);
        } catch (Throwable ignored) { }
    }

    /** Force the NMS bounding box to the 1.8 Snowman dimensions. */
    public static void normalizeHitbox(LivingEntity entity) {
        if (entity == null || entity.getWorld() == null) return;
        resolveReflection(entity);
        if (getHandle == null || aabbConstructor == null || setBoundingBox == null) return;
        try {
            Object handle = getHandle.invoke(entity);
            Location loc = entity.getLocation();
            Object box = aabbConstructor.newInstance(
                    loc.getX() - HITBOX_WIDTH / 2.0D, loc.getY(), loc.getZ() - HITBOX_WIDTH / 2.0D,
                    loc.getX() + HITBOX_WIDTH / 2.0D, loc.getY() + HITBOX_HEIGHT, loc.getZ() + HITBOX_WIDTH / 2.0D);
            setBoundingBox.invoke(handle, box);
        } catch (Throwable ignored) { }
    }

    private static void resolveReflection(LivingEntity entity) {
        if (resolved) return;
        resolved = true;
        try {
            Class<?> craftEntity = Class.forName("org.bukkit.craftbukkit.entity.CraftEntity");
            getHandle = craftEntity.getMethod("getHandle");
            Class<?> aabb = Class.forName("net.minecraft.world.phys.AABB");
            aabbConstructor = aabb.getConstructor(double.class, double.class, double.class, double.class, double.class, double.class);
            Class<?> nmsEntity = Class.forName("net.minecraft.world.entity.Entity");
            setBoundingBox = nmsEntity.getMethod("setBoundingBox", aabb);
        } catch (Throwable first) {
            try {
                String craftVersion = Bukkit.getServer().getClass().getPackage().getName();
                Class<?> craftEntity = Class.forName(craftVersion + ".entity.CraftEntity");
                getHandle = craftEntity.getMethod("getHandle");
                Class<?> aabb = Class.forName("net.minecraft.world.phys.AABB");
                aabbConstructor = aabb.getConstructor(double.class, double.class, double.class, double.class, double.class, double.class);
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
                location.getX() - HITBOX_WIDTH / 2.0D, location.getY(), location.getZ() - HITBOX_WIDTH / 2.0D,
                location.getX() + HITBOX_WIDTH / 2.0D, location.getY() + HITBOX_HEIGHT, location.getZ() + HITBOX_WIDTH / 2.0D);
    }
}
