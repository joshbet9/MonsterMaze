package me.monstermaze.util;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.ProtocolManager;
import com.comphenix.protocol.wrappers.WrappedDataValue;
import com.comphenix.protocol.wrappers.WrappedDataWatcher;
import com.comphenix.protocol.events.PacketContainer;
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

/** Per-observer scoreboard and player-visibility state. */
public class GameScoreboard {
    private final Map<UUID, Scoreboard> boards = new ConcurrentHashMap<UUID, Scoreboard>();
    private final ProtocolManager protocolManager = ProtocolLibrary.getProtocolManager();

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
        boolean paging=total>14;int pageSize=paging?14:15;
        int pages=paging?(total+pageSize-1)/pageSize:1;
        int page=paging?(int)((System.currentTimeMillis()/5000L)%pages):0;
        int offset=page*pageSize;int count=Math.max(0,Math.min(pageSize,total-offset));
        boolean compact=total>10;
        obj.setDisplayName(paging?ChatColor.GOLD+""+ChatColor.BOLD+"Monster Maze ("+(page+1)+"/"+pages+")":
                ChatColor.GOLD+""+ChatColor.BOLD+"Monster Maze");
        if(compact){
            removeLine(board,"line_mode");removeLine(board,"line_pad");removeLine(board,"line_stage");removeLine(board,"line_pb");
            obj.getScore(label(ChatColor.YELLOW,"Players:")).setScore(15);
        }else{
            setLine(board,obj,"line_mode",getUniqueEntry(15),15,ChatColor.YELLOW+"Mode: ",ChatColor.WHITE+(mode==null?"?":mode));
            obj.getScore(label(ChatColor.YELLOW,"Players:")).setScore(14);
        }
        for(int i=0;i<15;i++){
            String teamName="line_p"+i,entry=getUniqueEntry(i+1);
            if(i<count){
                PlayerDistance player=standings.get(offset+i);
                String name=player.name.length()>14?player.name.substring(0,14):player.name;
                int score=compact?14-i:13-i;
                setLine(board,obj,teamName,entry,score,ChatColor.YELLOW+name,ChatColor.GRAY+" "+player.distance+"b");
            }else removeLine(board,teamName);
        }
        if(!compact){
            int padScore=13-count,stageScore=12-count,pbScore=11-count;
            setLine(board,obj,"line_pad",getUniqueEntry(12),padScore,ChatColor.GREEN+"Pad: ",
                    hasPad?ChatColor.WHITE+""+phaseSeconds+"s":ChatColor.GRAY+"None");
            setLine(board,obj,"line_stage",getUniqueEntry(13),stageScore,ChatColor.GOLD+"Stage: ",ChatColor.WHITE+String.valueOf(stage));
            String pb=pbText!=null&&!pbText.isEmpty()?pbText:"None";
            setLine(board,obj,"line_pb",getUniqueEntry(14),pbScore,ChatColor.AQUA+"PB: ",ChatColor.WHITE+pb);
        }
        applyVisibility(p);
    }

    private void removeLine(Scoreboard board,String teamName){
        Team team=board.getTeam(teamName);if(team==null)return;
        for(String entry:new java.util.ArrayList<String>(team.getEntries())){board.resetScores(entry);team.removeEntry(entry);}
        team.unregister();
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
        if (item == null) return VisibilityMode.VISIBLE;
        if (item.getType() == org.bukkit.Material.GRAY_DYE) return VisibilityMode.INVISIBLE;
        if (item.getType() == org.bukkit.Material.PURPLE_DYE) return VisibilityMode.TRANSPARENT;
        return VisibilityMode.VISIBLE;
    }

    private void sendInvisibleMetadata(Player observer, Player target, boolean invisible) {
        try {
            WrappedDataWatcher source = WrappedDataWatcher.getEntityWatcher(target);
            Byte current = source.getByte(0);
            byte flags = current == null ? 0 : current.byteValue();
            flags = invisible ? (byte) (flags | 0x20) : (byte) (flags & ~0x20);
            PacketContainer packet = protocolManager.createPacket(PacketType.Play.Server.ENTITY_METADATA);
            packet.getIntegers().write(0, target.getEntityId());
            List<WrappedDataValue> values = new ArrayList<WrappedDataValue>();
            values.add(new WrappedDataValue(0, WrappedDataWatcher.Registry.get(Byte.class), flags));
            packet.getDataValueCollectionModifier().write(0, values);
            protocolManager.sendServerPacket(observer, packet);
        } catch (Throwable ignored) { }
    }

    /** Compatibility shim for older KitManager calls. Global ghost state is intentionally gone. */
    public void setGhost(UUID hidden, boolean ghost) { }

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
