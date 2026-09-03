package me.monstermaze.util;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/** Minecraft 1.8.8 living entity types that can be used as Monster Maze visual skins. */
public final class MobTypes {
    public static final class MobType {
        public final String id;
        public final String display;
        public final int entityId;
        public final String registryName;

        private MobType(String id, String display, int entityId, String registryName) {
            this.id = id;
            this.display = display;
            this.entityId = entityId;
            this.registryName = registryName;
        }
    }

    private static MobType m(String id, String display, int entityId, String registryName) {
        return new MobType(id, display, entityId, registryName);
    }

    /*
     * Playable visual skins. Deliberately excludes very large/special entities whose
     * client model would be wildly larger than the Snowman hitbox used by Monster Maze:
     * Giant, Ghast, Ender Dragon, Wither and Iron Golem.
     *
     * Endermen remain available despite being tall because their width/hitbox footprint
     * is close to the normal maze monster and Enderman is the default for Eye of Ender.
     */
    private static final List<MobType> ALL = Collections.unmodifiableList(Arrays.asList(
            m("creeper", "Creeper", 50, "Creeper"),
            m("skeleton", "Skeleton", 51, "Skeleton"),
            m("spider", "Spider", 52, "Spider"),
            m("zombie", "Zombie", 54, "Zombie"),
            m("slime", "Slime", 55, "Slime"),
            m("zombie_pigman", "Zombie Pigman", 57, "PigZombie"),
            m("enderman", "Enderman", 58, "Enderman"),
            m("cave_spider", "Cave Spider", 59, "CaveSpider"),
            m("silverfish", "Silverfish", 60, "Silverfish"),
            m("blaze", "Blaze", 61, "Blaze"),
            m("magma_cube", "Magma Cube", 62, "LavaSlime"),
            m("bat", "Bat", 65, "Bat"),
            m("witch", "Witch", 66, "Witch"),
            m("endermite", "Endermite", 67, "Endermite"),
            m("guardian", "Guardian", 68, "Guardian"),
            m("pig", "Pig", 90, "Pig"),
            m("sheep", "Sheep", 91, "Sheep"),
            m("cow", "Cow", 92, "Cow"),
            m("chicken", "Chicken", 93, "Chicken"),
            m("squid", "Squid", 94, "Squid"),
            m("wolf", "Wolf", 95, "Wolf"),
            m("mooshroom", "Mooshroom", 96, "MushroomCow"),
            m("snowman", "Snow Golem", 97, "SnowMan"),
            m("ocelot", "Ocelot", 98, "Ozelot"),
            m("horse", "Horse", 100, "EntityHorse"),
            m("rabbit", "Rabbit", 101, "Rabbit"),
            m("villager", "Villager", 120, "Villager"),
            m("random", "Random Mob", -1, "Random")
    ));

    private MobTypes() {}

    public static List<MobType> all() { return ALL; }

    public static MobType byId(String id) {
        if (id == null) return null;
        for (MobType mob : ALL) if (mob.id.equalsIgnoreCase(id)) return mob;
        return null;
    }
}
