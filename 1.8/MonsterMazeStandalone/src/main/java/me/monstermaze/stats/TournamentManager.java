package me.monstermaze.stats;

import me.monstermaze.MonsterMazePlugin;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Hosted-server tournament cache. Inactive when the backend is not configured. */
public final class TournamentManager {
    private final MonsterMazePlugin plugin;
    private final BackendClient backend;
    private volatile Tournament tournament;
    private volatile Match playerMatch;
    private volatile List<Row> leaderboard = new ArrayList<Row>();
    private volatile long lastRefresh;

    public TournamentManager(MonsterMazePlugin plugin) {
        this.plugin = plugin;
        this.backend = plugin.getBackendClient();
        if (backend != null && backend.isEnabled()) {
            Bukkit.getScheduler().runTaskLater(plugin, new Runnable() { @Override public void run() { refresh(); } }, 60L);
            Bukkit.getScheduler().runTaskTimer(plugin, new Runnable() { @Override public void run() { refresh(); } }, 600L, 600L);
        }
    }

    public boolean isEnabled() { return backend != null && backend.isEnabled(); }
    public Tournament getTournament() { return tournament; }
    public Match getPlayerMatch() { return playerMatch; }
    public List<Row> getLeaderboard() { return new ArrayList<Row>(leaderboard); }

    public void refresh() {
        if (!isEnabled()) return;
        Bukkit.getScheduler().runTaskAsynchronously(plugin, new Runnable() {
            @Override public void run() {
                try {
                    String currentJson = backend.get("/api/v1/tournament/current");
                    Tournament t = parseTournament(currentJson);
                    String lbJson = backend.get("/api/v1/tournament/leaderboard");
                    List<Row> rows = parseLeaderboard(lbJson);
                    tournament = t;
                    leaderboard = rows;
                    lastRefresh = System.currentTimeMillis();
                    for (Player p : Bukkit.getOnlinePlayers()) refreshPlayerMatch(p);
                } catch (Exception e) {
                    plugin.getLogger().warning("Tournament sync failed: " + e.getMessage());
                }
            }
        });
    }

    private void refreshPlayerMatch(final Player player) {
        if (!isEnabled()) return;
        Bukkit.getScheduler().runTaskAsynchronously(plugin, new Runnable() {
            @Override public void run() {
                try {
                    Match m = parseMatch(backend.get("/api/v1/tournament/player/" + player.getUniqueId().toString()));
                    playerMatch = m;
                } catch (Exception ignored) { }
            }
        });
    }

    public void show(Player p, boolean leaderboardOnly) {
        if (!isEnabled()) { p.sendMessage(ChatColor.GRAY + "Tournaments are available on the hosted Monster Maze server."); return; }
        Tournament t = tournament;
        if (t == null) { p.sendMessage(ChatColor.YELLOW + "Tournament information is still loading..."); refresh(); return; }
        p.sendMessage("");
        p.sendMessage(ChatColor.GOLD + "=== Tournament #" + t.number + " — " + t.name + " ===");
        p.sendMessage(ChatColor.AQUA + "Status: " + ChatColor.WHITE + pretty(t.status));
        if (!leaderboardOnly) {
            p.sendMessage(ChatColor.AQUA + "Players: " + ChatColor.WHITE + t.players + " | Bracket: " + t.bracketSize);
            if (t.start != null) p.sendMessage(ChatColor.AQUA + "Start: " + ChatColor.WHITE + formatDate(t.start));
            Match m = playerMatch;
            if (m != null && m.p1 != null && m.p2 != null) {
                String opponent = m.p1.equalsIgnoreCase(p.getUniqueId().toString()) ? m.p2Name : m.p1Name;
                String you = m.p1.equalsIgnoreCase(p.getUniqueId().toString()) ? m.p1Name : m.p2Name;
                p.sendMessage(ChatColor.YELLOW + "Your match: " + ChatColor.WHITE + you + " vs " + opponent);
                p.sendMessage(ChatColor.YELLOW + "Game: " + ChatColor.WHITE + m.nextGame + "/3" + ChatColor.GRAY + " — series " + m.w1 + "–" + m.w2);
            } else p.sendMessage(ChatColor.GRAY + "You have no currently playable tournament match.");
        }
        p.sendMessage(ChatColor.YELLOW + "--- Tournament Points ---");
        List<Row> rows = leaderboard;
        if (rows.isEmpty()) p.sendMessage(ChatColor.GRAY + "No tournament points recorded yet.");
        else { int rank=1; for(Row r:rows){ p.sendMessage(ChatColor.GRAY+"#"+rank+" "+ChatColor.WHITE+r.name+ChatColor.DARK_GRAY+" — "+ChatColor.GOLD+r.points+" pts"); rank++; } }
        p.sendMessage("");
    }

    public void showMatch(Player p) {
        if (!isEnabled()) { p.sendMessage(ChatColor.GRAY + "Tournament information is available on the hosted Monster Maze server."); return; }
        Match m = playerMatch;
        if (m == null) { p.sendMessage(ChatColor.YELLOW + "You do not currently have a playable tournament match."); refresh(); return; }
        String you = m.p1.equalsIgnoreCase(p.getUniqueId().toString()) ? m.p1Name : m.p2Name;
        String opp = m.p1.equalsIgnoreCase(p.getUniqueId().toString()) ? m.p2Name : m.p1Name;
        p.sendMessage(ChatColor.GOLD + "=== Your Tournament Match ===");
        p.sendMessage(ChatColor.WHITE + you + ChatColor.GRAY + " vs " + ChatColor.WHITE + opp);
        p.sendMessage(ChatColor.AQUA + "Game " + ChatColor.WHITE + m.nextGame + ChatColor.AQUA + "/3");
        p.sendMessage(ChatColor.AQUA + "Series: " + ChatColor.WHITE + m.w1 + "–" + m.w2);
    }

    private static Tournament parseTournament(String json) {
        if (json == null || !json.contains("\"tournament\"")) return null;
        String object = objectAfter(json, "tournament"); if (object == null || object.trim().equals("null")) return null;
        int number = integer(object,"number",0), bracket = integer(object,"bracketSize",0);
        String name=string(object,"name"), status=string(object,"status"), start=string(object,"start");
        int players=countObjects(array(object,"players"));
        if(number<1||name==null)return null;
        return new Tournament(number,name,status,start,bracket,players);
    }

    private static List<Row> parseLeaderboard(String json) {
        List<Row> out=new ArrayList<Row>(); if(json==null)return out; String a=array(json,"rows"); if(a==null)return out;
        Matcher m=Pattern.compile("\\{([^{}]*)\\}").matcher(a); while(m.find()){String o=m.group(1);String n=string(o,"name");int pts=integer(o,"score",integer(o,"points",0));if(n!=null)out.add(new Row(n,pts));} return out;
    }

    private static Match parseMatch(String json) {
        if(json==null||!json.contains("\"match\""))return null; String o=objectAfter(json,"match");if(o==null||o.trim().equals("null"))return null;
        String p1=string(o,"player1"),p2=string(o,"player2");if(p1==null||p2==null)return null;
        int w1=integer(o,"player1Wins",0),w2=integer(o,"player2Wins",0);int next=w1+w2+1;
        return new Match(p1,p2,displayName(o,"player1Name",p1),displayName(o,"player2Name",p2),w1,w2,next);
    }

    private static String displayName(String o,String key,String uuid){String s=string(o,key);return s==null?uuid.substring(0,Math.min(8,uuid.length())):s;}
    private static String objectAfter(String json,String key){Matcher m=Pattern.compile("\\\""+Pattern.quote(key)+"\\\"\\s*:\\s*(\\{.*?\\}|null)",Pattern.DOTALL).matcher(json);return m.find()?m.group(1):null;}
    private static String array(String json,String key){Matcher m=Pattern.compile("\\\""+Pattern.quote(key)+"\\\"\\s*:\\s*\\[(.*?)\\]",Pattern.DOTALL).matcher(json);return m.find()?m.group(1):null;}
    private static int countObjects(String s){if(s==null)return 0;Matcher m=Pattern.compile("\\{([^{}]*)\\}").matcher(s);int n=0;while(m.find())n++;return n;}
    private static String string(String json,String key){Matcher m=Pattern.compile("\\\""+Pattern.quote(key)+"\\\"\\s*:\\s*\\\"((?:\\\\.|[^\\\"\\\\])*)\\\"").matcher(json);return m.find()?m.group(1).replace("\\\"","\"").replace("\\\\","\\"):null;}
    private static int integer(String json,String key,int fallback){Matcher m=Pattern.compile("\\\""+Pattern.quote(key)+"\\\"\\s*:\\s*(-?\\d+)").matcher(json);if(!m.find())return fallback;try{return Integer.parseInt(m.group(1));}catch(NumberFormatException e){return fallback;}}
    private static String pretty(String s){if(s==null||s.length()==0)return"Unknown";return Character.toUpperCase(s.charAt(0))+s.substring(1);}
    private static String formatDate(String iso){return iso==null?"Unknown":iso.replace('T',' ').replace("+00:00"," UTC");}

    public static final class Tournament { public final int number,bracketSize,players;public final String name,status,start;Tournament(int n,String name,String status,String start,int b,int p){number=n;this.name=name;this.status=status;this.start=start;bracketSize=b;players=p;} }
    public static final class Match { public final String p1,p2,p1Name,p2Name;public final int w1,w2,nextGame;Match(String a,String b,String an,String bn,int x,int y,int n){p1=a;p2=b;p1Name=an;p2Name=bn;w1=x;w2=y;nextGame=n;} }
    public static final class Row { public final String name;public final int points;Row(String n,int p){name=n;points=p;} }
}
