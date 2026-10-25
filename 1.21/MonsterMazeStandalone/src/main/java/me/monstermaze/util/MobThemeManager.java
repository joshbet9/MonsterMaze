package me.monstermaze.util;
import me.monstermaze.MonsterMazePlugin;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.*;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import java.io.File;
import java.io.IOException;
import java.util.*;

public final class MobThemeManager implements Listener {
    public static final String MOB_METADATA = "monstermaze_mob";
    private static final int SLOT = 6;
    private static final List<String> THEMES = Collections.unmodifiableList(Arrays.asList(
        "map_default","creeper","skeleton","spider","zombie","slime","zombie_pigman","enderman","cave_spider",
        "silverfish","blaze","magma_cube","bat","witch","endermite","guardian","pig","sheep","cow",
        "chicken","squid","wolf","mooshroom","snowman","ocelot","horse","rabbit","villager","random"));
    private final MonsterMazePlugin plugin;

    private final File file;
    private final Map<UUID,String> choices = new LinkedHashMap<UUID,String>();
    private final Map<String,PadVisual> pads = new LinkedHashMap<String,PadVisual>();
    private boolean warnedMissingDisguise;
    public MobThemeManager(MonsterMazePlugin plugin) {
        this.plugin=plugin; this.file=new File(plugin.getDataFolder(),"player-mob-themes.yml"); load();
        Bukkit.getPluginManager().registerEvents(this,plugin);
    }
    public String selected(Player player) {
        if(player==null)return defaultTheme();
        String s=choices.get(player.getUniqueId()); return normalize(s==null?"map_default":s);
    }
    public String forViewer(Player viewer, UUID entityId) {
        String s=selected(viewer); if("map_default".equals(s))s=defaultTheme(); return "random".equals(s)?randomConcrete(viewer.getUniqueId(),entityId):s;
    }
    public void giveSelector(Player p) {
        if(p==null||!p.isOnline())return;
        String choice=selected(p),iconTheme="map_default".equals(choice)?defaultTheme():choice;
        Material icon=selectorIcon(iconTheme);ItemStack item=new ItemStack(icon==null?Material.PAPER:icon);
        ItemMeta meta=item.getItemMeta();
        meta.setDisplayName(ChatColor.LIGHT_PURPLE+"Mob Theme: "+ChatColor.YELLOW+displayName(choice));
        meta.setLore(Arrays.asList(ChatColor.GRAY+"Right-click to cycle mob themes",ChatColor.GRAY+"Uses vanilla mob heads or spawn eggs",ChatColor.GRAY+"Map Default uses the map's normal mob and colours."));
        item.setItemMeta(meta);p.getInventory().setItem(SLOT,item);p.updateInventory();
    }
    private static Material selectorIcon(String theme){
        if("random".equals(theme))return Material.NETHER_STAR;
        if("creeper".equals(theme))return Material.CREEPER_HEAD;
        if("skeleton".equals(theme))return Material.SKELETON_SKULL;
        if("zombie".equals(theme))return Material.ZOMBIE_HEAD;
        if("snowman".equals(theme))return Material.PUMPKIN;
        return spawnEggMaterial(theme);
    }
    private static Material spawnEggMaterial(String theme){
        String id=theme==null?"creeper":theme.toLowerCase(Locale.ROOT);
        if("zombie_pigman".equals(id))id="zombified_piglin";
        if("snowman".equals(id))id="snow_golem";
        Material egg=Material.matchMaterial(id.toUpperCase(Locale.ROOT)+"_SPAWN_EGG");
        if(egg==null)egg=Material.PIG_SPAWN_EGG;
        return egg;
    }
    public void registerSafePad(Location center) {
        if(center==null||center.getWorld()==null)return;
        pads.put(key(center),new PadVisual(center.clone(),center.getBlockY()-1)); refreshSafePad(center);
    }
    public void setSafePadBeaconActive(Location center,boolean active) {
        // Beacon beams retain vanilla Minecraft colouring; only the pad terracotta is themed.
        refreshSafePad(center);
    }
    public void refreshSafePad(Location center) {
        PadVisual pad=center==null?null:pads.get(key(center)); if(pad==null)return;
        for(Player player:Bukkit.getOnlinePlayers())if(visibleTo(player,pad))paintPad(player,pad);
    }
    public void refreshPlayerPads(Player player) {
        if(player==null||!player.isOnline())return;
        for(PadVisual p:pads.values())if(visibleTo(player,p))paintPad(player,p);
    }
    public void unregisterSafePad(Location center) {
        if(center==null)return; PadVisual pad=pads.remove(key(center)); if(pad==null||pad.center.getWorld()==null)return;
        World w=pad.center.getWorld(); int cx=pad.center.getBlockX(),cz=pad.center.getBlockZ();
        for(Player p:Bukkit.getOnlinePlayers())if(visibleTo(p,pad)) {
            for(int x=-2;x<=2;x++)for(int z=-2;z<=2;z++){Location at=new Location(w,cx+x,pad.surfaceY,cz+z);p.sendBlockChange(at,w.getBlockAt(at).getBlockData().clone());}
            Location tint=new Location(w,cx,pad.surfaceY+3,cz);p.sendBlockChange(tint,w.getBlockAt(tint).getBlockData().clone());
        }
    }
    public void shutdown(){save();}
    @EventHandler public void onThemeClick(PlayerInteractEvent e) {
        if(e.getHand()!=EquipmentSlot.HAND)return;
        Action a=e.getAction(); if(a!=Action.RIGHT_CLICK_AIR&&a!=Action.RIGHT_CLICK_BLOCK)return;
        Player p=e.getPlayer(); if(p.getInventory().getHeldItemSlot()!=SLOT||!isSelector(p.getInventory().getItemInMainHand()))return;
        e.setCancelled(true); String c=selected(p); choices.put(p.getUniqueId(),THEMES.get((THEMES.indexOf(c)+1)%THEMES.size()));
        save();giveSelector(p);plugin.refreshPlayerMobThemes(p);refreshPlayerPads(p);
        if(!plugin.hasPerPlayerMobDisguises()&&!warnedMissingDisguise){p.sendMessage(ChatColor.RED+"Mob appearance changes require ProtocolLib; Safe Pad and beacon colours still update.");warnedMissingDisguise=true;}
        p.sendMessage(ChatColor.LIGHT_PURPLE+"Mob theme: "+ChatColor.YELLOW+displayName(selected(p))+ChatColor.GRAY+" (personal view).");
    }
    @EventHandler(priority=org.bukkit.event.EventPriority.MONITOR)
    public void refreshTintAfterPadInteraction(final PlayerInteractEvent e) {
        if(e.getHand()!=EquipmentSlot.HAND||e.getAction()!=Action.RIGHT_CLICK_BLOCK||e.getClickedBlock()==null)return;
        final Block clicked=e.getClickedBlock();
        final Player player=e.getPlayer();
        PadVisual matched=null;
        for(PadVisual candidate:pads.values()) {
            if(candidate.center.getWorld().equals(clicked.getWorld())
                    && clicked.getY()==candidate.surfaceY
                    && Math.abs(clicked.getX()-candidate.center.getBlockX())<=2
                    && Math.abs(clicked.getZ()-candidate.center.getBlockZ())<=2) {
                matched=candidate;
                break;
            }
        }
        if(matched==null)return;
        final PadVisual pad=matched;
        for(final long delay:new long[]{1L,3L,6L,10L}) {
            Bukkit.getScheduler().runTaskLater(plugin,new Runnable(){@Override public void run(){
                if(player.isOnline()&&pads.get(key(pad.center))==pad&&visibleTo(player,pad))paintPad(player,pad);
            }},delay);
        }
    }
    @EventHandler public void onInventoryClick(InventoryClickEvent e) {
        if(!(e.getWhoClicked() instanceof Player))return; Player p=(Player)e.getWhoClicked();
        if(isSelector(e.getCurrentItem())||isSelector(e.getCursor())||e.getHotbarButton()==SLOT
                ||(e.getClickedInventory()==p.getInventory()&&e.getSlot()==SLOT))e.setCancelled(true);
    }
    @EventHandler public void onDrop(PlayerDropItemEvent e){if(isSelector(e.getItemDrop().getItemStack()))e.setCancelled(true);}
    @EventHandler public void onJoin(PlayerJoinEvent e){laterRefresh(e.getPlayer());}
    @EventHandler public void onWorldChange(PlayerChangedWorldEvent e){laterRefresh(e.getPlayer());}
    @EventHandler public void onTeleport(PlayerTeleportEvent e){laterRefresh(e.getPlayer());}
    @EventHandler public void onMove(PlayerMoveEvent e) {
        Location a=e.getFrom(),b=e.getTo(); if(b==null)return;
        if(a.getWorld().equals(b.getWorld())&&(a.getBlockX()>>4)==(b.getBlockX()>>4)&&(a.getBlockZ()>>4)==(b.getBlockZ()>>4))return;
        laterRefresh(e.getPlayer());
    }
    private void laterRefresh(final Player p){Bukkit.getScheduler().runTaskLater(plugin,new Runnable(){public void run(){if(p.isOnline())refreshPlayerPads(p);}},2L);}
    private void paintPad(Player p,PadVisual pad) {
        World w=pad.center.getWorld();int cx=pad.center.getBlockX(),cz=pad.center.getBlockZ();
        BlockData tint=padMaterial(p).createBlockData();boolean mapDefault="map_default".equals(selected(p));
        for(int x=-2;x<=2;x++)for(int z=-2;z<=2;z++){
            Location at=new Location(w,cx+x,pad.surfaceY,cz+z);Block real=w.getBlockAt(at);BlockData visual=real.getBlockData().clone();
            if(!mapDefault&&real.getType()==Material.LIME_TERRACOTTA)visual=tint.clone();p.sendBlockChange(at,visual);
        }
        // Clear any prior overlay left at the upper visual marker.
        Location above=new Location(w,cx,pad.surfaceY+3,cz);Block actual=w.getBlockAt(above);p.sendBlockChange(above,actual.getBlockData().clone());
    }
    private Material padMaterial(Player p){Material m=Material.matchMaterial(color(palette(p))+"_TERRACOTTA");return m==null?Material.LIME_TERRACOTTA:m;}

    private boolean visibleTo(Player p,PadVisual d){if(p==null||!p.isOnline()||d.center.getWorld()==null||!p.getWorld().equals(d.center.getWorld()))return false;Location l=p.getLocation();return Math.abs(l.getX()-d.center.getX())<=192&&Math.abs(l.getZ()-d.center.getZ())<=192;}
    private String palette(Player p){String s=selected(p);if("map_default".equals(s))return s;return "random".equals(s)?randomConcrete(p.getUniqueId(),null):s;}
    private String defaultTheme(){try{String s=plugin.getMapManager()==null?null:plugin.getMapManager().activeMob();return normalize(s==null?"creeper":s);}catch(Throwable ignored){return "creeper";}}
    private static String normalize(String s) {
        if(s==null||s.trim().isEmpty())return "creeper";s=s.trim().toLowerCase(Locale.ROOT).replace('-','_').replace(' ','_');
        if("pig_zombie".equals(s)||"zombified_piglin".equals(s))s="zombie_pigman";if("snow_golem".equals(s))s="snowman";
        if("mushroom_cow".equals(s))s="mooshroom";if("eyeofender".equals(s)||"eye_of_ender".equals(s))s="enderman";return THEMES.contains(s)?s:"creeper";
    }
    private static String randomConcrete(UUID viewer,UUID entity){int h=viewer.hashCode();if(entity!=null)h=31*h+entity.hashCode();return THEMES.get(1+Math.floorMod(h,THEMES.size()-2));}
    private static String color(String s) {
        switch(normalize(s)){
            case "creeper":case "slime":return "LIME";case "skeleton":return "LIGHT_GRAY";case "spider":return "BLACK";
            case "silverfish":case "bat":case "wolf":return "GRAY";case "zombie":return "GREEN";
            case "zombie_pigman":case "magma_cube":case "mooshroom":return "RED";case "enderman":case "witch":return "PURPLE";
            case "cave_spider":return "LIGHT_BLUE";case "blaze":case "villager":return "ORANGE";case "endermite":return "MAGENTA";
            case "guardian":return "CYAN";case "pig":case "rabbit":return "PINK";case "sheep":case "chicken":case "snowman":return "WHITE";
            case "cow":case "horse":return "BROWN";case "squid":return "BLUE";case "ocelot":return "YELLOW";default:return "LIME";
        }
    }
    private static String displayName(String s){if("map_default".equals(s))return "Map Default";if("random".equals(s))return "Random Mob";StringBuilder b=new StringBuilder();for(String part:normalize(s).split("_")){if(b.length()>0)b.append(' ');b.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));}return b.toString();}
    private static boolean isSelector(ItemStack i){if(i==null||!i.hasItemMeta())return false;ItemMeta m=i.getItemMeta();return m!=null&&m.hasDisplayName()&&ChatColor.stripColor(m.getDisplayName()).startsWith("Mob Theme:");}
    private String key(Location l){return l.getWorld().getUID()+":"+l.getBlockX()+":"+l.getBlockY()+":"+l.getBlockZ();}
    private void load(){if(!file.exists())return;YamlConfiguration y=YamlConfiguration.loadConfiguration(file);for(String id:y.getKeys(false)){try{choices.put(UUID.fromString(id),normalize(y.getString(id)));}catch(IllegalArgumentException ignored){}}}
    private void save(){try{File parent=file.getParentFile();if(parent!=null&&!parent.exists())parent.mkdirs();YamlConfiguration y=new YamlConfiguration();for(Map.Entry<UUID,String> e:choices.entrySet())y.set(e.getKey().toString(),e.getValue());y.save(file);}catch(IOException ex){plugin.getLogger().warning("Could not save mob-theme choices: "+ex.getMessage());}}
    private static final class PadVisual{final Location center;final int surfaceY;PadVisual(Location c,int y){center=c;surfaceY=y;}}
}