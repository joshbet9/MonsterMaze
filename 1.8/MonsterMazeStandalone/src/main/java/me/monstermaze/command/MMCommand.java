package me.monstermaze.command;

import me.monstermaze.MonsterMazePlugin;
import me.monstermaze.game.BuildBypassListener;
import me.monstermaze.game.GameManager;
import me.monstermaze.game.GameState;
import me.monstermaze.game.MazeMode;
import me.monstermaze.kit.KitType;
import me.monstermaze.stats.ChallengeManager;
import me.monstermaze.stats.CompetitiveUI;
import me.monstermaze.stats.LeaderboardManager;
import me.monstermaze.stats.RunRecorder;
import me.monstermaze.stats.TournamentManager;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.scheduler.BukkitRunnable;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MMCommand implements CommandExecutor {
    private final MonsterMazePlugin plugin;
    private final CompetitiveUI competitiveUI;
    private final TournamentManager tournamentManager;
    public MMCommand(MonsterMazePlugin plugin) { this.plugin=plugin; this.competitiveUI=new CompetitiveUI(plugin); this.tournamentManager=new TournamentManager(plugin); }

    @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        GameManager gm=plugin.getGameManager();
        if(args.length==0){showHelp(sender,gm);return true;}
        String sub=args[0].toLowerCase();
        if(sub.equals("gui")){if(!(sender instanceof Player)){sender.sendMessage(ChatColor.RED+"Players only.");return true;}if(gm.isLive()){sender.sendMessage(ChatColor.RED+"Can't change kit mid-game.");return true;}gm.getKitManager().openSelector((Player)sender);return true;}
        if(sub.equals("stats")||sub.equals("profile")){if(!(sender instanceof Player)){sender.sendMessage(ChatColor.RED+"Players only.");return true;}competitiveUI.showStats((Player)sender);return true;}
        if(sub.equals("clb")||sub.equals("competitive")||sub.equals("competitiveleaderboard")){if(!(sender instanceof Player)){sender.sendMessage(ChatColor.RED+"Players only.");return true;}competitiveUI.showLeaderboard((Player)sender,args.length>=2?args[1]:"mmcl");return true;}
        if(sub.equals("challenge")||sub.equals("competition")){if(!(sender instanceof Player)){sender.sendMessage(ChatColor.RED+"Players only.");return true;}plugin.getChallengeManager().show((Player)sender,args.length>=2&&args[1].equalsIgnoreCase("lb"));return true;}
        if(sub.equals("tournament")||sub.equals("tourney")){if(!(sender instanceof Player)){sender.sendMessage(ChatColor.RED+"Players only.");return true;}Player p=(Player)sender;if(args.length>=2&&args[1].equalsIgnoreCase("match"))tournamentManager.showMatch(p);else tournamentManager.show(p,args.length>=2&&(args[1].equalsIgnoreCase("lb")||args[1].equalsIgnoreCase("leaderboard")));return true;}
        if(sub.equals("kit")||sub.equals("kits")){if(!(sender instanceof Player)){sender.sendMessage(ChatColor.RED+"Players only.");return true;}Player p=(Player)sender;if(sub.equals("kits")){showKits(p,gm);return true;}if(args.length<2){if(gm.isLive()){sender.sendMessage(ChatColor.RED+"Can't change kit mid-game.");return true;}gm.getKitManager().openSelector(p);return true;}KitType kit=KitType.byName(args[1]);if(kit==null&&args[1].equalsIgnoreCase("body"))kit=KitType.BODY_BUILDER;if(kit==null){sender.sendMessage(ChatColor.RED+"Unknown kit. Try /mm kits");return true;}if(gm.isLive()){sender.sendMessage(ChatColor.RED+"Can't change kit mid-game.");return true;}gm.getKitManager().setKit(p,kit);return true;}
        if(sub.equals("pb")||sub.equals("lb")||sub.equals("leaderboard")){if(!(sender instanceof Player)){sender.sendMessage(ChatColor.RED+"Players only.");return true;}Player p=(Player)sender;if(sub.equals("pb"))showPB(p);else showLeaderboard(p,args);return true;}
        if(sub.equals("exportpbs")||sub.equals("exportpb")||sub.equals("export")){if(!(sender instanceof Player)){sender.sendMessage(ChatColor.RED+"Players only.");return true;}exportPBs((Player)sender);return true;}

        if(sub.equals("void")||sub.equals("voidworld")||sub.equals("lobby")||sub.equals("stop")||sub.equals("force")||sub.equals("setcenter")||sub.equals("build")||sub.equals("buildmode")){
            if(!sender.hasPermission("monstermaze.admin")){sender.sendMessage(ChatColor.RED+"No permission.");return true;}
            if(sub.equals("build")||sub.equals("buildmode")){if(!(sender instanceof Player)){sender.sendMessage(ChatColor.RED+"Players only.");return true;}Player p=(Player)sender;if(p.hasMetadata(BuildBypassListener.METADATA)){p.removeMetadata(BuildBypassListener.METADATA,plugin);p.sendMessage(ChatColor.YELLOW+"Monster Maze build bypass "+ChatColor.RED+"DISABLED"+ChatColor.YELLOW+".");}else{p.setMetadata(BuildBypassListener.METADATA,new FixedMetadataValue(plugin,true));p.sendMessage(ChatColor.YELLOW+"Monster Maze build bypass "+ChatColor.GREEN+"ENABLED"+ChatColor.YELLOW+". You can edit the active arena in Creative.");}return true;}
            if(sub.equals("void")||sub.equals("voidworld")||sub.equals("lobby")){if(!(sender instanceof Player)){sender.sendMessage(ChatColor.RED+"Players only.");return true;}plugin.getVoidWorlds().sendToVoid((Player)sender);return true;}
            if(sub.equals("stop")||sub.equals("force")){gm.forceStop();sender.sendMessage(ChatColor.GREEN+"Monster Maze force stopped.");return true;}
            if(sub.equals("setcenter")){if(!(sender instanceof Player)){sender.sendMessage(ChatColor.RED+"Only players can set the center.");return true;}Player p=(Player)sender;gm.setCenter(p.getLocation());sender.sendMessage(ChatColor.GREEN+"Lobby box placed at "+p.getLocation().getBlockX()+", "+p.getLocation().getBlockY()+", "+p.getLocation().getBlockZ());return true;}
        }

        if(sub.equals("start")){return start(sender,args,gm);}
        if(sub.equals("mode")){if(args.length<2){sender.sendMessage(ChatColor.YELLOW+"Usage: /mm mode <original|speed|modern>");sender.sendMessage(ChatColor.AQUA+"Current: "+plugin.getMode().color+plugin.getMode().id);for(MazeMode m:MazeMode.values())sender.sendMessage(ChatColor.GRAY+" - "+m.color+m.id+ChatColor.GRAY+": "+m.description);return true;}if(gm.isRunning()){sender.sendMessage(ChatColor.RED+"Change the mode when no game is running.");return true;}MazeMode mode=MazeMode.byName(args[1]);if(mode==null){sender.sendMessage(ChatColor.RED+"Unknown mode. Try original, speed, or modern.");return true;}plugin.setMode(mode);gm.rerenderLeaderboardBoard();sender.sendMessage(ChatColor.GREEN+"Mode set to "+mode.color+mode.id+ChatColor.GREEN+". It will apply on the next /mm start.");return true;}
        if(sub.equals("pattern")||sub.equals("maze")){if(args.length<2){sender.sendMessage(ChatColor.YELLOW+"Usage: /mm pattern <1|2|3|random>");sender.sendMessage(ChatColor.GRAY+"Current: "+formatPattern(gm.getMazeGenerator().getForcedPattern()));return true;}if(gm.isRunning()){sender.sendMessage(ChatColor.RED+"Change the pattern when no game is running.");return true;}String pa=args[1].toLowerCase();if(pa.equals("random")){gm.getMazeGenerator().setForcedPattern(-1);sender.sendMessage(ChatColor.GREEN+"Maze pattern set to random for the next game.");return true;}try{int pattern=Integer.parseInt(pa);if(pattern<1||pattern>3)throw new NumberFormatException();gm.getMazeGenerator().setForcedPattern(pattern-1);sender.sendMessage(ChatColor.GREEN+"Maze pattern set to "+ChatColor.WHITE+pattern+ChatColor.GREEN+" for the next game.");}catch(NumberFormatException e){sender.sendMessage(ChatColor.RED+"Invalid pattern. Use 1, 2, 3, or random.");}return true;}
        if(sub.equals("map")||sub.equals("arena")){if(args.length<2){sender.sendMessage(ChatColor.YELLOW+"Usage: /mm map <name>");sender.sendMessage(ChatColor.GRAY+"Available: "+String.join(", ",plugin.getMapManager().knownMaps()));return true;}if(gm.isRunning()){sender.sendMessage(ChatColor.RED+"Change the map when no game is running.");return true;}List<Player> lobbyPlayers=gm.getState()==GameState.IDLE?findLobbyPlayers(gm.getLobbySpawn()):new ArrayList<Player>();String want=args[1].toLowerCase();if(!plugin.getMapManager().setActiveMap(want)){sender.sendMessage(ChatColor.RED+"Unknown map '"+args[1]+"'. Try: "+String.join(", ",plugin.getMapManager().knownMaps()));return true;}plugin.getMapManager().ensureActiveWorld();gm.applyMap();for(Player p:lobbyPlayers)gm.sendToLobby(p);sender.sendMessage(ChatColor.GREEN+"Map set to "+ChatColor.WHITE+want+ChatColor.GREEN+". Lobby moved; run /mm start.");return true;}
        if(sub.equals("status")){sender.sendMessage(ChatColor.AQUA+"State: "+ChatColor.WHITE+gm.getState());sender.sendMessage(ChatColor.AQUA+"Stage: "+ChatColor.WHITE+gm.getStage());sender.sendMessage(ChatColor.AQUA+"Alive: "+ChatColor.WHITE+gm.getAlivePlayers().size());sender.sendMessage(ChatColor.AQUA+"Map: "+ChatColor.WHITE+plugin.getMapManager().getActiveMap());sender.sendMessage(ChatColor.AQUA+"Next pattern: "+ChatColor.WHITE+formatPattern(gm.getMazeGenerator().getForcedPattern()));return true;}
        sender.sendMessage(ChatColor.RED+"Unknown command. Use /mm for help.");return true;
    }

    private boolean start(CommandSender sender,String[] args,GameManager gm){
        if(gm.getState()!=GameState.IDLE&&gm.getState()!=GameState.ENDING){sender.sendMessage(ChatColor.RED+"Game already running ("+gm.getState()+").");return true;}
        if(args.length>=2&&(args[1].equalsIgnoreCase("challenge")||args[1].equalsIgnoreCase("mmr"))){
            if(!(sender instanceof Player)){sender.sendMessage(ChatColor.RED+"Players only.");return true;}
            final Player p=(Player)sender;
            if(args[1].equalsIgnoreCase("challenge")){
                ChallengeManager.Challenge c=plugin.getChallengeManager().getChallenge();
                if(c==null){sender.sendMessage(ChatColor.YELLOW+"Weekly challenge is still loading...");plugin.getChallengeManager().refresh();return true;}
                MazeMode mode=MazeMode.byName(c.mode);KitType kit=KitType.byName(c.kit);
                if(mode==null||kit==null){sender.sendMessage(ChatColor.RED+"The hosted challenge configuration is invalid.");return true;}
                plugin.setMode(mode);gm.getMazeGenerator().setForcedPattern(c.pattern);gm.getKitManager().setKit(p,kit);gm.startGame(p.getLocation());sender.sendMessage(ChatColor.GREEN+"Starting Weekly Challenge #"+c.number+"...");return true;
            }
            startMMR(p,gm);
            return true;
        }
        if(args.length>=2){try{int pattern=Integer.parseInt(args[1]);if(pattern<1||pattern>3)throw new NumberFormatException();gm.getMazeGenerator().setForcedPattern(pattern-1);}catch(NumberFormatException e){sender.sendMessage(ChatColor.RED+"Invalid maze pattern. Use 1, 2, 3, challenge, or mmr.");return true;}}
        if(sender instanceof Player)gm.startGame(((Player)sender).getLocation());else gm.startGame();sender.sendMessage(ChatColor.GREEN+"Starting Monster Maze...");return true;
    }

    private void startMMR(final Player player, final GameManager gm){
        if(!plugin.getBackendClient().isEnabled()){player.sendMessage(ChatColor.RED+"MMR start is unavailable because the competitive backend is not configured.");return;}
        player.sendMessage(ChatColor.GRAY+"Finding your weakest MMR configuration...");
        new BukkitRunnable(){
            @Override public void run(){
                try{
                    String json=plugin.getBackendClient().get("/api/v1/mmr/player/"+player.getUniqueId()+"/next/1.8");
                    final MMRTarget target=parseMMRTarget(json);
                    new BukkitRunnable(){
                        @Override public void run(){
                            if(target==null){player.sendMessage(ChatColor.YELLOW+"No eligible MMR configuration was found.");return;}
                            if(gm.getState()!=GameState.IDLE&&gm.getState()!=GameState.ENDING){player.sendMessage(ChatColor.RED+"The game started before your MMR target was loaded.");return;}
                            MazeMode mode=MazeMode.byName(target.mode);KitType kit=KitType.byName(target.kit);
                            if(mode==null||kit==null){player.sendMessage(ChatColor.RED+"The backend returned an invalid MMR configuration.");return;}
                            plugin.setMode(mode);
                            gm.getMazeGenerator().setForcedPattern(target.pattern);
                            gm.getKitManager().setKit(player,kit);
                            gm.startGame(player.getLocation());
                            player.sendMessage(ChatColor.GREEN+"Starting your weakest MMR configuration: "+mode.id+" / Maze "+(target.pattern+1)+" / "+kit.display+ChatColor.GRAY+" ("+formatPercent(target.percentage)+" complete)");
                        }
                    }.runTask(plugin);
                }catch(Exception e){new BukkitRunnable(){@Override public void run(){player.sendMessage(ChatColor.RED+"Could not load your MMR target right now. Please try again.");}}.runTask(plugin);}
            }
        }.runTaskAsynchronously(plugin);
    }

    private String formatPercent(double percentage){return String.format(java.util.Locale.US,"%.1f%%",percentage);}
    private MMRTarget parseMMRTarget(String json){
        if(json==null||json.length()==0||json.indexOf("\"target\"")<0)return null;
        String mode=parseJsonString(json,"mode");String kit=parseJsonString(json,"kit");Integer pattern=parseJsonInt(json,"pattern");Double percentage=parseJsonDouble(json,"percentage");
        if(mode==null||kit==null||pattern==null||percentage==null)return null;
        return new MMRTarget(mode,kit,pattern,percentage);
    }
    private String parseJsonString(String json,String key){Matcher m=Pattern.compile("\\\""+Pattern.quote(key)+"\\\"\\s*:\\s*\\\"([^\\\"]+)\\\"").matcher(json);return m.find()?m.group(1):null;}
    private Integer parseJsonInt(String json,String key){Matcher m=Pattern.compile("\\\""+Pattern.quote(key)+"\\\"\\s*:\\s*(-?\\d+)").matcher(json);return m.find()?Integer.valueOf(m.group(1)):null;}
    private Double parseJsonDouble(String json,String key){Matcher m=Pattern.compile("\\\""+Pattern.quote(key)+"\\\"\\s*:\\s*(-?\\d+(?:\\.\\d+)?)").matcher(json);return m.find()?Double.valueOf(m.group(1)):null;}
    private static class MMRTarget{final String mode;final String kit;final int pattern;final double percentage;MMRTarget(String mode,String kit,int pattern,double percentage){this.mode=mode;this.kit=kit;this.pattern=pattern;this.percentage=percentage;}}

    private void showHelp(CommandSender sender,GameManager gm){
        sender.sendMessage(ChatColor.GOLD+"=== Monster Maze ===");
        sender.sendMessage(ChatColor.YELLOW+"Player Commands");
        sender.sendMessage(ChatColor.WHITE+"/mm start [1|2|3]"+ChatColor.GRAY+" - start a run");
        sender.sendMessage(ChatColor.WHITE+"/mm start challenge"+ChatColor.GRAY+" - play this week's challenge");
        sender.sendMessage(ChatColor.WHITE+"/mm start mmr"+ChatColor.GRAY+" - start your weakest MMR configuration");
        sender.sendMessage(ChatColor.WHITE+"/mm map <name>"+ChatColor.GRAY+" - choose the map");
        sender.sendMessage(ChatColor.WHITE+"/mm pattern <1|2|3|random>"+ChatColor.GRAY+" - choose the maze");
        sender.sendMessage(ChatColor.WHITE+"/mm mode <original|speed|modern>"+ChatColor.GRAY+" - choose the mode");
        sender.sendMessage(ChatColor.WHITE+"/mm gui"+ChatColor.GRAY+" - open kit selector");
        sender.sendMessage(ChatColor.WHITE+"/mm kit <kit>"+ChatColor.GRAY+" - choose a kit");
        sender.sendMessage(ChatColor.WHITE+"/mm kits"+ChatColor.GRAY+" - list kits");
        sender.sendMessage(ChatColor.WHITE+"/mm status"+ChatColor.GRAY+" - current server state");
        sender.sendMessage(ChatColor.WHITE+"/mm pb"+ChatColor.GRAY+" - personal bests");
        sender.sendMessage(ChatColor.WHITE+"/mm lb [1|2|3]"+ChatColor.GRAY+" - leaderboard");
        sender.sendMessage(ChatColor.WHITE+"/mm stats"+ChatColor.GRAY+" - competitive stats");
        sender.sendMessage(ChatColor.WHITE+"/mm clb [mmcl|mmr|elo|weekly|tournament]"+ChatColor.GRAY+" - competitive leaderboard");
        sender.sendMessage(ChatColor.WHITE+"/mm challenge [lb]"+ChatColor.GRAY+" - weekly challenge");
        sender.sendMessage(ChatColor.WHITE+"/mm tournament [match|lb]"+ChatColor.GRAY+" - tournament info");
        if(sender.hasPermission("monstermaze.admin")){sender.sendMessage(ChatColor.RED+"Admin Commands");sender.sendMessage(ChatColor.RED+"/mm stop | /mm force | /mm setcenter | /mm void | /mm build");}
        sender.sendMessage(ChatColor.AQUA+"Current: "+plugin.getMode().color+plugin.getMode().id+ChatColor.GRAY+" | Map: "+plugin.getMapManager().getActiveMap()+ChatColor.GRAY+" | Next: "+formatPattern(gm.getMazeGenerator().getForcedPattern()));
    }

    private List<Player> findLobbyPlayers(Location lobby){List<Player> players=new ArrayList<Player>();if(lobby==null||lobby.getWorld()==null)return players;for(Player p:org.bukkit.Bukkit.getOnlinePlayers()){if(p.getGameMode()==org.bukkit.GameMode.SPECTATOR)continue;if(p.getWorld()!=lobby.getWorld())continue;if(p.getLocation().distanceSquared(lobby)<=64*64)players.add(p);}return players;}
    private void showKits(Player p,GameManager gm){p.sendMessage(ChatColor.GOLD+"Kits:");boolean qol=plugin.getMode()!=MazeMode.ORIGINAL;for(KitType k:KitType.available(qol))p.sendMessage(ChatColor.GRAY+" - "+k.display+ChatColor.DARK_GRAY+" (/mm kit "+k.name().toLowerCase()+")");}
    private String formatPattern(int pattern){return pattern<0?"random":"Maze "+(pattern+1);}
    private void exportPBs(Player p){if(!plugin.isSoloMode()){p.sendMessage(ChatColor.RED+"PB export is only available in Solo Mode.");return;}LeaderboardManager lb=plugin.getLeaderboards();RunRecorder recorder=plugin.getRunRecorder();int exported=0;for(MazeMode mode:MazeMode.values())for(int pat=0;pat<LeaderboardManager.PATTERN_COUNT;pat++)for(KitType kit:KitType.values()){int stage=lb.getKitPB(mode,pat,p.getUniqueId(),kit.id);if(stage<1)continue;if(recorder.recordHistorical(p,mode,pat,kit.id,stage))exported++;}if(exported==0)p.sendMessage(ChatColor.YELLOW+"No stored personal bests found to export.");else{p.sendMessage(ChatColor.GREEN+"Exported "+exported+" stored personal best(s) for submission.");p.sendMessage(ChatColor.GRAY+"Run the Solo submitter to send them to Discord.");}}
    private void showPB(Player p){LeaderboardManager lb=plugin.getLeaderboards();MazeMode mode=plugin.getMode();p.sendMessage(ChatColor.GOLD+"=== Personal Bests ("+mode.color+mode.id+ChatColor.GOLD+") ===");boolean any=false;for(int pat=0;pat<LeaderboardManager.PATTERN_COUNT;pat++){LeaderboardManager.PBInfo best=lb.getBest(mode,pat,p.getUniqueId());if(best!=null)any=true;p.sendMessage(ChatColor.YELLOW+LeaderboardManager.patternName(pat)+":"+(best!=null?ChatColor.WHITE+" Stage "+best.stage+kitSuffix(best.kit):ChatColor.GRAY+" no PB yet"));if(best!=null)for(KitType k:KitType.available(mode!=MazeMode.ORIGINAL)){int per=lb.getKitPB(mode,pat,p.getUniqueId(),k.id);if(per>0)p.sendMessage("   "+k.display+ChatColor.GRAY+": "+ChatColor.WHITE+"Stage "+per);}}if(!any)p.sendMessage(ChatColor.GRAY+"Play a game to set a personal best!");}
    private void showLeaderboard(Player p,String[] args){LeaderboardManager lb=plugin.getLeaderboards();MazeMode mode=plugin.getMode();Integer want=null;if(args.length>=2)try{int v=Integer.parseInt(args[1]);if(v>=1&&v<=LeaderboardManager.PATTERN_COUNT)want=v-1;}catch(NumberFormatException ignored){}int start=want!=null?want:0,end=want!=null?want+1:LeaderboardManager.PATTERN_COUNT;for(int pat=start;pat<end;pat++){p.sendMessage("");p.sendMessage(ChatColor.GOLD+"=== "+LeaderboardManager.patternName(pat)+" ("+mode.color+mode.id+ChatColor.GOLD+") ===");List<LeaderboardManager.Entry> rows=lb.getLeaderboard(mode,pat,10);if(rows.isEmpty()){p.sendMessage(ChatColor.GRAY+"No scores yet.");continue;}int rank=1;for(LeaderboardManager.Entry e:rows){p.sendMessage(ChatColor.GRAY+"#"+rank+" "+ChatColor.WHITE+e.name+ChatColor.DARK_GRAY+" — Stage "+ChatColor.GOLD+e.stage+kitSuffix(e.kit));rank++;}}}
    private String kitSuffix(String kitId){if(kitId==null||kitId.isEmpty())return"";KitType k=KitType.byName(kitId);return ChatColor.DARK_GRAY+" ("+(k!=null?k.display:kitId)+ChatColor.DARK_GRAY+")";}
}
