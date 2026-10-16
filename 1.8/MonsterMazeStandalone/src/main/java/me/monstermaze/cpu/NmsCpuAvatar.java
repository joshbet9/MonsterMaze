package me.monstermaze.cpu;

import com.mojang.authlib.GameProfile;
import me.monstermaze.MonsterMazePlugin;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.craftbukkit.v1_8_R3.CraftServer;
import org.bukkit.craftbukkit.v1_8_R3.CraftWorld;

import net.minecraft.server.v1_8_R3.EntityPlayer;
import net.minecraft.server.v1_8_R3.EnumProtocolDirection;
import net.minecraft.server.v1_8_R3.MinecraftServer;
import net.minecraft.server.v1_8_R3.NetworkManager;
import net.minecraft.server.v1_8_R3.Packet;
import net.minecraft.server.v1_8_R3.PacketPlayOutEntityDestroy;
import net.minecraft.server.v1_8_R3.PacketPlayOutEntityHeadRotation;
import net.minecraft.server.v1_8_R3.PacketPlayOutNamedEntitySpawn;
import net.minecraft.server.v1_8_R3.PacketPlayOutPlayerInfo;
import net.minecraft.server.v1_8_R3.PlayerConnection;
import net.minecraft.server.v1_8_R3.PlayerInteractManager;
import net.minecraft.server.v1_8_R3.WorldServer;
import net.minecraft.server.v1_8_R3.WorldSettings;

import java.util.UUID;

/**
 * Minecraft 1.8 player-shaped CPU avatar.
 *
 * <p>The entity is a real NMS EntityPlayer with a Bukkit CraftPlayer wrapper.
 * It is added to the world/tracker but is intentionally not registered as an
 * online network player. A dummy player connection prevents NMS tracker code
 * from treating the missing client connection as an error.
 */
public final class NmsCpuAvatar implements CpuAvatar {
    public static final String METADATA_KEY = "monstermaze_cpu";

    private final MonsterMazePlugin plugin;
    private final String id;
    private final String name;
    private final UUID uuid;

    private NmsCpuPlayer handle;
    private Player bukkitPlayer;
    private boolean spawned;

    public NmsCpuAvatar(MonsterMazePlugin plugin, String id, String name, UUID uuid) {
        if (plugin == null) throw new IllegalArgumentException("plugin");
        if (id == null || id.trim().isEmpty()) throw new IllegalArgumentException("id");
        if (uuid == null) throw new IllegalArgumentException("uuid");

        this.plugin = plugin;
        this.id = id;
        this.name = safeName(name, id);
        this.uuid = uuid;
    }

    @Override
    public String id() {
        return id;
    }

    public UUID uniqueId() {
        return uuid;
    }

    @Override
    public Player player() {
        if (!spawned || bukkitPlayer == null) {
            throw new IllegalStateException("CPU avatar is not spawned: " + id);
        }
        return bukkitPlayer;
    }

    @Override
    public void spawn(Location location) {
        if (spawned) return;
        if (location == null || location.getWorld() == null) {
            throw new IllegalArgumentException("location/world");
        }

        MinecraftServer server = ((CraftServer) Bukkit.getServer()).getServer();
        WorldServer world = ((CraftWorld) location.getWorld()).getHandle();
        GameProfile profile = new GameProfile(uuid, name);
        PlayerInteractManager interactManager = new PlayerInteractManager(world);

        NmsCpuPlayer entity = new NmsCpuPlayer(server, world, profile, interactManager);
        entity.setLocation(location.getX(), location.getY(), location.getZ(),
                location.getYaw(), location.getPitch());

        /*
         * EntityTrackerEntry assumes a player has a non-null connection. The
         * dummy connection deliberately swallows outbound packets addressed to
         * the CPU itself. Real human clients receive the actual spawn packets
         * below.
         */
        entity.playerConnection = new SilentPlayerConnection(server, entity);

        if (!world.addEntity(entity)) {
            throw new IllegalStateException("Could not add CPU avatar to world: " + id);
        }

        /*
         * WorldServer maintains a dedicated player collection used by some
         * login/player-list logic. We want the avatar to be a tracked world
         * entity without masquerading as a real network login.
         */
        world.players.remove(entity);

        entity.getBukkitEntity().setMetadata(
                METADATA_KEY, new FixedMetadataValue(plugin, Boolean.TRUE));

        this.handle = entity;
        this.bukkitPlayer = (Player) entity.getBukkitEntity();
        this.spawned = true;

        broadcastSpawn(entity, location);
    }

    @Override
    public void apply(CpuAvatarAction action) {
        if (!spawned) return;
        if (action == null) action = CpuAvatarAction.idle();

        handle.yaw = normaliseYaw(handle.yaw + action.yawDelta);
        handle.aK = handle.yaw;
        handle.setSprinting(action.sprint);

        if (action.jump && handle.onGround) {
            /*
             * EntityLiving.bF() is the exact vanilla 1.8 jump path, including
             * the sprint jump impulse. NmsCpuPlayer exposes it without making
             * the AI layer NMS-aware.
             */
            handle.performJump();
        }

        /*
         * EntityHuman.g(strafe, forward) is the vanilla player movement path.
         * The CPU supplies input; Minecraft still resolves collisions, friction,
         * acceleration and gravity.
         */
        handle.g((float) action.strafe, (float) action.forward);
    }

    @Override
    public void teleport(Location location) {
        if (!spawned || location == null) return;
        handle.setLocation(location.getX(), location.getY(), location.getZ(),
                location.getYaw(), location.getPitch());

        for (Player observer : Bukkit.getOnlinePlayers()) {
            sendTeleport(observer, handle);
        }
    }

    @Override
    public void destroy() {
        if (!spawned) return;

        int entityId = handle.getId();
        for (Player observer : Bukkit.getOnlinePlayers()) {
            send(observer, new PacketPlayOutEntityDestroy(entityId));
        }

        if (handle.world != null) {
            ((WorldServer) handle.world).removeEntity(handle);
        }

        handle.dead = true;
        handle = null;
        bukkitPlayer = null;
        spawned = false;
    }

    @Override
    public boolean isSpawned() {
        return spawned && handle != null && !handle.dead;
    }

    private void broadcastSpawn(EntityPlayer entity, Location location) {
        PacketPlayOutPlayerInfo add =
                new PacketPlayOutPlayerInfo(
                        PacketPlayOutPlayerInfo.EnumPlayerInfoAction.ADD_PLAYER, entity);
        PacketPlayOutNamedEntitySpawn spawn = new PacketPlayOutNamedEntitySpawn(entity);
        PacketPlayOutEntityHeadRotation head =
                new PacketPlayOutEntityHeadRotation(entity,
                        (byte) ((location.getYaw() * 256.0f) / 360.0f));
        PacketPlayOutPlayerInfo remove =
                new PacketPlayOutPlayerInfo(
                        PacketPlayOutPlayerInfo.EnumPlayerInfoAction.REMOVE_PLAYER, entity);

        for (Player observer : Bukkit.getOnlinePlayers()) {
            PlayerConnection connection =
                    ((org.bukkit.craftbukkit.v1_8_R3.entity.CraftPlayer) observer)
                            .getHandle().playerConnection;
            if (connection == null) continue;
            connection.sendPacket(add);
            connection.sendPacket(spawn);
            connection.sendPacket(head);
            connection.sendPacket(remove);
        }
    }

    private void send(Player observer, Packet packet) {
        if (observer == null || !observer.isOnline()) return;
        PlayerConnection connection =
                ((org.bukkit.craftbukkit.v1_8_R3.entity.CraftPlayer) observer)
                        .getHandle().playerConnection;
        if (connection != null) connection.sendPacket(packet);
    }

    private void sendTeleport(Player observer, EntityPlayer entity) {
        send(observer, new net.minecraft.server.v1_8_R3.PacketPlayOutEntityTeleport(entity));
        send(observer, new PacketPlayOutEntityHeadRotation(entity,
                (byte) ((entity.yaw * 256.0f) / 360.0f)));
    }

    private static float normaliseYaw(float yaw) {
        while (yaw >= 180.0f) yaw -= 360.0f;
        while (yaw < -180.0f) yaw += 360.0f;
        return yaw;
    }

    private static String safeName(String requested, String fallback) {
        String source = requested == null || requested.trim().isEmpty() ? fallback : requested;
        source = source.replaceAll("[^A-Za-z0-9_]", "_");
        if (source.isEmpty()) source = "MazeBot";
        return source.length() > 16 ? source.substring(0, 16) : source;
    }

    /**
     * EntityPlayer subclass kept entirely inside the avatar adapter.
     */
    private static final class NmsCpuPlayer extends EntityPlayer {
        private NmsCpuPlayer(MinecraftServer server, WorldServer world,
                             GameProfile profile, PlayerInteractManager manager) {
            super(server, world, profile, manager);
        }

        private void performJump() {
            bF();
        }
    }

    /**
     * Connection required by NMS entity-tracking/player code but intentionally
     * disconnected from any real client.
     */
    private static final class SilentPlayerConnection extends PlayerConnection {
        private SilentPlayerConnection(MinecraftServer server, EntityPlayer player) {
            super(server, new NetworkManager(EnumProtocolDirection.CLIENTBOUND), player);
        }

        @Override
        public void sendPacket(Packet packet) {
            // The CPU has no client. All player-facing packets go to real observers.
        }
    }
}
