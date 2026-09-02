package me.monstermaze.entity;

import me.monstermaze.MonsterMazePlugin;
import me.monstermaze.game.GameManager;
import me.monstermaze.game.GameState;
import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityCombustEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityTargetEvent;
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

    @EventHandler
    public void onDamage(EntityDamageEvent event) {
        if (!isMonster(event.getEntity())) return;
        switch (event.getCause()) {
            case ENTITY_ATTACK:
            case ENTITY_SWEEP_ATTACK:
                // Monster Maze ghosts were not player-damageable. Kits use their own events.
                event.setCancelled(true);
                break;
            case FIRE:
            case FIRE_TICK:
            case MELTING:
            case DROWNING:
            case DRYOUT:
                // Environmental survival is not part of Monster Maze mob behaviour.
                event.setCancelled(true);
                break;
            default:
                break;
        }
    }

    private boolean isMonster(Entity entity) {
        return entity instanceof LivingEntity && game.getMonsterManager().isMonster((LivingEntity) entity);
    }
}
