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
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Spawns labeled villagers in the pre-game spawn cage for kit selection.
 * Right-click opens the kit GUI (or selects that kit directly).
 */
public class KitSelectorNPCs implements Listener {

    private final MonsterMazePlugin plugin;
    private final GameManager game;
    private final KitGUI gui;
    private final KitManager kits;
    private final List<LivingEntity> npcs = new ArrayList<LivingEntity>();
    /** Spawn anchor per NPC so we can pin them in place. */
    private final Map<LivingEntity, Location> anchors = new HashMap<LivingEntity, Location>();

    public KitSelectorNPCs(MonsterMazePlugin plugin, GameManager game, KitManager kits, KitGUI gui) {
        this.plugin = plugin;
        this.game = game;
        this.kits = kits;
        this.gui = gui;
        Bukkit.getPluginManager().registerEvents(this, plugin);

        // Pin every kit-selector villager to its spawn anchor every 0.5s (2Hz) to stop player
        // pushing. 20Hz was overkill — a pushed villager barely drifts between pins, and this
        // was a permanent always-running task (PERF).
        Bukkit.getScheduler().runTaskTimer(plugin, new Runnable() {
            @Override
            public void run() {
                if (game.getState() != GameState.IDLE && game.getState() != GameState.STARTING) return;
                for (LivingEntity e : npcs) {
                    if (e == null || !e.isValid()) continue;
                    e.setVelocity(new org.bukkit.util.Vector(0, 0, 0));
                    Location anchor = anchors.get(e);
                    if (anchor == null) continue;
                    if (e.getLocation().distanceSquared(anchor) > 0.0001) {
                        e.teleport(anchor);
                    }
                }
            }
        }, 10L, 10L);
    }

    public void spawnAt(Location center) {
        clearNearby(center);

        java.util.List<KitType> available = java.util.Arrays.asList(KitType.values());
        int n = available.size();
        for (int i = 0; i < n; i++) {
            KitType type = available.get(i);
            double angle = (2 * Math.PI * i) / n - Math.PI / 2;
            double dx = Math.cos(angle) * 2.5;
            double dz = Math.sin(angle) * 2.5;

            Location loc = center.clone().add(dx, 0, dz);
            loc.setY(center.getY());

            // Face toward center
            Location look = center.clone();
            loc.setDirection(look.toVector().subtract(loc.toVector()));

            Villager v = (Villager) loc.getWorld().spawnEntity(loc, EntityType.VILLAGER);
            v.setCustomName(type.display + ChatColor.GRAY + " [Click]");
            v.setCustomNameVisible(true);
            v.setProfession(Villager.Profession.LIBRARIAN);
            v.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, Integer.MAX_VALUE, 255, false, false), true);

            // Disable vanilla AI so the villager never wanders (Paper API).
            v.setAI(false);
            v.setAdult();
            v.setRemoveWhenFarAway(false);
            v.setCanPickupItems(false);

            anchors.put(v, loc.clone());
            npcs.add(v);
        }
    }

    private void clearNearby(Location center) {
        clear();
        if (center == null || center.getWorld() == null) return;
        for (Entity e : center.getWorld().getEntities()) {
            if (!(e instanceof Villager)) continue;
            String name = e.getCustomName();
            if (name == null) continue;
            if (!name.contains(" [Click]") && kitFromName(name) == null) continue;
            if (!e.getLocation().getWorld().equals(center.getWorld())) continue;
            double d = e.getLocation().distanceSquared(center);
            if (d > 20 * 20) continue;
            e.remove();
        }
    }

    public void clear() {
        for (LivingEntity e : npcs) {
            if (e == null) continue;
            try {
                e.remove();
            } catch (Throwable ignored) {
            }
        }
        npcs.clear();
        anchors.clear();
    }

    @EventHandler
    public void onInteract(PlayerInteractEntityEvent event) {
        Entity ent = event.getRightClicked();
        if (!(ent instanceof Villager)) return;
        if (!npcs.contains(ent)) return;

        event.setCancelled(true);
        Player player = event.getPlayer();

        if (game.getState() == GameState.LIVE) {
            player.sendMessage(ChatColor.RED + "Can't change kit mid-game.");
            return;
        }

        KitType type = kitFromName(ent.getCustomName());
        if (type != null) {
            kits.setKit(player, type);
            player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1.2f);
            gui.open(player);
        } else {
            gui.open(player);
        }
    }

    @EventHandler
    public void onDamage(EntityDamageEvent event) {
        if (npcs.contains(event.getEntity())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onDamageBy(EntityDamageByEntityEvent event) {
        if (npcs.contains(event.getEntity())) {
            event.setCancelled(true);
            if (event.getDamager() instanceof Player) {
                Player player = (Player) event.getDamager();
                if (game.getState() == GameState.LIVE) return;
                KitType type = kitFromName(event.getEntity().getCustomName());
                if (type != null) {
                    kits.setKit(player, type);
                    player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1.2f);
                    gui.open(player);
                }
            }
        }
    }

    private KitType kitFromName(String name) {
        if (name == null) return null;
        String strip = ChatColor.stripColor(name).replace(" [Click]", "").trim();
        for (KitType k : KitType.values()) {
            if (ChatColor.stripColor(k.display).equalsIgnoreCase(strip)) return k;
            if (k.id.equalsIgnoreCase(strip)) return k;
        }
        return null;
    }
}