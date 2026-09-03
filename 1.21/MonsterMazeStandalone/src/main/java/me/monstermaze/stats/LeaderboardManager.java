package me.monstermaze.stats;

import me.monstermaze.MonsterMazePlugin;
import me.monstermaze.game.MazeMode;
import me.monstermaze.kit.KitType;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.scheduler.BukkitRunnable;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Backend-authoritative PB/leaderboard mirror with local fallback. */
public class LeaderboardManager {
    public static final int PATTERN_COUNT = 3;
    private final MonsterMazePlugin plugin;
    private final File file;
    private YamlConfiguration data;
    private final BackendClient backend;
    private volatile boolean remoteReady;
    private volatile Map<String,List<OverallEntry>> overall=new HashMap<String,List<OverallEntry>>();
    private volatile Map<String,Map<String,List<OverallEntry>>> kits=new HashMap<String,Map<String,List<OverallEntry>>>();
    private volatile Map<String,Map<Integer,Map<String,PBInfo>>> pbs=new HashMap<String,Map<Integer,Map<String,PBInfo>>>();
    private static final Pattern ROW=Pattern.compile("\\{\\\"name\\\":\\\"((?:\\\\.|[^\\\"\\\\])*)\\\",\\\"kit\\\":\\\"((?:\\\\.|[^\\\"\\\\])*)\\\",\\\"stage\\\":(\\d+)\\}");
    private static final Pattern PB=Pattern.compile("\\{\\\"pattern\\\":(\\d+),\\\"kit\\\":\\\"((?:\\\\.|[^\\\"\\\\])*)\\\",\\\"stage\\\":(\\d+),\\\"timeMs\\\":(\\d+)\\}");
    public static class PBInfo { public final int stage; public final String kit; public PBInfo(int s,String k){stage=s;kit=k;} }
    public static class Entry { public final String name; public final int stage; public final String kit; public Entry(String n,int s,String k){name=n;stage=s;kit=k;} }
    public static class OverallEntry { public final String name; public final int stage; public final String kit; public final int pattern; public OverallEntry(String n,int s,String k,int p){name=n;stage=s;kit=k;pattern=p;} }
    public LeaderboardManager(MonsterMazePlugin plugin){this.plugin=plugin;this.file=new File(plugin.getDataFolder(),"leaderboards.yml");this.backend=plugin.getBackendClient();reload();if(backend!=null&&backend.isEnabled()){Bukkit.getScheduler().runTaskLater(plugin,new Runnable(){public void run(){refreshFromBackend();}},20L);Bukkit.getScheduler().runTaskTimer(plugin,new Runnable(){public void run(){refreshFromBackend();}},600L,600L);}}
    public void reload(){data=YamlConfiguration.loadConfiguration(file);}
    private static String modeKey(MazeMode m){return m.id.toLowerCase();} private static String patternKey(int p){return "pattern"+p;}
    public static String patternName(int p){return "Maze "+(p+1);}
    public void refreshFromBackend(){if(backend==null||!backend.isEnabled())return;new BukkitRunnable(){public void run(){try{Map<String,List<OverallEntry>> o=new HashMap<String,List<OverallEntry>>();Map<String,Map<String,List<OverallEntry>>> k=new HashMap<String,Map<String,List<OverallEntry>>>();Map<String,Map<Integer,Map<String,PBInfo>>> pbs2=new HashMap<String,Map<Integer,Map<String,PBInfo>>>();for(MazeMode m:MazeMode.values()){String mk=modeKey(m);o.put(mk,parseBoard(backend.get("/api/v1/leaderboard/1.21/"+mk+"/overall")));Map<String,List<OverallEntry>> km=new HashMap<String,List<OverallEntry>>();for(KitType kit:KitType.values())km.put(kit.id,parseBoard(backend.get("/api/v1/leaderboard/1.21/"+mk+"/kit/"+kit.id)));k.put(mk,km);for(org.bukkit.entity.Player pl:Bukkit.getOnlinePlayers())pbs2.put(mk+"|"+pl.getUniqueId(),parsePB(backend.get("/api/v1/pb/1.21/"+mk+"/"+pl.getUniqueId())));}overall=o;kits=k;pbs=pbs2;remoteReady=true;plugin.getLogger().fine("Monster Maze backend leaderboards/PBs refreshed.");}catch(Exception e){plugin.getLogger().warning("Backend leaderboard sync failed: "+e.getMessage());}}}.runTaskAsynchronously(plugin);}
    private static List<OverallEntry> parseBoard(String json){List<OverallEntry> out=new ArrayList<OverallEntry>();if(json==null)return out;Matcher m=ROW.matcher(json);while(m.find())out.add(new OverallEntry(unescape(m.group(1)),Integer.parseInt(m.group(3)),unescape(m.group(2)),-1));return out;}
    private static Map<Integer,Map<String,PBInfo>> parsePB(String json){Map<Integer,Map<String,PBInfo>> out=new HashMap<Integer,Map<String,PBInfo>>();if(json==null)return out;Matcher m=PB.matcher(json);while(m.find()){int p=Integer.parseInt(m.group(1));Map<String,PBInfo> km=out.get(p);if(km==null){km=new HashMap<String,PBInfo>();out.put(p,km);}String kit=unescape(m.group(2));int s=Integer.parseInt(m.group(3));PBInfo old=km.get(kit);if(old==null||s>old.stage)km.put(kit,new PBInfo(s,kit));}return out;}
    private static String unescape(String s){return s==null?"":s.replace("\\\"","\"").replace("\\\\","\\").replace("\\n","\n").replace("\\r","\r").replace("\\t","\t");}
    private Map<Integer,Map<String,PBInfo>> remotePlayer(MazeMode m,UUID u){return pbs.get(modeKey(m)+"|"+u);}
    public int getKitPB(MazeMode m,int p,UUID u,String kit){if(kit==null)return 0;if(remoteReady){Map<Integer,Map<String,PBInfo>> x=remotePlayer(m,u);if(x!=null){Map<String,PBInfo> y=x.get(p);if(y!=null){PBInfo i=y.get(kit);if(i!=null)return i.stage;for(Map.Entry<String,PBInfo> e:y.entrySet())if(e.getKey().equalsIgnoreCase(kit))return e.getValue().stage;}}}ConfigurationSection ps=playerSection(m,p,u);return ps==null?0:ps.getInt(kit,0);}
    public int getPB(MazeMode m,int p,UUID u){PBInfo b=getBest(m,p,u);return b==null?0:b.stage;}
    public PBInfo getBest(MazeMode m,int p,UUID u){if(remoteReady){Map<Integer,Map<String,PBInfo>> x=remotePlayer(m,u);if(x!=null){Map<String,PBInfo> y=x.get(p);if(y!=null){PBInfo b=null;for(PBInfo i:y.values())if(b==null||i.stage>b.stage)b=i;if(b!=null)return b;}}}ConfigurationSection ps=playerSection(m,p,u);if(ps==null)return null;String bk=null;int bs=0;for(String k:ps.getKeys(false)){int s=ps.getInt(k,0);if(s>bs){bs=s;bk=k;}}return bs<1?null:new PBInfo(bs,bk);}
    public void recordRun(MazeMode m,int p,UUID u,int s,String kit){if(backend!=null&&backend.isEnabled())return;if(u==null||p<0||p>=PATTERN_COUNT||s<1||kit==null||kit.isEmpty())return;if(s<=getKitPB(m,p,u,kit))return;data.set(modeKey(m)+"."+patternKey(p)+"."+u+"."+kit,s);save();}
    public void recordRun(MazeMode m,int p,UUID u,int s,KitType kit){if(kit!=null)recordRun(m,p,u,s,kit.id);}
    public List<Entry> getLeaderboard(MazeMode m,int p,int limit){reload();Map<Integer,List<Map.Entry<UUID,String>>> by=new TreeMap<Integer,List<Map.Entry<UUID,String>>(Collections.reverseOrder());ConfigurationSection ms=data.getConfigurationSection(modeKey(m));if(ms!=null){ConfigurationSection ps=ms.getConfigurationSection(patternKey(p));if(ps!=null)for(String key:ps.getKeys(false)){UUID u;try{u=UUID.fromString(key);}catch(Exception e){continue;}ConfigurationSection x=ps.getConfigurationSection(key);if(x==null)continue;int bs=0;String bk=null;for(String k:x.getKeys(false)){int s=x.getInt(k,0);if(s>bs){bs=s;bk=k;}}if(bs>0){List<Map.Entry<UUID,String>> b=by.get(bs);if(b==null){b=new ArrayList<Map.Entry<UUID,String>>();by.put(bs,b);}b.add(new AbstractMap.SimpleEntry<UUID,String>(u,bk));}}}List<Entry> out=new ArrayList<Entry>();for(Map.Entry<Integer,List<Map.Entry<UUID,String>>> b:by.entrySet())for(Map.Entry<UUID,String> e:b.getValue()){if(out.size()>=limit)return out;out.add(new Entry(displayName(e.getKey()),b.getKey(),e.getValue()));}return out;}
    public List<OverallEntry> getModeLeaderboard(MazeMode m,int limit){if(remoteReady){List<OverallEntry> r=overall.get(modeKey(m));if(r!=null)return new ArrayList<OverallEntry>(r.subList(0,Math.min(limit,r.size())));}return localModeLeaderboard(m,limit,null);}
    public List<OverallEntry> getModeAndKitLeaderboard(MazeMode m,KitType kit,int limit){if(kit==null)return getModeLeaderboard(m,limit);if(remoteReady){Map<String,List<OverallEntry>> km=kits.get(modeKey(m));if(km!=null){List<OverallEntry> r=km.get(kit.id);if(r!=null)return new ArrayList<OverallEntry>(r.subList(0,Math.min(limit,r.size())));}}return localModeLeaderboard(m,limit,kit);}
    private List<OverallEntry> localModeLeaderboard(MazeMode m,int limit,KitType filter){reload();Map<UUID,OverallEntry> best=new HashMap<UUID,OverallEntry>();ConfigurationSection ms=data.getConfigurationSection(modeKey(m));if(ms!=null)for(String pk:ms.getKeys(false)){ConfigurationSection ps=ms.getConfigurationSection(pk);Integer p=parsePatternKey(pk);if(ps==null||p==null)continue;for(String key:ps.getKeys(false)){UUID u;try{u=UUID.fromString(key);}catch(Exception e){continue;}ConfigurationSection x=ps.getConfigurationSection(key);if(x==null)continue;int bs=0;String bk=null;for(String k:x.getKeys(false)){if(filter!=null&&!k.equalsIgnoreCase(filter.id)&&!k.equalsIgnoreCase(filter.name()))continue;int s=x.getInt(k,0);if(s>bs){bs=s;bk=k;}}if(bs>0){OverallEntry old=best.get(u);if(old==null||bs>old.stage)best.put(u,new OverallEntry(displayName(u),bs,bk,p));}}}List<OverallEntry> out=new ArrayList<OverallEntry>(best.values());Collections.sort(out,(a,b)->b.stage-a.stage);return out.size()>limit?new ArrayList<OverallEntry>(out.subList(0,limit)):out;}
    private Integer parsePatternKey(String k){if(k==null||!k.startsWith("pattern"))return null;try{return Integer.valueOf(k.substring(7));}catch(Exception e){return null;}}
    private ConfigurationSection playerSection(MazeMode m,int p,UUID u){ConfigurationSection ms=data.getConfigurationSection(modeKey(m));if(ms==null)return null;ConfigurationSection ps=ms.getConfigurationSection(patternKey(p));return ps==null?null:ps.getConfigurationSection(u.toString());}
    private String displayName(UUID u){String n=Bukkit.getOfflinePlayer(u).getName();return n!=null&&!n.isEmpty()?n:u.toString().substring(0,6);}
    private void save(){try{data.save(file);}catch(IOException e){plugin.getLogger().warning("Could not save leaderboards.yml: "+e.getMessage());}}
}
