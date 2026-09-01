package me.monstermaze.game;

import me.monstermaze.MonsterMazePlugin;
import me.monstermaze.world.VoidWorldManager;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.Snowman;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.EntityBlockFormEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityRegainHealthEvent;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerVelocityEvent;
import org.bukkit.event.weather.ThunderChangeEvent;
import org.bukkit.event.weather.WeatherChangeEvent;

/**
 * Keeps the arena clean: no snow trails, no random mobs, blocked block interactions.
 */
public class ArenaListener implements Listener {

    private final MonsterMazePlugin plugin;
    private final GameManager game;

    public ArenaListener(MonsterMazePlugin plugin, GameManager game) {
        this.plugin = plugin;
        this.game = game;
        org.bukkit.Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    private boolean inArena(Location loc) {
        Location c = game.getCenter();
        if (c == null || loc == null || loc.getWorld() != c.getWorld()) return false;
        if (game.getState() == GameState.IDLE) return false;
        int r = game.getMazeGenerator().getPlatformRadius() + 2;
        return Math.abs(loc.getBlockX() - c.getBlockX()) <= r
                && Math.abs(loc.getBlockZ() - c.getBlockZ()) <= r;
    }

    /** Prevent players from opening beacon UIs inside the arena during a game. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBeaconInteract(PlayerInteractEvent event) {
        if (game.getState() == GameState.IDLE) return;
        if (event.getAction() == Action.RIGHT_CLICK_BLOCK) {
            Block block = event.getClickedBlock();
            if (block != null && block.getType() == Material.BEACON) {
                if (inArena(block.getLocation())) {
                    event.setCancelled(true);
                }
            }
        }
    }

    /** Preserve Maverick kit directional pad velocity from being overridden. */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerVelocity(PlayerVelocityEvent event) {
        Player p = event.getPlayer();
        if (p.hasMetadata("maverick_launch")) {
            p.removeMetadata("maverick_launch", plugin);
        }
    }

    /** Snowmen forming snow layers. */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onSnowForm(EntityBlockFormEvent event) {
        if (event.getEntity() instanceof Snowman) {
            event.setCancelled(true);
        }
    }

    /** Players must not be able to attack the maze monsters — direct hits OR projectiles (snowballs). */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerHitMonster(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Snowman)) return;
        if (!inArena(event.getEntity().getLocation())) return;

        Entity damager = event.getDamager();
        if (damager instanceof Player) {
            event.setCancelled(true);
            return;
        }
        if (damager instanceof Projectile) {
            Projectile proj = (Projectile) damager;
            if (proj.getShooter() instanceof Player) {
                event.setCancelled(true);
            }
        }
    }

    /** Disable weather for the whole game. */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onWeather(WeatherChangeEvent event) {
        if (inVoidWorld(event.getWorld()) && event.toWeatherState()) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onThunder(ThunderChangeEvent event) {
        if (inVoidWorld(event.getWorld()) && event.toThunderState()) {
            event.setCancelled(true);
        }
    }

    /** PvP off: players can never damage each other during a game. */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerHitPlayer(EntityDamageByEntityEvent event) {
        if (game.getState() == GameState.IDLE) return;
        if (!(event.getEntity() instanceof Player)) return;
        if (event.getDamager() instanceof Player) {
            event.setCancelled(true);
            return;
        }
        Entity d = event.getDamager();
        if (d instanceof org.bukkit.entity.Projectile) {
            org.bukkit.entity.Projectile proj = (org.bukkit.entity.Projectile) d;
            if (proj.getShooter() instanceof Player) {
                event.setCancelled(true);
            }
        }
    }

    /** Play player hurt sound instead of default mob hit sound when attacked by a snowman. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMonsterAttackPlayer(EntityDamageByEntityEvent event) {
        if (game.getState() == GameState.IDLE) return;
        if (!(event.getEntity() instanceof Player)) return;
        Entity damager = event.getDamager();
        if (damager instanceof Snowman) {
            Player player = (Player) event.getEntity();
            player.getWorld().playSound(player.getLocation(), org.bukkit.Sound.HURT_FLESH, 1.0f, 1.0f);
        }
    }

    /** Regen off: no natural (or any) health regeneration during a game. */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onRegain(EntityRegainHealthEvent event) {
        if (game.getState() == GameState.IDLE) return;
        if (!(event.getEntity() instanceof Player)) return;
        event.setCancelled(true);
    }

    /** Extra safety for any entity->block change (snow/gravel etc). */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onChangeBlock(EntityChangeBlockEvent event) {
        Entity e = event.getEntity();
        if (e instanceof Snowman || inArena(event.getBlock().getLocation())) {
            if (e instanceof Snowman || e instanceof LivingEntity) {
                if (inArena(event.getBlock().getLocation())) {
                    event.setCancelled(true);
                }
            }
        }
    }

    /** Block natural/reinforcement spawns inside the arena; allow custom snowmen. */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onCreatureSpawn(CreatureSpawnEvent event) {
        if (!inArena(event.getLocation())) return;
        if (game.getState() == GameState.IDLE) return;
        if (event.getSpawnReason() == SpawnReason.CUSTOM) return;
        if ("MazeMonster".equals(event.getEntity().getCustomName())) return;
        event.setCancelled(true);
    }

    /** Lock hunger. */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onFood(FoodLevelChangeEvent event) {
        if (!(event.getEntity() instanceof Player)) return;
        if (!inVoidWorld(event.getEntity().getWorld())) return;
        event.setCancelled(true);
        Player p = (Player) event.getEntity();
        p.setFoodLevel(20);
        try { p.setSaturation(20f); } catch (Throwable ignored) { }
    }

    /** Keep players in the pre/post-game lobby safe. */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onLobbyDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player)) return;
        if (game.getState() != GameState.IDLE && game.getState() != GameState.ENDING) return;
        if (!inVoidWorld(event.getEntity().getWorld())) return;
        event.setCancelled(true);
        ((Player) event.getEntity()).setHealth(((Player) event.getEntity()).getMaxHealth());
        ((Player) event.getEntity()).setFoodLevel(20);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        if (isMapWorld(event.getPlayer().getWorld())) {
            event.setCancelled(true);
            return;
        }
        if (inVoidWorld(event.getPlayer().getWorld()) && game.getState() != GameState.IDLE) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        if (isMapWorld(event.getPlayer().getWorld())) {
            event.setCancelled(true);
            return;
        }
        if (inVoidWorld(event.getPlayer().getWorld()) && game.getState() != GameState.IDLE) {
            event.setCancelled(true);
        }
    }

    /** True when the world is the active arena map world. */
    private boolean isMapWorld(World w) {
        if (w == null) return false;
        try {
            World active = me.monstermaze.world.MapManager.class.cast(plugin.getMapManager()).ensureActiveWorld();
            return active != null && active == w;
        } catch (Throwable t) {
            return false;
        }
    }

    private boolean inVoidWorld(World w) {
        return w != null && VoidWorldManager.WORLD_NAME.equals(w.getName());
    }
}
