package me.monstermaze.entity;
import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.ProtocolManager;
import com.comphenix.protocol.events.ListenerPriority;
import com.comphenix.protocol.events.PacketAdapter;
import com.comphenix.protocol.events.PacketEvent;
import com.comphenix.protocol.wrappers.WrappedWatchableObject;
import me.monstermaze.MonsterMazePlugin;
import me.monstermaze.util.MobThemeManager;
import me.monstermaze.util.MobTypes;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

public final class MonsterDisguiseListener {
    private final ProtocolManager protocolManager;
    private final PacketAdapter adapter;
    private final MonsterMazePlugin plugin;
    public MonsterDisguiseListener(final MonsterMazePlugin plugin) {
        this.plugin=plugin;protocolManager=ProtocolLibrary.getProtocolManager();
        adapter=new PacketAdapter(plugin,ListenerPriority.NORMAL,PacketType.Play.Server.SPAWN_ENTITY_LIVING,PacketType.Play.Server.ENTITY_METADATA) {
            @Override public void onPacketSending(PacketEvent e) {
                if(e.getPacketType()==PacketType.Play.Server.SPAWN_ENTITY_LIVING)rewriteSpawn(e);
                else if(e.getPacketType()==PacketType.Play.Server.ENTITY_METADATA)sanitizeMetadata(e);
            }
        };protocolManager.addPacketListener(adapter);
    }
    public void shutdown(){protocolManager.removePacketListener(adapter);}
    public void refreshPlayer(Player p) {
        if(p==null||!p.isOnline())return;
        for(Entity e:p.getWorld().getEntities())if(e.hasMetadata(MobThemeManager.MOB_METADATA))try {
            // Resend the spawn packet so the viewer-specific entity type is rewritten.
            // Custom NMS ghost entities can be absent from the tracker query while still visible.
            protocolManager.updateEntity(e,Collections.singletonList(p));
        }catch(Throwable failure){
            plugin.getLogger().warning("[MonsterMaze] Could not refresh mob disguise for "+p.getName()+": "+failure.getClass().getSimpleName());
        }
    }
    private void rewriteSpawn(PacketEvent event) {
        try {
            Entity e=event.getPacket().getEntityModifier(event).read(0);
            if(e==null||!e.hasMetadata(MobThemeManager.MOB_METADATA))return;
            String id=plugin.getMobThemeManager()==null?"snowman":plugin.getMobThemeManager().forViewer(event.getPlayer(),e.getUniqueId());
            MobTypes.MobType mob=MobTypes.byId(id);if(mob==null||mob.entityId<0)return;
            EntityType type=EntityType.fromId(mob.entityId);if(type!=null)event.getPacket().getEntityTypeModifier().write(0,type);
        }catch(Throwable ignored){}
    }
    private void sanitizeMetadata(PacketEvent event) {
        try {
            Entity e=event.getPacket().getEntityModifier(event).read(0);
            if(e==null||!e.hasMetadata(MobThemeManager.MOB_METADATA))return;
            List<WrappedWatchableObject> values=event.getPacket().getWatchableCollectionModifier().read(0);
            if(values==null||values.isEmpty())return;
            List<WrappedWatchableObject> filtered=new ArrayList<WrappedWatchableObject>();
            for(WrappedWatchableObject v:values)if(v.getIndex()!=16)filtered.add(v);
            if(filtered.size()!=values.size())event.getPacket().getWatchableCollectionModifier().write(0,filtered);
        }catch(Throwable ignored){}
    }
}