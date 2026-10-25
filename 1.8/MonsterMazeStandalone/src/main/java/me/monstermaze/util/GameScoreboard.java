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
    private final Map<UUID, Integer> layoutPlayerCounts = new ConcurrentHashMap<UUID, Integer>();

    private Scoreboard boardFor(Player p) {
        Scoreboard board=boards.get(p.getUniqueId());
        if(board==null){
            board=Bukkit.getScoreboardManager().getNewScoreboard();
            Team ghostTeam=board.registerNewTeam("mm_ghosts");ghostTeam.setCanSeeFriendlyInvisibles(true);ghostTeam.addEntry(p.getName());
            Objective obj=board.registerNewObjective("mm","dummy");obj.setDisplaySlot(DisplaySlot.SIDEBAR);
            obj.setDisplayName(ChatColor.GOLD+""+ChatColor.BOLD+"Monster Maze");
            boards.put(p.getUniqueId(),board);
        }
        return board;
    }


    private String label(ChatColor color, String text) { return color + "" + ChatColor.BOLD + text; }

    public static final class PlayerDistance {
        public final String name, distance;
        public PlayerDistance(String name, String distance) { this.name=name==null?"?":name; this.distance=distance==null?"?":distance; }
    }

    public void update(Player p, int alive, int stage, int phaseSeconds, boolean hasPad, String mode, String pbText,
                       List<PlayerDistance> standings) {
        Scoreboard board=boardFor(p);Objective obj=board.getObjective("mm");if(obj==null)return;
        int total=standings==null?0:standings.size();
        final int pageSize=6;
        int pages=Math.max(1,(total+pageSize-1)/pageSize);
        int page=pages>1?(int)((System.currentTimeMillis()/5000L)%pages):0;
        int offset=page*pageSize;
        int count=Math.max(0,Math.min(pageSize,total-offset));
        Integer previousCount=layoutPlayerCounts.put(p.getUniqueId(),Integer.valueOf(count));
        if(previousCount==null||previousCount.intValue()!=count)clearDynamicLines(board);
        obj.setDisplayName(pages>1?ChatColor.GOLD+""+ChatColor.BOLD+"Monster Maze ("+(page+1)+"/"+pages+")":
                ChatColor.GOLD+""+ChatColor.BOLD+"Monster Maze");

        // Keep the classic one-line field layout and reserve blank rows between sections.
        // Six player rows fit alongside four real spacer rows within the sidebar's 15-line limit.
        setLine(board,obj,"line_mode",getUniqueEntry(15),15,ChatColor.YELLOW+""+ChatColor.BOLD+"Mode: ",
                ChatColor.WHITE+(mode==null?"?":mode));
        setSpacer(obj,14);
        setLine(board,obj,"line_players",getUniqueEntry(13),13,label(ChatColor.YELLOW,"Players:"),
                ChatColor.GRAY+" ("+total+")");
        for(int i=0;i<count;i++){
            PlayerDistance player=standings.get(offset+i);
            String name=player.name.length()>14?player.name.substring(0,14):player.name;
            int score=12-i;
            setLine(board,obj,"line_p"+i,getUniqueEntry(score),score,ChatColor.YELLOW+name,
                    ChatColor.GRAY+" "+player.distance+"m");
        }
        for(int i=count;i<pageSize;i++)removeLine(board,"line_p"+i);
        setSpacer(obj,6);
        setLine(board,obj,"line_pad",getUniqueEntry(5),5,ChatColor.GREEN+""+ChatColor.BOLD+"Safe Pad: ",
                hasPad?ChatColor.WHITE+""+phaseSeconds+"s":ChatColor.GRAY+"None");
        setSpacer(obj,4);
        setLine(board,obj,"line_stage",getUniqueEntry(3),3,ChatColor.GOLD+""+ChatColor.BOLD+"Stage: ",
                ChatColor.WHITE+String.valueOf(stage));
        setSpacer(obj,2);
        String pb=pbText!=null&&!pbText.isEmpty()?pbText:"None";
        setLine(board,obj,"line_pb",getUniqueEntry(1),1,ChatColor.AQUA+""+ChatColor.BOLD+"PB: ",ChatColor.WHITE+pb);
        applyVisibility(p);
    }

    private void clearDynamicLines(Scoreboard board) {
        for(Team team:new java.util.ArrayList<Team>(board.getTeams())){
            if(!team.getName().startsWith("line_"))continue;
            for(String entry:new java.util.ArrayList<String>(team.getEntries())){
                board.resetScores(entry);
                team.removeEntry(entry);
            }
            team.unregister();
        }
    }

    private void removeLine(Scoreboard board,String teamName){
        Team team=board.getTeam(teamName);if(team==null)return;
        for(String entry:new java.util.ArrayList<String>(team.getEntries())){board.resetScores(entry);team.removeEntry(entry);}
        team.unregister();
    }

    private void setSpacer(Objective obj,int score){
        obj.getScore(getUniqueEntry(score)).setScore(score);
    }

    private void setLine(Scoreboard board,Objective obj,String teamName,String entry,int score,String prefix,String suffix){
        Team team=board.getTeam(teamName);if(team==null)team=board.registerNewTeam(teamName);
        if(!team.hasEntry(entry)){
            for(String previous:new java.util.ArrayList<String>(team.getEntries())){board.resetScores(previous);team.removeEntry(previous);}
            team.addEntry(entry);
        }
        team.setPrefix(limit(prefix,16));team.setSuffix(limit(suffix,16));obj.getScore(entry).setScore(score);
    }
    private String limit(String value,int max){if(value==null)return "";return value.length()>max?value.substring(0,max):value;}
    private String getUniqueEntry(int index){return ChatColor.values()[Math.abs(index)%15].toString()+ChatColor.RESET;}

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
            layoutPlayerCounts.remove(p.getUniqueId());
        }
    }
}
