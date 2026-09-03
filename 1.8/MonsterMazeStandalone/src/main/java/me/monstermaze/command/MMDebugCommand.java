package me.monstermaze.command;

import me.monstermaze.MonsterMazePlugin;
import me.monstermaze.entity.MonsterManager;
import me.monstermaze.game.GameManager;
import me.monstermaze.util.MobTypes;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;

/** Lightweight diagnostics for the map-test implementation. */
public class MMDebugCommand implements CommandExecutor {
    private final MonsterMazePlugin plugin;

    public MMDebugCommand(MonsterMazePlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        GameManager game = plugin.getGameManager();
        MonsterManager mobs = game.getMonsterManager();

        sender.sendMessage(ChatColor.GOLD + "=== Monster Maze Debug ===");
        sender.sendMessage(ChatColor.GRAY + "Map: " + ChatColor.WHITE + pretty(plugin.getMapManager().getActiveMap()));
        sender.sendMessage(ChatColor.GRAY + "Mode: " + game.getMode().color + game.getMode().id);
        sender.sendMessage(ChatColor.GRAY + "Pattern: " + ChatColor.WHITE + formatPattern(game.getMazeGenerator().getForcedPattern()));
        sender.sendMessage(ChatColor.GRAY + "Configured mob: " + ChatColor.WHITE + prettyMob(plugin.getMapManager().activeMob()));
        sender.sendMessage(ChatColor.GRAY + "Game state: " + ChatColor.WHITE + game.getState());
        sender.sendMessage(ChatColor.GRAY + "Stage: " + ChatColor.WHITE + game.getStage());

        if (args.length > 0 && args[0].equalsIgnoreCase("mobs")) {
            sender.sendMessage(ChatColor.GOLD + "--- Active maze entities ---");
            int count = 0;
            for (Entity entity : plugin.getMapManager().ensureActiveWorld().getEntities()) {
                if (!(entity instanceof LivingEntity)) continue;
                if (entity instanceof org.bukkit.entity.Player) continue;
                if (!entity.getClass().getName().contains("MonsterMaze") && !entity.getClass().getName().contains("monstermaze")) continue;
                sender.sendMessage(ChatColor.GRAY + "#" + (++count) + " " + ChatColor.WHITE + entity.getClass().getSimpleName()
                        + ChatColor.GRAY + " @ " + String.format("%.1f, %.1f, %.1f", entity.getLocation().getX(), entity.getLocation().getY(), entity.getLocation().getZ()));
            }
            if (count == 0) sender.sendMessage(ChatColor.GRAY + "No active Monster Maze entities found.");
        }
        return true;
    }

    private String formatPattern(int pattern) { return pattern < 0 ? "Random" : "Maze " + (pattern + 1); }

    private String pretty(String value) {
        if (value == null || value.isEmpty()) return "Unknown";
        String[] parts = value.split("_");
        StringBuilder out = new StringBuilder();
        for (String part : parts) {
            if (part.isEmpty()) continue;
            if (out.length() > 0) out.append(' ');
            out.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
        }
        return out.toString();
    }

    private String prettyMob(String value) {
        MobTypes.MobType mob = MobTypes.byId(value);
        return mob == null ? pretty(value) : mob.display;
    }
}
