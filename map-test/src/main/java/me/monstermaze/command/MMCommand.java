package me.monstermaze.command;

import me.monstermaze.MonsterMazePlugin;
import me.monstermaze.game.GameManager;
import me.monstermaze.game.GameState;
import me.monstermaze.game.MazeMode;
import me.monstermaze.kit.KitType;
import me.monstermaze.stats.LeaderboardManager;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;

public class MMCommand implements CommandExecutor {

    private final MonsterMazePlugin plugin;

    public MMCommand(MonsterMazePlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        GameManager gm = plugin.getGameManager();

        if (args.length == 0) {
            sender.sendMessage(ChatColor.GOLD + "=== Monster Maze ===");
            sender.sendMessage(ChatColor.YELLOW + "/mm void|setcenter|start|stop|status|mode|map|pattern");
            sender.sendMessage(ChatColor.YELLOW + "/mm kit <jumper|slowball|body|repulsor|maverick>");
            sender.sendMessage(ChatColor.YELLOW + "/mm kits " + ChatColor.GRAY + "- list kits");
            sender.sendMessage(ChatColor.YELLOW + "/mm pb " + ChatColor.GRAY + "- your bests per pattern");
            sender.sendMessage(ChatColor.YELLOW + "/mm lb [1|2|3] " + ChatColor.GRAY + "- leaderboard");
            sender.sendMessage(ChatColor.AQUA + "Current mode: " + plugin.getMode().color
                    + plugin.getMode().id
                    + ChatColor.GRAY + " (/mm mode <original|speed|modern>)");
            sender.sendMessage(ChatColor.AQUA + "Current map: " + plugin.getMapManager().getActiveMap()
                    + ChatColor.GRAY + " (/mm map <name>)");
            sender.sendMessage(ChatColor.AQUA + "Next pattern: "
                    + ChatColor.WHITE + formatPattern(gm.getMazeGenerator().getForcedPattern()));
            return true;
        }

        String sub = args[0].toLowerCase();

        // Kit selection – any player
        if (sub.equals("kit") || sub.equals("kits")) {
            if (!(sender instanceof Player)) {
                sender.sendMessage(ChatColor.RED + "Players only.");
                return true;
            }

            Player p = (Player) sender;

            if (sub.equals("kits")) {
                sender.sendMessage(ChatColor.GOLD + "Kits:");
                boolean qol = plugin.getMode() != MazeMode.ORIGINAL;

                for (KitType k : KitType.available(qol)) {
                    boolean sel = gm.getKitManager().getKit(p) == k
                            && gm.getKitManager().getKit(p) != null;

                    sender.sendMessage((sel ? ChatColor.GREEN + " > " : ChatColor.GRAY + "   ")
                            + k.display
                            + ChatColor.DARK_GRAY
                            + " (/mm kit " + k.name().toLowerCase() + ")");

                    for (String line : k.description) {
                        sender.sendMessage("      " + line);
                    }
                }

                return true;
            }

            if (args.length < 2) {
                if (gm.isLive()) {
                    sender.sendMessage(ChatColor.RED + "Can't change kit mid-game.");
                    return true;
                }

                gm.getKitManager().openSelector(p);
                return true;
            }

            KitType kit = KitType.byName(args[1]);

            if (kit == null && args[1].equalsIgnoreCase("body")) {
                kit = KitType.BODY_BUILDER;
            }

            if (kit == null) {
                sender.sendMessage(ChatColor.RED + "Unknown kit. Try /mm kits");
                return true;
            }

            if (gm.isLive()) {
                sender.sendMessage(ChatColor.RED + "Can't change kit mid-game.");
                return true;
            }

            gm.getKitManager().setKit(p, kit);
            return true;
        }

        // Personal bests / leaderboard – any player
        if (sub.equals("pb") || sub.equals("lb") || sub.equals("leaderboard")) {
            if (!(sender instanceof Player)) {
                sender.sendMessage(ChatColor.RED + "Players only.");
                return true;
            }

            Player p = (Player) sender;

            if (sub.equals("pb")) {
                showPB(p);
            } else {
                showLeaderboard(p, args);
            }

            return true;
        }

        // Admin commands
        if (!sender.hasPermission("monstermaze.admin")) {
            sender.sendMessage(ChatColor.RED + "No permission.");
            return true;
        }

        switch (sub) {

            case "void":
            case "voidworld":
            case "lobby":
                if (!(sender instanceof Player)) {
                    sender.sendMessage(ChatColor.RED + "Players only.");
                    return true;
                }

                plugin.getVoidWorlds().sendToVoid((Player) sender);
                break;

            case "start":
                if (gm.getState() != GameState.IDLE && gm.getState() != GameState.ENDING) {
                    sender.sendMessage(ChatColor.RED
                            + "Game already running (" + gm.getState() + ").");
                    return true;
                }

                if (sender instanceof Player) {
                    gm.startGame(((Player) sender).getLocation());
                } else {
                    gm.startGame();
                }

                sender.sendMessage(ChatColor.GREEN + "Starting Monster Maze...");
                break;

            case "stop":
            case "force":
                gm.forceStop();
                sender.sendMessage(ChatColor.GREEN + "Monster Maze force stopped.");
                break;

            case "setcenter":
                if (!(sender instanceof Player)) {
                    sender.sendMessage(ChatColor.RED + "Only players can set the center.");
                    return true;
                }

                Player pl = (Player) sender;
                gm.setCenter(pl.getLocation());

                sender.sendMessage(ChatColor.GREEN + "Lobby box placed at "
                        + pl.getLocation().getBlockX() + ", "
                        + pl.getLocation().getBlockY() + ", "
                        + pl.getLocation().getBlockZ());

                sender.sendMessage(ChatColor.GRAY
                        + "World stays empty except this box. Then /mm start");
                break;

            case "mode":
                if (args.length < 2) {
                    sender.sendMessage(ChatColor.RED
                            + "Usage: /mm mode <original|speed|modern>");
                    sender.sendMessage(ChatColor.AQUA
                            + "Current: "
                            + plugin.getMode().color
                            + plugin.getMode().id);

                    for (MazeMode m : MazeMode.values()) {
                        sender.sendMessage(ChatColor.GRAY
                                + " - "
                                + m.color
                                + m.id
                                + ChatColor.GRAY
                                + ": "
                                + m.description);
                    }

                    return true;
                }

                if (gm.isRunning()) {
                    sender.sendMessage(ChatColor.RED
                            + "Change the mode when no game is running.");
                    return true;
                }

                MazeMode m = MazeMode.byName(args[1]);

                if (m == null) {
                    sender.sendMessage(ChatColor.RED
                            + "Unknown mode. Try original, speed, or modern.");
                    return true;
                }

                plugin.setMode(m);
                gm.rerenderLeaderboardBoard();

                sender.sendMessage(ChatColor.GREEN
                        + "Mode set to "
                        + m.color
                        + m.id
                        + ChatColor.GREEN
                        + ". It will apply on the next /mm start.");
                break;

            case "pattern":
            case "maze":
                if (args.length < 2) {
                    sender.sendMessage(ChatColor.YELLOW
                            + "Usage: /mm pattern <1|2|3|random>");
                    sender.sendMessage(ChatColor.GRAY
                            + "Current next pattern: "
                            + formatPattern(gm.getMazeGenerator().getForcedPattern()));
                    return true;
                }

                if (gm.isRunning()) {
                    sender.sendMessage(ChatColor.RED
                            + "Change the pattern when no game is running.");
                    return true;
                }

                String patternArg = args[1].toLowerCase();

                if (patternArg.equals("random")) {
                    gm.getMazeGenerator().setForcedPattern(-1);

                    sender.sendMessage(ChatColor.GREEN
                            + "Maze pattern set to random for the next game.");
                    return true;
                }

                int pattern;

                try {
                    pattern = Integer.parseInt(patternArg);
                } catch (NumberFormatException e) {
                    sender.sendMessage(ChatColor.RED
                            + "Invalid pattern. Use 1, 2, 3, or random.");
                    return true;
                }

                if (pattern < 1 || pattern > 3) {
                    sender.sendMessage(ChatColor.RED
                            + "Invalid pattern. Use 1, 2, 3, or random.");
                    return true;
                }

                gm.getMazeGenerator().setForcedPattern(pattern - 1);

                sender.sendMessage(ChatColor.GREEN
                        + "Maze pattern set to "
                        + ChatColor.WHITE
                        + pattern
                        + ChatColor.GREEN
                        + " for the next game.");
                break;

            case "status":
                sender.sendMessage(ChatColor.AQUA
                        + "State: "
                        + ChatColor.WHITE
                        + gm.getState());

                sender.sendMessage(ChatColor.AQUA
                        + "Stage: "
                        + ChatColor.WHITE
                        + gm.getStage());

                sender.sendMessage(ChatColor.AQUA
                        + "Alive: "
                        + ChatColor.WHITE
                        + gm.getAlivePlayers().size());

                sender.sendMessage(ChatColor.AQUA
                        + "Map: "
                        + ChatColor.WHITE
                        + plugin.getMapManager().getActiveMap());

                sender.sendMessage(ChatColor.AQUA
                        + "Next pattern: "
                        + ChatColor.WHITE
                        + formatPattern(gm.getMazeGenerator().getForcedPattern()));
                break;

            case "map":
            case "arena":
                if (args.length < 2) {
                    sender.sendMessage(ChatColor.RED + "Usage: /mm map <name>");
                    sender.sendMessage(ChatColor.GRAY
                            + "Available: "
                            + String.join(", ", plugin.getMapManager().knownMaps()));
                    return true;
                }

                if (gm.isRunning()) {
                    sender.sendMessage(ChatColor.RED
                            + "Change the map when no game is running.");
                    return true;
                }

                String want = args[1].toLowerCase();

                if (!plugin.getMapManager().setActiveMap(want)) {
                    sender.sendMessage(ChatColor.RED
                            + "Unknown map '"
                            + args[1]
                            + "'. Try: "
                            + String.join(", ", plugin.getMapManager().knownMaps()));
                    return true;
                }

                plugin.getMapManager().ensureActiveWorld();
                gm.applyMap();

                sender.sendMessage(ChatColor.GREEN
                        + "Map set to "
                        + ChatColor.WHITE
                        + want
                        + ChatColor.GREEN
                        + ". Lobby moved; run /mm start.");
                break;

            default:
                sender.sendMessage(ChatColor.RED
                        + "Unknown subcommand. Try /mm");
                break;
        }

        return true;
    }

    private String formatPattern(int pattern) {
        if (pattern < 0) {
            return "random";
        }

        return "Maze " + (pattern + 1);
    }

    private void showPB(Player p) {
        LeaderboardManager lb = plugin.getLeaderboards();
        MazeMode mode = plugin.getMode();

        p.sendMessage(ChatColor.GOLD
                + "=== Personal Bests ("
                + mode.color
                + mode.id
                + ChatColor.GOLD
                + ") ===");

        boolean any = false;

        for (int pat = 0; pat < LeaderboardManager.PATTERN_COUNT; pat++) {
            LeaderboardManager.PBInfo best =
                    lb.getBest(mode, pat, p.getUniqueId());

            if (best != null) {
                any = true;
            }

            p.sendMessage(ChatColor.YELLOW
                    + LeaderboardManager.patternName(pat)
                    + ":"
                    + (best != null
                    ? ChatColor.WHITE
                    + " Stage "
                    + best.stage
                    + kitSuffix(best.kit)
                    : ChatColor.GRAY
                    + " no PB yet"));

            if (best != null) {
                for (KitType k : KitType.available(mode != MazeMode.ORIGINAL)) {
                    int per = lb.getKitPB(
                            mode,
                            pat,
                            p.getUniqueId(),
                            k.id);

                    if (per > 0) {
                        p.sendMessage("   "
                                + k.display
                                + ChatColor.GRAY
                                + ": "
                                + ChatColor.WHITE
                                + "Stage "
                                + per);
                    }
                }
            }
        }

        if (!any) {
            p.sendMessage(ChatColor.GRAY
                    + "Play a game to set a personal best!");
        }
    }

    private void showLeaderboard(Player p, String[] args) {
        LeaderboardManager lb = plugin.getLeaderboards();
        MazeMode mode = plugin.getMode();

        Integer want = null;

        if (args.length >= 2) {
            try {
                int v = Integer.parseInt(args[1]);

                if (v >= 1 && v <= LeaderboardManager.PATTERN_COUNT) {
                    want = v - 1;
                }
            } catch (NumberFormatException ignored) {
            }
        }

        int start = want != null ? want : 0;
        int end = want != null
                ? want + 1
                : LeaderboardManager.PATTERN_COUNT;

        for (int pat = start; pat < end; pat++) {
            p.sendMessage("");

            p.sendMessage(ChatColor.GOLD
                    + "=== "
                    + LeaderboardManager.patternName(pat)
                    + " ("
                    + mode.color
                    + mode.id
                    + ChatColor.GOLD
                    + ") ===");

            List<LeaderboardManager.Entry> rows =
                    lb.getLeaderboard(mode, pat, 10);

            if (rows.isEmpty()) {
                p.sendMessage(ChatColor.GRAY + "No scores yet.");
                continue;
            }

            int rank = 1;

            for (LeaderboardManager.Entry e : rows) {
                p.sendMessage(ChatColor.GRAY
                        + "#"
                        + rank
                        + " "
                        + ChatColor.WHITE
                        + e.name
                        + ChatColor.DARK_GRAY
                        + " — Stage "
                        + ChatColor.GOLD
                        + e.stage
                        + kitSuffix(e.kit));

                rank++;
            }
        }
    }

    private String kitSuffix(String kitId) {
        if (kitId == null || kitId.isEmpty()) {
            return "";
        }

        KitType k = KitType.byName(kitId);

        return ChatColor.DARK_GRAY
                + " ("
                + (k != null ? k.display : kitId)
                + ChatColor.DARK_GRAY
                + ")";
    }
}
