package me.monstermaze.entity;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.ProtocolManager;
import com.comphenix.protocol.events.ListenerPriority;
import com.comphenix.protocol.events.PacketAdapter;
import com.comphenix.protocol.events.PacketEvent;
import com.comphenix.protocol.wrappers.WrappedDataValue;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Rewrites the client-facing entity type of Monster Maze's Snow Golem-backed mobs.
 *
 * <p>The server continues to simulate one physical entity type (Snow Golem), just like the
 * original 1.8 ghost-mob implementation. Only the outgoing packets are changed, so the
 * client renders the requested mob while the server never runs that mob's native AI.</p>
 */
public final class MonsterDisguiseListener {
    /** Snow Golem's subclass-specific synced-data field: pumpkin/head state. */
    private static final int SNOW_GOLEM_METADATA_INDEX = 16;

    private final ProtocolManager protocolManager;
    private final PacketAdapter adapter;

    public MonsterDisguiseListener() {
        protocolManager = ProtocolLibrary.getProtocolManager();
        adapter = new PacketAdapter(
                ListenerPriority.NORMAL,
                PacketType.Play.Server.SPAWN_ENTITY,
                PacketType.Play.Server.ENTITY_METADATA) {
            @Override
            public void onPacketSending(PacketEvent event) {
                if (event.getPacketType() == PacketType.Play.Server.SPAWN_ENTITY) {
                    rewriteSpawn(event);
                } else if (event.getPacketType() == PacketType.Play.Server.ENTITY_METADATA) {
                    sanitizeMetadata(event);
                }
            }
        };
        protocolManager.addPacketListener(adapter);
    }

    public void shutdown() {
        protocolManager.removePacketListener(adapter);
    }

    private void rewriteSpawn(PacketEvent event) {
        try {
            if (event.getPacket().getUUIDs().size() == 0) return;
            UUID uuid = event.getPacket().getUUIDs().read(0);
            String skin = MonsterEntityController.getSkin(uuid);
            if (skin == null) return;

            EntityType type = resolveClientType(skin);
            if (type == null) return;
            event.getPacket().getEntityTypeModifier().write(0, type);
        } catch (Throwable ignored) {
            // Protocol compatibility must never prevent a normal entity packet from being sent.
        }
    }

    /**
     * The physical server entity is a Snow Golem, so its subclass metadata can contain a
     * Snow-Golem-only field. Once the spawn type has been rewritten to another mob, that field
     * is no longer valid for the client-side entity and must not be leaked into the packet.
     */
    private void sanitizeMetadata(PacketEvent event) {
        try {
            Entity entity = event.getPacket().getEntityModifier(event).read(0);
            if (entity == null || MonsterEntityController.getSkin(entity.getUniqueId()) == null) return;

            List<WrappedDataValue> values = event.getPacket().getDataValueCollectionModifier().read(0);
            if (values == null || values.isEmpty()) return;

            List<WrappedDataValue> filtered = new ArrayList<WrappedDataValue>(values.size());
            for (WrappedDataValue value : values) {
                if (value.getIndex() == SNOW_GOLEM_METADATA_INDEX) continue;
                filtered.add(value);
            }

            if (filtered.size() != values.size()) {
                event.getPacket().getDataValueCollectionModifier().write(0, filtered);
            }
        } catch (Throwable ignored) {
            // Never break an entity metadata packet because of disguise compatibility.
        }
    }

    private EntityType resolveClientType(String skin) {
        String id = skin.trim().toLowerCase(Locale.ROOT);
        if (id.isEmpty() || "snowman".equals(id) || "snow_golem".equals(id)) return EntityType.SNOW_GOLEM;
        if ("eyeofender".equals(id) || "enderman".equals(id)) return EntityType.ENDERMAN;
        if ("zombified_piglin".equals(id) || "pig_zombie".equals(id) || "zombie_pigman".equals(id)) return EntityType.ZOMBIFIED_PIGLIN;
        try {
            return EntityType.valueOf(id.replace('-', '_').replace(' ', '_').toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }
}
