package me.monstermaze.entity;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.ProtocolManager;
import com.comphenix.protocol.events.ListenerPriority;
import com.comphenix.protocol.events.PacketAdapter;
import com.comphenix.protocol.events.PacketEvent;
import org.bukkit.entity.EntityType;

import java.util.Locale;
import java.util.UUID;

/**
 * Rewrites the client-facing entity type of Monster Maze's Snow Golem-backed mobs.
 *
 * <p>The server continues to simulate one physical entity type (Snow Golem), just like the
 * original 1.8 ghost-mob implementation. Only the outgoing spawn packet is changed, so the
 * client renders the requested mob while the server never runs that mob's native AI.</p>
 */
public final class MonsterDisguiseListener {
    private final ProtocolManager protocolManager;
    private final PacketAdapter adapter;

    public MonsterDisguiseListener() {
        protocolManager = ProtocolLibrary.getProtocolManager();
        adapter = new PacketAdapter(
                ListenerPriority.NORMAL,
                PacketType.Play.Server.SPAWN_ENTITY) {
            @Override
            public void onPacketSending(PacketEvent event) {
                rewriteSpawn(event);
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
