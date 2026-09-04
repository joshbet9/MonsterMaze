package me.monstermaze.stats;

import me.monstermaze.MonsterMazePlugin;
import me.monstermaze.game.MazeMode;
import me.monstermaze.kit.KitType;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.util.EulerAngle;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * A hologram leaderboard shown in the pre-game lobby. Rendered as a column of
 * invisible marker armor stands for clean spacing, with a central click box.
 */
public class LeaderboardBoard implements Listener {

    private static final int LINES = 10;
    private static final double SPACING = 0.27;
    private static final double BOARD_X = 3.0;

    private final MonsterMazePlugin plugin;
    private final List<ArmorStand> stands = new ArrayList<ArmorStand>();
    private final Map<UUID, Long> lastClick = new HashMap<UUID, Long>();
    private ArmorStand clickTarget;
    private Location anchor;

    private MazeMode activeMode = MazeMode.ORIGINAL;
    private KitType activeKit = null; // null = Overall / All Kits

    public LeaderboardBoard(MonsterMazePlugin plugin) {
        this.plugin = plugin;
        MazeMode storedMode = MazeMode.byName(plugin.getConfig().getString("leaderboard.mode", "Original"));
        if (storedMode != null) activeMode = storedMode;
        KitType storedKit = KitType.byName(plugin.getConfig().getString("leaderboard.kit", ""));
        if (storedKit != null) activeKit = storedKit;
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    public void place(Location lobbyCenter) {
        remove();
        this.anchor = lobbyCenter.clone();
        org.bukkit.World world = anchor.getWorld();
        if (world == null) return;

        Location spawnCenter = anchor.clone().add(BOARD_X, 1.8, 0);

        // Purge any lingering ghost armor stands in the immediate area left from reloads/crashes
        for (Entity e : world.getNearbyEntities(spawnCenter, 2.5, 3.5, 2.5)) {
            if (e instanceof ArmorStand) e.remove();
        }

        // Spawn marker stands inside the lobby so players can approach and click the board
        // without having to pass through the glass perimeter or step into the void.
        for (int i = 0; i < LINES; i++) {
            ArmorStand stand = (ArmorStand) world.spawnEntity(
                    anchor.clone().add(BOARD_X, 1.2 + (LINES - 1 - i) * SPACING, 0), EntityType.ARMOR_STAND);
            stand.setVisible(false);
            stand.setGravity(false);
            stand.setSmall(true);
            stand.setMarker(true);
            stand.setBasePlate(false);
            stand.setArms(false);
            stand.setCustomNameVisible(true);
            try { stand.setRightArmPose(new EulerAngle(i * 0.1, 0, 0)); } catch (Exception ignored) { }
            stands.add(stand);
        }

        // Spawn 1 invisible interaction stand over the hologram to catch clicks
        clickTarget = (ArmorStand) world.spawnEntity(spawnCenter, EntityType.ARMOR_STAND);
        clickTarget.setVisible(false);
        clickTarget.setGravity(false);
        clickTarget.setMarker(false);
        clickTarget.setBasePlate(false);
        clickTarget.setSmall(false);

        render(activeMode);
    }

    public void remove() {
        for (ArmorStand stand : stands) if (stand != null) stand.remove();
        stands.clear();
        if (clickTarget != null) { clickTarget.remove(); clickTarget = null; }
        anchor = null;
    }

    public void cycleMode() {
        MazeMode[] modes = MazeMode.values();
        activeMode = modes[(activeMode.ordinal() + 1) % modes.length];
        saveFilterSelection();
        render(activeMode);
    }

    public void cycleKit() {
        KitType[] kits = KitType.values();
        if (activeKit == null) activeKit = kits[0];
        else {
            int nextIndex = activeKit.ordinal() + 1;
            activeKit = (nextIndex < kits.length) ? kits[nextIndex] : null;
        }
        saveFilterSelection();
        render(activeMode);
    }

    private void saveFilterSelection() {
        plugin.getConfig().set("leaderboard.mode", activeMode.id);
        plugin.getConfig().set("leaderboard.kit", activeKit == null ? "" : activeKit.id);
        plugin.saveConfig();
    }

    public void render(MazeMode mode) {
        if (anchor == null) return;
        this.activeMode = mode;
        LeaderboardManager lb = plugin.getLeaderboards();
        List<LeaderboardManager.OverallEntry> rows = (activeKit == null)
                ? lb.getModeLeaderboard(mode, LINES - 2)
                : lb.getModeAndKitLeaderboard(mode, activeKit, LINES - 2);
        String kitLabel = (activeKit == null) ? "All Kits" : activeKit.display;
        ChallengeManager cm = plugin.getChallengeManager();
        ChallengeManager.Challenge challenge = cm == null ? null : cm.getChallenge();

        for (int i = 0; i < stands.size(); i++) {
            ArmorStand stand = stands.get(i);
            String text;
            if (i == 0) {
                text = ChatColor.GOLD + "" + ChatColor.BOLD + "Leaderboard (" + mode.color + mode.id
                        + ChatColor.GRAY + " - " + ChatColor.YELLOW + kitLabel + ChatColor.GOLD + ")";
            } else if (i - 1 < rows.size() && i < LINES - 1) {
                LeaderboardManager.OverallEntry e = rows.get(i - 1);
                text = ChatColor.GRAY + "#" + i + " " + ChatColor.WHITE + e.name
                        + ChatColor.DARK_GRAY + " - Stage " + ChatColor.GOLD + e.stage;
            } else if (i == LINES - 1 && challenge != null) {
                text = ChatColor.AQUA + "Weekly Challenge #" + challenge.number
                        + ChatColor.GRAY + " — " + ChatColor.WHITE + pretty(challenge.mode)
                        + ChatColor.GRAY + " / Maze " + (challenge.pattern + 1)
                        + ChatColor.GRAY + " / " + ChatColor.WHITE + challenge.kit;
            } else {
                text = "";
            }
            stand.setCustomName(text);
        }
    }

    private static String pretty(String mode) {
        return mode == null || mode.isEmpty() ? "Unknown" : Character.toUpperCase(mode.charAt(0)) + mode.substring(1);
    }

    private boolean isBoardEntity(Entity entity) {
        if (!(entity instanceof ArmorStand)) return false;
        return (clickTarget != null && clickTarget.equals(entity)) || stands.contains(entity);
    }

    private boolean checkCooldown(Player player) {
        long now = System.currentTimeMillis();
        Long last = lastClick.get(player.getUniqueId());
        if (last != null && (now - last) < 150) return false;
        lastClick.put(player.getUniqueId(), now);
        return true;
    }

    private void handleInteract(Player player) {
        if (player.isSneaking()) {
            cycleKit();
            player.sendMessage(ChatColor.YELLOW + "Switched leaderboard kit filter.");
        } else {
            cycleMode();
            player.sendMessage(ChatColor.GREEN + "Switched leaderboard mode filter.");
        }
        player.playSound(player.getLocation(), org.bukkit.Sound.UI_BUTTON_CLICK, 1f, 1.4f);
    }

    @EventHandler
    public void onRightClickAt(PlayerInteractAtEntityEvent event) {
        if (!isBoardEntity(event.getRightClicked())) return;
        event.setCancelled(true);
        if (checkCooldown(event.getPlayer())) handleInteract(event.getPlayer());
    }

    @EventHandler
    public void onRightClick(PlayerInteractEntityEvent event) {
        if (!isBoardEntity(event.getRightClicked())) return;
        event.setCancelled(true);
        if (checkCooldown(event.getPlayer())) handleInteract(event.getPlayer());
    }

    @EventHandler
    public void onLeftClick(EntityDamageByEntityEvent event) {
        if (!isBoardEntity(event.getEntity())) return;
        event.setCancelled(true);
        if (event.getDamager() instanceof Player) {
            Player player = (Player) event.getDamager();
            if (checkCooldown(player)) {
                cycleKit();
                player.sendMessage(ChatColor.YELLOW + "Switched leaderboard kit filter.");
                player.playSound(player.getLocation(), org.bukkit.Sound.UI_BUTTON_CLICK, 1f, 1.4f);
            }
        }
    }

    public void clear() {
        if (anchor == null) return;
        for (ArmorStand stand : stands) stand.setCustomName("");
    }
}
