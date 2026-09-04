package me.monstermaze.stats;

import me.monstermaze.MonsterMazePlugin;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Hosted-server tournament cache. Inactive when the backend is not configured. */
public final class TournamentManager {
    private final MonsterMazePlugin plugin; private final BackendClient backend;
    private volatile Tournament tournament; private final Map<String,Match> playerMatches=new HashMap<String,Match>(); private volatile List<Row> leaderboard=new ArrayList<Row>();
    public TournamentManager(MonsterMazePlugin plugin){this.plugin=plugin;this.backend=plugin.getBackendClient();if(backend!=null&&backend.isEnabled()){Bukkit.getScheduler().runTaskLater(plugin,new Runnable(){public void run(){refresh();}},60L);Bukkit.getScheduler().runTaskTimer(plugin,new Runnable(){public void run(){refresh();}},600L,600L);}}
    public boolean isEnabled(){return backend!=null&&backend.isEnabled();}
    public void refresh(){if(!isEnabled())return;Bukkit.getScheduler().runTaskAsynchronously(plugin,new Runnable(){public void run(){try{String cj=backend.get("/api/v1/tournament/current");Tournament t=parseTournament(cj);List<Row> rows=parseLeaderboard(backend.get("/api/v1/tournament/leaderboard"));synchronized(playerMatches){tournament=t;leaderboard=rows;}for(Player p:Bukkit.getOnlinePlayers())refreshPlayerMatch(p);}catch(Exception e){plugin.getLogger().warning("Tournament sync failed: "+e.getMessage());}}});}
    private void refreshPlayerMatch(final Player p){Bukkit.getScheduler().runTaskAsynchronously(plugin,new Runnable(){public void run(){try{Match m=parseMatch(backend.get("/api/v1/tournament/player/"+p.getUniqueId()));synchronized(playerMatches){if(m==null)playerMatches.remove(p.getUniqueId().toString().toLowerCase());else playerMatches.put(p.getUniqueId().toString().toLowerCase(),m);}}catch(Exception ignored){}}});}
    private Match matchFor(Player p){synchronized(playerMatches){return playerMatches.get(p.getUniqueId().toString().toLowerCase());}}
    public void show(Player p,boolean lbOnly){if(!isEnabled()){p.sendMessage(ChatColor.GRAY+"Tournaments are available on the hosted Monster Maze server.");return;}Tournament t=tournament;if(t==null){p.sendMessage(ChatColor.YELLOW+"Tournament information is still loading...");refresh();return;}p.sendMessage("");p.sendMessage(ChatColor.GOLD+"=== Tournament #"+t.number+" — "+t.name+" ===");p.sendMessage(ChatColor.AQUA+"Status: "+ChatColor.WHITE+pretty(t.status));if(!lbOnly){p.sendMessage(ChatColor.AQUA+"Players: "+ChatColor.WHITE+t.players+" | Bracket: "+t.bracketSize);if(t.start!=null)p.sendMessage(ChatColor.AQUA+"Start: "+ChatColor.WHITE+formatDate(t.start));Match m=matchFor(p);if(m!=null){boolean first=m.p1.equalsIgnoreCase(p.getUniqueId().toString());p.sendMessage(ChatColor.YELLOW+"Your match: "+ChatColor.WHITE+(first?m.p1Name:m.p2Name)+" vs "+(first?m.p2Name:m.p1Name));p.sendMessage(ChatColor.YELLOW+"Game: "+ChatColor.WHITE+m.nextGame+"/3"+ChatColor.GRAY+" — series "+m.w1+"–"+m.w2);}else p.sendMessage(ChatColor.GRAY+"You have no currently playable tournament match.");}p.sendMessage(ChatColor.YELLOW+"--- Tournament Points ---");if(leaderboard.isEmpty())p.sendMessage(ChatColor.GRAY+"No tournament points recorded yet.");else{int rank=1;for(Row r:leaderboard){p.sendMessage(ChatColor.GRAY+"#"+rank+" "+ChatColor.WHITE+r.name+ChatColor.DARK_GRAY+" — "+ChatColor.GOLD+r.points+" pts");rank++;}}p.sendMessage("");}
    public void showMatch(Player p){if(!isEnabled()){p.sendMessage(ChatColor.GRAY+"Tournament information is available on the hosted Monster Maze server.");return;}Match m=matchFor(p);if(m==null){p.sendMessage(ChatColor.YELLOW+"You do not currently have a playable tournament match.");refreshPlayerMatch(p);return;}boolean first=m.p1.equalsIgnoreCase(p.getUniqueId().toString());p.sendMessage(ChatColor.GOLD+"=== Your Tournament Match ===");p.sendMessage(ChatColor.WHITE+(first?m.p1Name:m.p2Name)+ChatColor.GRAY+" vs "+ChatColor.WHITE+(first?m.p2Name:m.p1Name));p.sendMessage(ChatColor.AQUA+"Game "+ChatColor.WHITE+m.nextGame+ChatColor.AQUA+"/3");p.sendMessage(ChatColor.AQUA+"Series: "+ChatColor.WHITE+m.w1+"–"+m.w2);}
    private static Tournament parseTournament(String j){if(j==null||!j.contains("\"tournament\""))return null;String o=objectAfter(j,"tournament");if(o==null||o.trim().equals("null"))return null;int n=integer(o,"number",0),b=integer(o,"bracketSize",0),pc=countObjects(array(o,"players"));String name=string(o,"name"),status=string(o,"status"),start=string(o,"start");return n<1||name==null?null:new Tournament(n,name,status,start,b,pc);}
    private static List<Row> parseLeaderboard(String j){List<Row> out=new ArrayList<Row>();if(j==null)return out;String a=array(j,"rows");if(a==null)return out;Matcher m=Pattern.compile("\\{([^{}]*)\\}").matcher(a);while(m.find()){String o=m.group(1),n=string(o,"name");int pts=integer(o,"score",integer(o,"points",0));if(n!=null)out.add(new Row(n,pts));}return out;}
    private static Match parseMatch(String j){if(j==null||!j.contains("\"match\""))return null;String o=objectAfter(j,"match");if(o==null||o.trim().equals("null"))return null;String a=string(o,"player1"),b=string(o,"player2");if(a==null||b==null)return null;int x=integer(o,"player1Wins",0),y=integer(o,"player2Wins",0);return new Match(a,b,displayName(o,"player1Name",a),displayName(o,"player2Name",b),x,y,x+y+1);}
    private static String displayName(String o,String k,String u){String s=string(o,k);return s==null?u.substring(0,Math.min(8,u.length())):s;}
    private static String objectAfter(String j,String k){Matcher m=Pattern.compile("\\\""+Pattern.quote(k)+"\\\"\\s*:\\s*(\\{.*?\\}|null)",Pattern.DOTALL).matcher(j);return m.find()?m.group(1):null;}
    private static String array(String j,String k){Matcher m=Pattern.compile("\\\""+Pattern.quote(k)+"\\\"\\s*:\\s*\\[(.*?)\\]",Pattern.DOTALL).matcher(j);return m.find()?m.group(1):null;}
    private static int countObjects(String s){if(s==null)return 0;Matcher m=Pattern.compile("\\{([^{}]*)\\}").matcher(s);int n=0;while(m.find())n++;return n;}
    private static String string(String j,String k){Matcher m=Pattern.compile("\\\""+Pattern.quote(k)+"\\\"\\s*:\\s*\\\"((?:\\\\.|[^\\\"\\\\])*)\\\"").matcher(j);return m.find()?m.group(1).replace("\\\"","\"").replace("\\\\","\\"):null;}
    private static int integer(String j,String k,int f){Matcher m=Pattern.compile("\\\""+Pattern.quote(k)+"\\\"\\s*:\\s*(-?\\d+)").matcher(j);if(!m.find())return f;try{return Integer.parseInt(m.group(1));}catch(NumberFormatException e){return f;}}
    private static String pretty(String s){return s==null||s.length()==0?"Unknown":Character.toUpperCase(s.charAt(0))+s.substring(1);}
    private static String formatDate(String s){return s==null?"Unknown":s.replace('T',' ').replace("+00:00"," UTC");}
    public static final class Tournament{public final int number,bracketSize,players;public final String name,status,start;Tournament(int n,String nm,String st,String sr,int b,int p){number=n;name=nm;status=st;start=sr;bracketSize=b;players=p;}}
    public static final class Match{public final String p1,p2,p1Name,p2Name;public final int w1,w2,nextGame;Match(String a,String b,String an,String bn,int x,int y,int n){p1=a;p2=b;p1Name=an;p2Name=bn;w1=x;w2=y;nextGame=n;}}
    public static final class Row{public final String name;public final int points;Row(String n,int p){name=n;points=p;}}
}
