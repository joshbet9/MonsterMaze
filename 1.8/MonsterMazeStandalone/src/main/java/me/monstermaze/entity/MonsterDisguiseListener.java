package me.monstermaze.entity;
import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.ProtocolManager;
import com.comphenix.protocol.events.ListenerPriority;
import com.comphenix.protocol.events.PacketAdapter;
import com.comphenix.protocol.events.PacketEvent;
import com.comphenix.protocol.wrappers.WrappedWatchableObject;
import me.monstermaze.MonsterMazePlugin;
import me.monstermaze.util.MobThemeManager;
import me.monstermaze.util.MobTypes;
import me.monstermaze.util.UtilEnt;
import org.bukkit.Bukkit;
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
    public void refreshPlayer(final Player player) {
        if (player == null || !player.isOnline()) return;
        for (final Entity entity : UtilEnt.getGhostEntities(player.getWorld())) {
            if (!entity.hasMetadata(MobThemeManager.MOB_METADATA)) continue;
            try {
                // Remove the old client entity before respawning it with the new per-viewer type.
                PacketContainer destroy = protocolManager.createPacket(PacketType.Play.Server.ENTITY_DESTROY);
                destroy.getIntegerArrays().write(0, new int[] {entity.getEntityId()});
                protocolManager.sendServerPacket(player, destroy);
                Bukkit.getScheduler().runTaskLater(plugin, new Runnable() {
                    @Override public void run() {
                        if (!player.isOnline() || !player.getWorld().equals(entity.getWorld())
                                || !entity.hasMetadata(MobThemeManager.MOB_METADATA)) return;
                        try {
                            protocolManager.updateEntity(entity, Collections.singletonList(player));
                        } catch (Throwable failure) {
                            plugin.getLogger().warning("[MonsterMaze] Could not respawn mob disguise for "
                                    + player.getName() + ": " + failure.getClass().getSimpleName());
                        }
                    }
                }, 1L);
            } catch (Throwable failure) {
                plugin.getLogger().warning("[MonsterMaze] Could not refresh mob disguise for "
                        + player.getName() + ": " + failure.getClass().getSimpleName());
            }
        }
    }
    private Entity findEntityById(int entityId,Player viewer){
        if(viewer!=null&&viewer.getWorld()!=null)
            for(Entity candidate:viewer.getWorld().getEntities())if(candidate.getEntityId()==entityId)return candidate;
        return null;
    }
    private void rewriteSpawn(PacketEvent event) {
        try {
            int entityId=event.getPacket().getIntegers().read(0);
            Entity e=UtilEnt.getGhostEntity(entityId);
            if(e==null)e=findEntityById(entityId,event.getPlayer());
            if(e==null||!e.hasMetadata(MobThemeManager.MOB_METADATA))return;
            String id=plugin.getMobThemeManager()==null?"snowman":plugin.getMobThemeManager().forViewer(event.getPlayer(),e.getUniqueId());
            MobTypes.MobType mob=MobTypes.byId(id);if(mob==null||mob.entityId<0)return;
            EntityType type=EntityType.fromId(mob.entityId);if(type!=null)event.getPacket().getEntityTypeModifier().write(0,type);
        }catch(Throwable ignored){}
    }
    private void sanitizeMetadata(PacketEvent event) {
        try {
            int entityId=event.getPacket().getIntegers().read(0);
            Entity e=UtilEnt.getGhostEntity(entityId);
            if(e==null)e=findEntityById(entityId,event.getPlayer());
            if(e==null||!e.hasMetadata(MobThemeManager.MOB_METADATA))return;
            List<WrappedWatchableObject> values=event.getPacket().getWatchableCollectionModifier().read(0);
            if(values==null||values.isEmpty())return;
            List<WrappedWatchableObject> filtered=new ArrayList<WrappedWatchableObject>();
            for(WrappedWatchableObject v:values)if(v.getIndex()!=16)filtered.add(v);
            if(filtered.size()!=values.size())event.getPacket().getWatchableCollectionModifier().write(0,filtered);
        }catch(Throwable ignored){}
    }
}