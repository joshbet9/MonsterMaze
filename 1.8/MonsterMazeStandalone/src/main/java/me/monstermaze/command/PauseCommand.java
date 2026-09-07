package me.monstermaze.command;

import me.monstermaze.MonsterMazePlugin;
import me.monstermaze.game.GameManager;
import me.monstermaze.game.GameState;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerPickupItemEvent;
import org.bukkit.event.player.PlayerVelocityEvent;
import org.bukkit.event.player.PlayerToggleFlightEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.event.player.PlayerToggleSprintEvent;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.lang.reflect.Field;

/** Solo/admin game pause. */
public class PauseCommand implements CommandExecutor, Listener {
    private static final String ADMIN_PERMISSION = "monstermaze.admin";
    private static final int RESUME_COUNTDOWN_SECONDS = 3;

    private final MonsterMazePlugin plugin;
    private final GameManager game;
    private final Field stateField;
    private final Field liveStartField;
    private long pausedAtMs;
    private BukkitTask resumeTask;

    public PauseCommand(MonsterMazePlugin plugin) {
        this.plugin = plugin;
        this.game = plugin.getGameManager();
        try {
            stateField = GameManager.class.getDeclaredField("state");
            stateField.setAccessible(true);
            liveStartField = GameManager.class.getDeclaredField("liveStartMs");
            liveStartField.setAccessible(true);
        } catch (Exception e) {
            throw new IllegalStateException("Unable to initialise Monster Maze pause support", e);
        }
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!hasPermission(sender)) {
            sender.sendMessage(ChatColor.RED + "You cannot use /pause outside solo mode.");
            return true;
        }

        GameState state = game.getState();
        if (state == GameState.LIVE) {
            pause(sender);
        } else if (state == GameState.STARTING && pausedAtMs > 0L && resumeTask == null) {
            resume(sender);
        } else if (resumeTask != null) {
            sender.sendMessage(ChatColor.YELLOW + "The game is already resuming.");
        } else {
            sender.sendMessage(ChatColor.RED + "There is no live Monster Maze game to pause.");
        }
        return true;
    }

    private boolean hasPermission(CommandSender sender) {
        return plugin.isSoloMode() || sender.hasPermission(ADMIN_PERMISSION);
    }

    private void pause(CommandSender sender) {
        pausedAtMs = System.currentTimeMillis();
        setState(GameState.STARTING);
        freezeEntities();
        Bukkit.broadcastMessage(ChatColor.GOLD + "" + ChatColor.BOLD + "Monster Maze PAUSED");
        Bukkit.broadcastMessage(ChatColor.YELLOW + "The round is frozen. Use /pause to resume.");
        sender.sendMessage(ChatColor.GREEN + "Game paused.");
    }

    private void resume(CommandSender sender) {
        resumeTask = Bukkit.getScheduler().runTaskTimer(plugin, new Runnable() {
            int seconds = RESUME_COUNTDOWN_SECONDS;

            @Override
            public void run() {
                if (game.getState() != GameState.STARTING) {
                    resumeTask.cancel();
                    resumeTask = null;
                    return;
                }
                if (seconds <= 0) {
                    try {
                        long pausedFor = Math.max(0L, System.currentTimeMillis() - pausedAtMs);
                        long liveStart = liveStartField.getLong(game);
                        liveStartField.setLong(game, liveStart + pausedFor);
                    } catch (Exception e) {
                        plugin.getLogger().warning("Could not adjust live timer after pause: " + e.getMessage());
                    }
                    setState(GameState.LIVE);
                    Bukkit.broadcastMessage(ChatColor.GREEN + "" + ChatColor.BOLD + "Monster Maze RESUMED!");
                    resumeTask.cancel();
                    resumeTask = null;
                    return;
                }
                Bukkit.broadcastMessage(ChatColor.YELLOW + "Resuming in " + ChatColor.WHITE + seconds + ChatColor.YELLOW + "...");
                seconds--;
            }
        }, 0L, 20L);
        sender.sendMessage(ChatColor.GREEN + "Resume countdown started.");
    }

    private void setState(GameState state) {
        try {
            stateField.set(game, state);
        } catch (Exception e) {
            plugin.getLogger().severe("Unable to change Monster Maze pause state: " + e.getMessage());
        }
    }

    private boolean isPaused() {
        return game.getState() == GameState.STARTING && pausedAtMs > 0L;
    }

    private boolean isAlive(Player player) {
        for (Player alive : game.getAlivePlayers()) {
            if (alive.equals(player)) return true;
        }
        return false;
    }

    private void freezeEntities() {
        for (Player player : game.getAlivePlayers()) {
            player.setVelocity(new Vector(0, 0, 0));
            player.setFallDistance(0f);
        }
        Location center = game.getCenter();
        if (center == null || center.getWorld() == null) return;
        for (Entity entity : center.getWorld().getEntities()) {
            if (entity instanceof Player) continue;
            if (entity instanceof LivingEntity) {
                LivingEntity living = (LivingEntity) entity;
                if (living.getLocation().distanceSquared(center) <= 60 * 60) {
                    living.setVelocity(new Vector(0, 0, 0));
                    living.setFallDistance(0f);
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onMove(PlayerMoveEvent event) {
        if (isPaused() && isAlive(event.getPlayer())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onVelocity(PlayerVelocityEvent event) {
        if (isPaused() && isAlive(event.getPlayer())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInteract(PlayerInteractEvent event) {
        if (isPaused() && isAlive(event.getPlayer())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDamage(EntityDamageEvent event) {
        if (isPaused() && event.getEntity() instanceof Player && isAlive((Player) event.getEntity())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onFood(FoodLevelChangeEvent event) {
        if (isPaused() && event.getEntity() instanceof Player && isAlive((Player) event.getEntity())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDrop(PlayerDropItemEvent event) {
        if (isPaused() && isAlive(event.getPlayer())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    @SuppressWarnings("deprecation")
    public void onPickup(PlayerPickupItemEvent event) {
        if (isPaused() && isAlive(event.getPlayer())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onFlight(PlayerToggleFlightEvent event) {
        if (isPaused() && isAlive(event.getPlayer())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onSneak(PlayerToggleSneakEvent event) {
        if (isPaused() && isAlive(event.getPlayer())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onSprint(PlayerToggleSprintEvent event) {
        if (isPaused() && isAlive(event.getPlayer())) event.setCancelled(true);
    }
}
