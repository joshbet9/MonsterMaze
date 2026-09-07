package me.monstermaze.util;

import net.minecraft.server.v1_8_R3.DataWatcher;
import net.minecraft.server.v1_8_R3.PacketPlayOutEntityMetadata;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.craftbukkit.v1_8_R3.entity.CraftPlayer;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Per-observer scoreboard and player-visibility state.
 *
 * <p>The visibility toggle is deliberately client-specific. Each observer has their own
 * scoreboard/team and receives their own entity-metadata packet, so one player's view mode
 * cannot change what another player sees.</p>
 */
public class GameScoreboard {
    private final Map<UUID, Scoreboard> boards = new ConcurrentHashMap<UUID, Scoreboard>();

    private Scoreboard boardFor(Player p) {
        Scoreboard board = boards.get(p.getUniqueId());
        if (board == null) {
            board = Bukkit.getScoreboardManager().getNewScoreboard();
            Team ghostTeam = board.registerNewTeam("mm_ghosts");
            ghostTeam.setCanSeeFriendlyInvisibles(true);
            ghostTeam.addEntry(p.getName());

            Objective obj = board.registerNewObjective("mm", "dummy");
            obj.setDisplaySlot(DisplaySlot.SIDEBAR);
            obj.setDisplayName(ChatColor.GOLD + "" + ChatColor.BOLD + "Monster Maze");
            setStatic(board, obj, 16, label(ChatColor.YELLOW, "Mode"));
            setupDynamicLine(board, obj, 15, "line_mode", "", "");
            setStatic(board, obj, 14, " ");
            setStatic(board, obj, 13, label(ChatColor.YELLOW, "Players"));
            setupDynamicLine(board, obj, 12, "line_alive", "", "");
            setStatic(board, obj, 11, "  ");
            setStatic(board, obj, 10, label(ChatColor.GREEN, "Safe Pad"));
            setupDynamicLine(board, obj, 9, "line_pad", "", "");
            setStatic(board, obj, 8, "   ");
            setStatic(board, obj, 7, label(ChatColor.GOLD, "Stage"));
            setupDynamicLine(board, obj, 6, "line_stage", "", "");
            setStatic(board, obj, 5, "    ");
            setStatic(board, obj, 4, label(ChatColor.AQUA, "PB"));
            setupDynamicLine(board, obj, 3, "line_pb", "", "");
            boards.put(p.getUniqueId(), board);
        }
        return board;
    }

    private String label(ChatColor color, String text) { return color + "" + ChatColor.BOLD + text; }

    private void setupDynamicLine(Scoreboard board, Objective obj, int score, String teamName, String prefix, String suffix) {
        Team team = board.registerNewTeam(teamName);
        String entry = getUniqueEntry(score);
        team.addEntry(entry);
        team.setPrefix(prefix);
        team.setSuffix(suffix);
        obj.getScore(entry).setScore(score);
    }

    private void setStatic(Scoreboard board, Objective obj, int score, String text) { obj.getScore(text).setScore(score); }

    private String getUniqueEntry(int score) { return ChatColor.values()[Math.abs(score) % 15].toString() + ChatColor.RESET; }

    public void update(Player p, int alive, int stage, int phaseSeconds, boolean hasPad, String mode, String pbText) {
        Scoreboard board = boardFor(p);
        updateTeam(board, "line_mode", "", mode);
        updateTeam(board, "line_alive", "", String.valueOf(alive));
        updateTeam(board, "line_pad", "", hasPad ? ChatColor.WHITE + "" + phaseSeconds + " Seconds" : ChatColor.GRAY + "None");
        updateTeam(board, "line_stage", "", String.valueOf(stage));
        String pb = (pbText != null && !pbText.isEmpty()) ? ChatColor.WHITE + pbText : ChatColor.GRAY + "None";
        updateTeam(board, "line_pb", "", pb);
        applyVisibility(p);
    }

    /** Apply the observer's mode without changing another observer's client view. */
    private void applyVisibility(Player observer) {
        if (observer == null || !observer.isOnline()) return;
        Team ghostTeam = boardFor(observer).getTeam("mm_ghosts");
        VisibilityMode mode = visibilityMode(observer);
        for (UUID id : boards.keySet()) {
            Player target = Bukkit.getPlayer(id);
            if (target == null || !target.isOnline() || target.getUniqueId().equals(observer.getUniqueId())) continue;
            if (target.hasPotionEffect(PotionEffectType.INVISIBILITY)) target.removePotionEffect(PotionEffectType.INVISIBILITY);
            if (mode == VisibilityMode.INVISIBLE) {
                observer.hidePlayer(target);
                if (ghostTeam != null) ghostTeam.removeEntry(target.getName());
                sendInvisibleMetadata(observer, target, false);
            } else if (mode == VisibilityMode.TRANSPARENT) {
                observer.showPlayer(target);
                if (ghostTeam != null && !ghostTeam.hasEntry(target.getName())) ghostTeam.addEntry(target.getName());
                sendInvisibleMetadata(observer, target, true);
            } else {
                observer.showPlayer(target);
                if (ghostTeam != null) ghostTeam.removeEntry(target.getName());
                sendInvisibleMetadata(observer, target, false);
            }
        }
    }

    private enum VisibilityMode { VISIBLE, INVISIBLE, TRANSPARENT }

    private VisibilityMode visibilityMode(Player observer) {
        org.bukkit.inventory.ItemStack item = observer.getInventory().getItem(7);
        if (item == null || item.getType() != org.bukkit.Material.INK_SACK) return VisibilityMode.VISIBLE;
        short data = item.getDurability();
        if (data == 8) return VisibilityMode.INVISIBLE;
        if (data == 5) return VisibilityMode.TRANSPARENT;
        return VisibilityMode.VISIBLE;
    }

    private void sendInvisibleMetadata(Player observer, Player target, boolean invisible) {
        try {
            net.minecraft.server.v1_8_R3.EntityPlayer nmsTarget = ((CraftPlayer) target).getHandle();
            byte flags = nmsTarget.getDataWatcher().getByte(0);
            flags = invisible ? (byte) (flags | 0x20) : (byte) (flags & ~0x20);
            DataWatcher watcher = new DataWatcher(null);
            watcher.a(0, flags);
            PacketPlayOutEntityMetadata packet = new PacketPlayOutEntityMetadata(nmsTarget.getId(), watcher, true);
            ((CraftPlayer) observer).getHandle().playerConnection.sendPacket(packet);
        } catch (Throwable ignored) {
            // Never let a version-specific visibility packet break the game loop.
        }
    }

    /** Compatibility shim for older KitManager calls. Global ghost state is intentionally gone. */
    public void setGhost(UUID hidden, boolean ghost) { }

    /** On the observer's own scoreboard only, add/remove a target from its ghost team. */
    public void setGhostFor(UUID observer, UUID hidden, boolean ghost) {
        Player op = Bukkit.getPlayer(observer);
        if (op == null || !op.isOnline()) return;
        Scoreboard board = boardFor(op);
        Team t = board.getTeam("mm_ghosts");
        if (t == null) return;
        Player hp = Bukkit.getPlayer(hidden);
        if (hp == null || !hp.isOnline()) return;
        if (ghost) {
            if (!t.hasEntry(hp.getName())) t.addEntry(hp.getName());
        } else t.removeEntry(hp.getName());
    }

    private void updateTeam(Scoreboard board, String teamName, String prefix, String suffix) {
        Team team = board.getTeam(teamName);
        if (team != null) {
            if (prefix.length() > 16) prefix = prefix.substring(0, 16);
            if (suffix.length() > 16) suffix = suffix.substring(0, 16);
            team.setPrefix(prefix);
            team.setSuffix(suffix);
        }
    }

    public void create() { }

    public void apply(List<Player> players) {
        for (Player p : players) {
            Scoreboard board = boards.get(p.getUniqueId());
            if (board != null) p.setScoreboard(board);
        }
    }

    public void clear(List<Player> players) {
        Scoreboard main = Bukkit.getScoreboardManager().getMainScoreboard();
        for (Player p : players) {
            p.setScoreboard(main);
            boards.remove(p.getUniqueId());
        }
    }
}
