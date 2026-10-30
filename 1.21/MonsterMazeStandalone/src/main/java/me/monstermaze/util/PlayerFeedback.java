package me.monstermaze.util;

import me.monstermaze.MonsterMazePlugin;
import me.monstermaze.event.MonsterBumpPlayerEvent;
import me.monstermaze.game.GameManager;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Display;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.HashSet;
import java.util.Set;

/**
 * Short-lived modern-only floating feedback for Monster Maze health changes.
 * The indicator is sent only to viewers who are allowed to see the affected player.
 */
public final class PlayerFeedback implements Listener {
    private final Plugin plugin;
    private final GameManager game;
    private final Set<TextDisplay> activeDisplays = new HashSet<TextDisplay>();

    public PlayerFeedback(MonsterMazePlugin plugin, GameManager game) {
        this.plugin = plugin;
        this.game = game;
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    @EventHandler
    public void onMonsterBump(MonsterBumpPlayerEvent event) {
        if (!game.isLive()) return;
        show(event.getPlayer(), "-2 ♥", NamedTextColor.RED);
    }

    /** Display the pad heal value: two hearts for the first player, one for later arrivals. */
    public void showPadReward(Player player, boolean first) {
        show(player, first ? "+2 ♥" : "+1 ♥", NamedTextColor.GREEN);
    }

    private void show(final Player player, String value, NamedTextColor colour) {
        if (player == null || !player.isOnline() || !game.isLive()) return;

        Location origin = player.getLocation().clone().add(0.0D, 2.25D, 0.0D);
        final TextDisplay display = player.getWorld().spawn(origin, TextDisplay.class);
        display.text(Component.text(value, colour).decorate(TextDecoration.BOLD));
        display.setBillboard(Display.Billboard.CENTER);
        display.setAlignment(TextDisplay.TextAlignment.CENTER);
        display.setDefaultBackground(false);
        display.setShadowed(true);
        display.setSeeThrough(true);
        display.setLineWidth(100);
        display.setViewRange(1.0f);
        display.setPersistent(false);
        display.setInvulnerable(true);
        display.setSilent(true);
        display.setGravity(false);
        display.setVisibleByDefault(false);
        activeDisplays.add(display);

        showToAllowedViewers(player, display);

        new BukkitRunnable() {
            private int age;

            @Override
            public void run() {
                if (!game.isLive() || !player.isOnline() || !display.isValid() || age >= 24) {
                    display.remove();
                    activeDisplays.remove(display);
                    cancel();
                    return;
                }

                showToAllowedViewers(player, display);
                Location next = player.getLocation().clone().add(0.0D, 2.25D + age * 0.025D, 0.0D);
                display.teleport(next);
                display.setTextOpacity((byte) Math.max(0, 255 - age * 10));
                age++;
            }
        }.runTaskTimer(plugin, 1L, 1L);
    }

    private void showToAllowedViewers(Player subject, TextDisplay display) {
        for (Player viewer : Bukkit.getOnlinePlayers()) {
            if (viewer.getWorld() != subject.getWorld()
                    || game.getKitManager().isHiddenFrom(viewer, subject)) {
                viewer.hideEntity(plugin, display);
            } else {
                viewer.showEntity(plugin, display);
            }
        }
    }

    /** Remove any outstanding feedback when the round is shut down. */
    public void clear() {
        for (TextDisplay display : new HashSet<TextDisplay>(activeDisplays)) {
            if (display != null && display.isValid()) display.remove();
        }
        activeDisplays.clear();
    }
}
