package me.monstermaze.util;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class GameScoreboard {
    private final Map<UUID, Scoreboard> boards = new ConcurrentHashMap<UUID, Scoreboard>();

    /** Hidden players currently set to "transparent" (ghost). These are added to every observer's
     *  mm_ghosts team (canSeeFriendlyInvisibles) so they render see-through. Everyone else who is
     *  invisible is left OFF the team, so they stay completely hidden. */
    private final Set<UUID> ghostPlayers = ConcurrentHashMap.newKeySet();

    private Scoreboard boardFor(Player p) {
        Scoreboard board = boards.get(p.getUniqueId());
        if (board == null) {
            board = Bukkit.getScoreboardManager().getNewScoreboard();

            // mm_ghosts: members who also have INVISIBILITY render as a see-through ghost to this
            // observer. Only transparent players are members; invisible players stay fully hidden.
            Team ghostTeam = board.registerNewTeam("mm_ghosts");
            ghostTeam.setCanSeeFriendlyInvisibles(true);
            for (UUID id : ghostPlayers) {
                Player ghost = Bukkit.getPlayer(id);
                if (ghost != null && ghost.isOnline()) {
                    ghostTeam.addEntry(ghost.getName());
                }
            }

            Objective obj = board.registerNewObjective("mm", "dummy");
            obj.setDisplaySlot(DisplaySlot.SIDEBAR);
            obj.setDisplayName(ChatColor.GOLD + "" + ChatColor.BOLD + "Monster Maze");

            // Layout: each concept is a static label row with its dynamic value row directly below.
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

        // Keep this observer's ghost team in sync with the set of transparent players.
        Team ghostTeam = board.getTeam("mm_ghosts");
        if (ghostTeam != null) {
            for (UUID id : ghostPlayers) {
                Player ghost = Bukkit.getPlayer(id);
                if (ghost != null && ghost.isOnline() && !ghostTeam.hasEntry(ghost.getName())) {
                    ghostTeam.addEntry(ghost.getName());
                }
            }
        }

        // Update each value row (prefix stays empty; value goes in the suffix).
        updateTeam(board, "line_mode", "", mode);
        updateTeam(board, "line_alive", "", String.valueOf(alive));
        updateTeam(board, "line_pad", "",
                hasPad ? ChatColor.WHITE + "" + phaseSeconds + " Seconds" : ChatColor.GRAY + "None");
        updateTeam(board, "line_stage", "", String.valueOf(stage));

        String pb = (pbText != null && !pbText.isEmpty()) ? ChatColor.WHITE + pbText : ChatColor.GRAY + "None";
        updateTeam(board, "line_pb", "", pb);
    }

    /** Make {@code hidden} render as a see-through ghost (true) or fully hidden (false) to every observer. */
    public void setGhost(UUID hidden, boolean ghost) {
        if (ghost) {
            if (!ghostPlayers.add(hidden)) return;
        } else {
            if (!ghostPlayers.remove(hidden)) return;
        }
        Player hp = Bukkit.getPlayer(hidden);
        if (hp == null) return;
        for (Scoreboard b : boards.values()) {
            Team t = b.getTeam("mm_ghosts");
            if (t == null) continue;
            if (ghost) {
                if (!t.hasEntry(hp.getName())) t.addEntry(hp.getName());
            } else {
                t.removeEntry(hp.getName());
            }
        }
    }

    /** On {@code observer}'s OWN scoreboard only, add/remove {@code hidden} from its ghost team so
     *  {@code observer} — not everyone — renders {@code hidden} as a see-through ghost. */
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
        ghostPlayers.clear();
    }
}
