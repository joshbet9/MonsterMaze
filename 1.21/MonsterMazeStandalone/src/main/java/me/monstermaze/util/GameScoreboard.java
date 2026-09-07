package me.monstermaze.util;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.ProtocolManager;
import com.comphenix.protocol.wrappers.WrappedDataValue;
import com.comphenix.protocol.wrappers.WrappedDataWatcher;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

import java.util.ArrayList;
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
    private final ProtocolManager protocolManager = ProtocolLibrary.getProtocolManager();

    private Scoreboard boardFor(Player p) {
        Scoreboard board = boards.get(p.getUniqueId());
        if (board == null) {
            board = Bukkit.getScoreboardManager().getNewScoreboard();

            // mm_ghosts is observer-local. An invisible player placed on this observer's team
            // renders as a semi-transparent ghost to this observer only.
            Team ghostTeam = board.registerNewTeam("mm_ghosts");
            ghostTeam.setCanSeeFriendlyInvisibles(true);

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

    private String label(ChatColor color, String text) {
        return color + "" + ChatColor.BOLD + text;
    }

    private void setupDynamicLine(Scoreboard board, Objective obj, int score, String teamName, String prefix, String suffix) {
        Team team = board.registerNewTeam(teamName);
        String entry = getUniqueEntry(score);
        team.addEntry(entry);
        team.setPrefix(prefix);
        team.setSuffix(suffix);
        obj.getScore(entry).setScore(score);
    }

    private void setStatic(Scoreboard board, Objective obj, int score, String text) {
        obj.getScore(text).setScore(score);
    }

    private String getUniqueEntry(int score) {
        return ChatColor.values()[Math.abs(score) % 15].toString() + ChatColor.RESET;
    }

    public void update(Player p, int alive, int stage, int phaseSeconds, boolean hasPad, String mode, String pbText) {
        Scoreboard board = boardFor(p);

        updateTeam(board, "line_mode", "", mode);
        updateTeam(board, "line_alive", "", String.valueOf(alive));
        updateTeam(board, "line_pad", "",
                hasPad ? ChatColor.WHITE + "" + phaseSeconds + " Seconds" : ChatColor.GRAY + "None");
        updateTeam(board, "line_stage", "", String.valueOf(stage));

        String pb = (pbText != null && !pbText.isEmpty()) ? ChatColor.WHITE + pbText : ChatColor.GRAY + "None";
        updateTeam(board, "line_pb", "", pb);

        // Visibility is rendered per observer, never as a server-global potion state.
        applyVisibility(p);
    }

    /**
     * Apply one observer's requested view to every other player in this scoreboard set.
     * INVISIBLE uses hidePlayer; TRANSPARENT uses the vanilla invisible+seeFriendlyInvisibles
     * rendering path, but the invisibility bit is sent only to the observer's client.
     */
    private void applyVisibility(Player observer) {
        if (observer == null || !observer.isOnline()) return;
        Team ghostTeam = boardFor(observer).getTeam("mm_ghosts");
        VisibilityMode mode = visibilityMode(observer);

        for (UUID id : boards.keySet()) {
            Player target = Bukkit.getPlayer(id);
            if (target == null || !target.isOnline() || target.getUniqueId().equals(observer.getUniqueId())) continue;

            // The old KitManager implementation used a real potion effect to drive this feature.
            // Remove that global state; the renderer below is now entirely observer-specific.
            if (target.hasPotionEffect(PotionEffectType.INVISIBILITY)) {
                target.removePotionEffect(PotionEffectType.INVISIBILITY);
            }

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
        if (item == null) return VisibilityMode.VISIBLE;
        if (item.getType() == org.bukkit.Material.GRAY_DYE) return VisibilityMode.INVISIBLE;
        if (item.getType() == org.bukkit.Material.PURPLE_DYE) return VisibilityMode.TRANSPARENT;
        return VisibilityMode.VISIBLE;
    }

    /** Send only the entity flags needed by this observer; do not mutate the server entity. */
    private void sendInvisibleMetadata(Player observer, Player target, boolean invisible) {
        try {
            WrappedDataWatcher source = WrappedDataWatcher.getEntityWatcher(target);
            Byte current = source.getByte(0);
            byte flags = current == null ? 0 : current.byteValue();
            if (invisible) flags = (byte) (flags | 0x20);
            else flags = (byte) (flags & ~0x20);

            com.comphenix.protocol.events.PacketContainer packet = protocolManager.createPacket(PacketType.Play.Server.ENTITY_METADATA);
            packet.getIntegers().write(0, target.getEntityId());
            List<WrappedDataValue> values = new ArrayList<WrappedDataValue>();
            values.add(new WrappedDataValue(0, WrappedDataWatcher.Registry.get(Byte.class), flags));
            packet.getDataValueCollectionModifier().write(0, values);
            protocolManager.sendServerPacket(observer, packet);
        } catch (Throwable ignored) {
            // Visibility must never be allowed to break the game loop on a ProtocolLib mismatch.
        }
    }

    /** On {@code observer}'s OWN scoreboard only, add/remove {@code hidden} from its ghost team. */
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
        } else {
            t.removeEntry(hp.getName());
        }
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

    public void create() {
        // Compatibility no-op
    }

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
