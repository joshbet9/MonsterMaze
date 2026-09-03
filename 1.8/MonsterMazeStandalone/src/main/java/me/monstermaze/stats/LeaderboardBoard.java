package me.monstermaze.stats;

import me.monstermaze.MonsterMazePlugin;
import me.monstermaze.game.MazeMode;
import me.monstermaze.kit.KitType;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.craftbukkit.v1_8_R3.entity.CraftEntity;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.EulerAngle;
import net.minecraft.server.v1_8_R3.NBTTagCompound;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** A lobby hologram leaderboard backed by the authoritative Monster Maze backend. */
public class LeaderboardBoard implements Listener {
    private static final int LINES = 10;
    private static final double SPACING = 0.27;

    private final MonsterMazePlugin plugin;
    private final List<ArmorStand> stands = new ArrayList<ArmorStand>();
    private final Map<UUID, Long> lastClick = new HashMap<UUID, Long>();
    private final BukkitTask refreshTask;
    private ArmorStand clickTarget;
    private Location anchor;
    private MazeMode activeMode = MazeMode.ORIGINAL;
    private KitType activeKit = null;

    public LeaderboardBoard(MonsterMazePlugin plugin) {
        this.plugin = plugin;
        Bukkit.getPluginManager().registerEvents(this, plugin);
        this.refreshTask = Bukkit.getScheduler().runTaskTimer(plugin, new Runnable() {
            @Override public void run() {
                if (anchor != null) render(activeMode);
            }
        }, 20L, 20L);
    }

    public void place(Location lobbyCenter) {
        remove();
        this.anchor = lobbyCenter.clone();
        org.bukkit.World world = anchor.getWorld();
        if (world == null) return;
        Location spawnCenter = anchor.clone().add(6, 1.8, 0);
        for (Entity e : world.getNearbyEntities(spawnCenter, 2.5, 3.5, 2.5)) if (e instanceof ArmorStand) e.remove();
        for (int i = 0; i < LINES; i++) {
            ArmorStand stand = (ArmorStand) world.spawnEntity(anchor.clone().add(6, 1.2 + (LINES - 1 - i) * SPACING, 0), EntityType.ARMOR_STAND);
            stand.setVisible(false); stand.setGravity(false); stand.setSmall(true); stand.setMarker(true);
            stand.setBasePlate(false); stand.setArms(false); stand.setCustomNameVisible(true);
            try { stand.setRightArmPose(new EulerAngle(i * 0.1, 0, 0)); } catch (Exception ignored) {}
            stands.add(stand);
        }
        clickTarget = (ArmorStand) world.spawnEntity(spawnCenter, EntityType.ARMOR_STAND);
        clickTarget.setVisible(false); clickTarget.setGravity(false); clickTarget.setMarker(false);
        clickTarget.setBasePlate(false); clickTarget.setSmall(false);
        try {
            net.minecraft.server.v1_8_R3.Entity nmsEnt = ((CraftEntity) clickTarget).getHandle();
            NBTTagCompound tag = new NBTTagCompound(); nmsEnt.c(tag); tag.setByte("NoGravity", (byte) 1); nmsEnt.f(tag);
        } catch (Throwable ignored) {}
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
        render(activeMode);
    }

    public void cycleKit() {
        KitType[] kits = KitType.values();
        if (activeKit == null) activeKit = kits[0];
        else {
            int nextIndex = activeKit.ordinal() + 1;
            activeKit = (nextIndex < kits.length) ? kits[nextIndex] : null;
        }
        render(activeMode);
    }

    public void render(MazeMode mode) {
        if (anchor == null) return;
        this.activeMode = mode;
        LeaderboardManager lb = plugin.getLeaderboards();
        List<LeaderboardManager.OverallEntry> rows = (activeKit == null)
                ? lb.getModeLeaderboard(mode, LINES - 2)
                : lb.getModeAndKitLeaderboard(mode, activeKit, LINES - 2);
        String kitLabel = (activeKit == null) ? "All Kits" : activeKit.display;
        for (int i = 0; i < stands.size(); i++) {
            ArmorStand stand = stands.get(i);
            String text;
            if (i == 0) {
                text = ChatColor.GOLD + "" + ChatColor.BOLD + "Leaderboard (" + mode.color + mode.id
                        + ChatColor.GRAY + " - " + ChatColor.YELLOW + kitLabel + ChatColor.GOLD + ")";
            } else if (i - 1 < rows.size()) {
                LeaderboardManager.OverallEntry e = rows.get(i - 1);
                text = ChatColor.GRAY + "#" + i + " " + ChatColor.WHITE + e.name
                        + ChatColor.DARK_GRAY + " - Stage " + ChatColor.GOLD + e.stage;
            } else text = "";
            stand.setCustomName(text);
        }
    }

    private boolean isBoardEntity(Entity entity) {
        if (!(entity instanceof ArmorStand)) return false;
        return (clickTarget != null && clickTarget.equals(entity)) || stands.contains(entity);
    }

    private boolean checkCooldown(Player player) {
        long now = System.currentTimeMillis(); Long last = lastClick.get(player.getUniqueId());
        if (last != null && now - last < 150) return false;
        lastClick.put(player.getUniqueId(), now); return true;
    }

    private void handleInteract(Player player) {
        if (player.isSneaking()) { cycleKit(); player.sendMessage(ChatColor.YELLOW + "Switched leaderboard kit filter."); }
        else { cycleMode(); player.sendMessage(ChatColor.GREEN + "Switched leaderboard mode filter."); }
        player.playSound(player.getLocation(), org.bukkit.Sound.CLICK, 1f, 1.4f);
    }

    @EventHandler public void onRightClickAt(PlayerInteractAtEntityEvent event) {
        if (!isBoardEntity(event.getRightClicked())) return;
        event.setCancelled(true); if (checkCooldown(event.getPlayer())) handleInteract(event.getPlayer());
    }

    @EventHandler public void onRightClick(PlayerInteractEntityEvent event) {
        if (!isBoardEntity(event.getRightClicked())) return;
        event.setCancelled(true); if (checkCooldown(event.getPlayer())) handleInteract(event.getPlayer());
    }

    @EventHandler public void onLeftClick(EntityDamageByEntityEvent event) {
        if (!isBoardEntity(event.getEntity())) return;
        event.setCancelled(true);
        if (event.getDamager() instanceof Player) {
            Player player = (Player) event.getDamager();
            if (checkCooldown(player)) { cycleKit(); player.sendMessage(ChatColor.YELLOW + "Switched leaderboard kit filter."); player.playSound(player.getLocation(), org.bukkit.Sound.CLICK, 1f, 1.4f); }
        }
    }

    public void clear() {
        if (anchor == null) return;
        for (ArmorStand stand : stands) stand.setCustomName("");
    }
}
