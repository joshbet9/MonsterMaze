package me.monstermaze.kit;

import me.monstermaze.MonsterMazePlugin;
import me.monstermaze.game.GameManager;
import me.monstermaze.game.GameState;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** One lobby villager which opens the complete Monster Maze selector. */
public class KitSelectorNPCs implements Listener {
    private static final String NPC_NAME = ChatColor.GREEN + "Monster Maze";
    public static final String NPC_METADATA = "monstermaze_npc";

    private final MonsterMazePlugin plugin;
    private final GameManager game;
    private final KitGUI gui;
    private final List<LivingEntity> npcs = new ArrayList<LivingEntity>();
    private final Map<LivingEntity, Location> anchors = new HashMap<LivingEntity, Location>();

    public KitSelectorNPCs(MonsterMazePlugin plugin, GameManager game, KitManager kits, KitGUI existingGui) {
        this.plugin = plugin;
        this.game = game;
        this.gui = existingGui;
        Bukkit.getPluginManager().registerEvents(this, plugin);

        Bukkit.getScheduler().runTaskTimer(plugin, new Runnable() {
            @Override public void run() {
                if (game.getState() != GameState.IDLE && game.getState() != GameState.STARTING) return;
                for (LivingEntity e : npcs) {
                    if (e == null || !e.isValid()) continue;
                    Location anchor = anchors.get(e);
                    e.setVelocity(new Vector(0, 0, 0));
                    if (anchor != null && e.getLocation().distanceSquared(anchor) > 0.0001) e.teleport(anchor);
                }
            }
        }, 10L, 10L);
    }

    public void spawnAt(Location center) {
        clearNearby(center);
        if (center == null || center.getWorld() == null) return;

        Location loc = center.clone();
        loc.setY(center.getY());
        Villager v = (Villager) loc.getWorld().spawnEntity(loc, EntityType.VILLAGER);
        v.setMetadata(NPC_METADATA, new FixedMetadataValue(plugin, true));
        v.setCustomName(NPC_NAME);
        v.setCustomNameVisible(true);
        v.setProfession(Villager.Profession.LIBRARIAN);
        v.setAdult();
        v.setAI(false);
        v.setInvulnerable(true);
        v.setRemoveWhenFarAway(false);
        v.setCanPickupItems(false);
        v.setCollidable(false);

        npcs.add(v);
        anchors.put(v, loc.clone());
    }

    private void clearNearby(Location center) {
        clear();
        if (center == null || center.getWorld() == null) return;
        for (Entity e : new ArrayList<Entity>(center.getWorld().getEntities())) {
            if (e instanceof Villager && (NPC_NAME.equals(e.getCustomName()) || e.hasMetadata(NPC_METADATA))) e.remove();
        }
    }

    public void clear() {
        for (LivingEntity e : npcs) if (e != null) {
            try { e.remove(); } catch (Throwable ignored) { }
        }
        npcs.clear();
        anchors.clear();
    }

    private boolean isNpc(Entity entity) {
        return entity != null && (npcs.contains(entity) || entity.hasMetadata(NPC_METADATA)
                || (entity instanceof Villager && NPC_NAME.equals(entity.getCustomName())));
    }

    @EventHandler
    public void onInteract(PlayerInteractEntityEvent event) {
        if (!isNpc(event.getRightClicked())) return;
        event.setCancelled(true);
        Player player = event.getPlayer();
        if (game.getState() == GameState.LIVE) {
            player.sendMessage(ChatColor.RED + "The game is already running.");
            return;
        }
        gui.open(player);
    }

    @EventHandler
    public void onDamage(EntityDamageEvent event) {
        if (isNpc(event.getEntity())) event.setCancelled(true);
    }

    @EventHandler
    public void onDamageBy(EntityDamageByEntityEvent event) {
        if (isNpc(event.getEntity())) event.setCancelled(true);
    }
}
