package me.monstermaze.entity;

import me.monstermaze.MonsterMazePlugin;
import me.monstermaze.game.GameManager;
import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityCombustEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityTargetEvent;
import org.bukkit.event.entity.EntityTeleportEvent;
import org.bukkit.scheduler.BukkitTask;

/** Keeps real 1.21 renderer mobs from leaking vanilla gameplay into Monster Maze. */
public final class MonsterEntityListener implements Listener {
    private final GameManager game;
    private final BukkitTask tickTask;

    public MonsterEntityListener(MonsterMazePlugin plugin, GameManager game) {
        this.game = game;
        Bukkit.getPluginManager().registerEvents(this, plugin);
        this.tickTask = Bukkit.getScheduler().runTaskTimer(plugin, new Runnable() {
            @Override public void run() {
                for (LivingEntity entity : game.getMonsterManager().getMonsters()) {
                    MonsterEntityController.tick(entity);
                }
            }
        }, 1L, 1L);
    }

    public void shutdown() {
        if (tickTask != null) tickTask.cancel();
    }

    @EventHandler
    public void onCombust(EntityCombustEvent event) {
        if (isMonster(event.getEntity())) event.setCancelled(true);
    }

    @EventHandler
    public void onTarget(EntityTargetEvent event) {
        if (isMonster(event.getEntity())) event.setCancelled(true);
    }

    /** Endermen must never use their vanilla random teleport behaviour. */
    @EventHandler
    public void onTeleport(EntityTeleportEvent event) {
        if (isMonster(event.getEntity())) event.setCancelled(true);
    }

    @EventHandler
    public void onDamage(EntityDamageEvent event) {
        if (!isMonster(event.getEntity())) return;
        switch (event.getCause()) {
            case ENTITY_ATTACK:
            case ENTITY_SWEEP_ATTACK:
                event.setCancelled(true);
                break;
            case FIRE:
            case FIRE_TICK:
            case MELTING:
            case DROWNING:
            case DRYOUT:
                event.setCancelled(true);
                break;
            default:
                break;
        }
    }

    private boolean isMonster(Entity entity) {
        if (!(entity instanceof LivingEntity)) return false;
        for (LivingEntity monster : game.getMonsterManager().getMonsters()) {
            if (monster == entity) return true;
        }
        return false;
    }
}
