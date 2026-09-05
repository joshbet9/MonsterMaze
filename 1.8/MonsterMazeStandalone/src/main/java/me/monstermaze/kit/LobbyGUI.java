package me.monstermaze.kit;

import me.monstermaze.MonsterMazePlugin;
import me.monstermaze.game.GameManager;
import me.monstermaze.game.GameState;
import me.monstermaze.game.MazeMode;
import me.monstermaze.stats.LeaderboardManager;
import me.monstermaze.util.MobTypes;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Single-villager Monster Maze lobby selector with home and sub-pages. */
public class LobbyGUI implements Listener {
    public static final String TITLE = ChatColor.DARK_GREEN + "Monster Maze";
    private static final String MODE_TITLE = TITLE + ChatColor.GRAY + " - Mode";
    private static final String MAP_TITLE = TITLE + ChatColor.GRAY + " - Map";
    private static final String KIT_TITLE = TITLE + ChatColor.GRAY + " - Kit";
    private static final String PATTERN_TITLE = TITLE + ChatColor.GRAY + " - Pattern";
    private static final String MOB_TITLE = TITLE + ChatColor.GRAY + " - Mob";

    private final MonsterMazePlugin plugin;
    private final GameManager game;
    private final KitManager kits;

    public LobbyGUI(MonsterMazePlugin plugin, GameManager game, KitManager kits) {
        this.plugin = plugin;
        this.game = game;
        this.kits = kits;
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    public void open(Player player) { openHome(player); }

    private void openHome(Player player) {
        Inventory inv = Bukkit.createInventory(null, 54, TITLE);
        fill(inv);
        MazeMode mode = plugin.getMode();
        KitType kit = kits.getKit(player);
        String map = plugin.getMapManager().getActiveMap();
        String mob = plugin.getMapManager().activeMob();
        int pattern = game.getMazeGenerator().getForcedPattern();
        inv.setItem(4, item(Material.NETHER_STAR, ChatColor.GOLD + "Monster Maze", Arrays.asList(ChatColor.GRAY + "Configure your run below.", ChatColor.GRAY + "Then press " + ChatColor.GREEN + "PLAY" + ChatColor.GRAY + " to begin.")));
        inv.setItem(10, item(Material.WATCH, mode.color + "Mode", Arrays.asList(ChatColor.WHITE + mode.id, "", ChatColor.YELLOW + "Click to choose mode")));
        inv.setItem(13, item(Material.MAP, ChatColor.LIGHT_PURPLE + "Map", Arrays.asList(ChatColor.WHITE + pretty(map), "", ChatColor.YELLOW + "Click to choose map")));
        inv.setItem(16, item(kit.icon, ChatColor.YELLOW + "Kit", Arrays.asList(ChatColor.WHITE + kit.display, "", ChatColor.YELLOW + "Click to choose kit")));
        inv.setItem(19, item(Material.MONSTER_EGG, ChatColor.RED + "Mob", Arrays.asList(ChatColor.WHITE + prettyMob(mob), ChatColor.GRAY + "Default: " + ChatColor.WHITE + prettyMob(plugin.getMapManager().mob(map)), "", ChatColor.YELLOW + "Click to choose mob")));
        inv.setItem(28, item(Material.PAPER, ChatColor.AQUA + "Pattern", Arrays.asList(ChatColor.WHITE + patternName(pattern), "", ChatColor.YELLOW + "Click to choose pattern")));
        inv.setItem(31, pbItem(player, mode, pattern, kit, map, mob));
        inv.setItem(37, item(Material.BOOK, ChatColor.GRAY + "Selected Run", Arrays.asList(ChatColor.GRAY + "Mode: " + mode.color + mode.id, ChatColor.GRAY + "Map: " + ChatColor.WHITE + pretty(map), ChatColor.GRAY + "Mob: " + ChatColor.WHITE + prettyMob(mob), ChatColor.GRAY + "Kit: " + ChatColor.WHITE + kit.display, ChatColor.GRAY + "Pattern: " + ChatColor.WHITE + patternName(pattern))));
        inv.setItem(49, item(Material.EMERALD_BLOCK, ChatColor.GREEN + "PLAY", Arrays.asList(ChatColor.GRAY + "Start Monster Maze with this selection.")));
        inv.setItem(53, item(Material.BARRIER, ChatColor.RED + "Close", null));
        player.openInventory(inv);
    }

    private ItemStack pbItem(Player player, MazeMode mode, int pattern, KitType kit, String map, String mob) {
        List<String> lore = new ArrayList<String>();
        lore.add(ChatColor.GRAY + "Mode: " + mode.color + mode.id);
        lore.add(ChatColor.GRAY + "Map: " + ChatColor.WHITE + pretty(map));
        lore.add(ChatColor.GRAY + "Mob: " + ChatColor.WHITE + prettyMob(mob));
        lore.add(ChatColor.GRAY + "Kit: " + ChatColor.WHITE + kit.display);
        lore.add(ChatColor.GRAY + "Pattern: " + ChatColor.WHITE + patternName(pattern));
        lore.add("");
        if (pattern < 0) lore.add(ChatColor.YELLOW + "Select a specific pattern to view PB.");
        else {
            int pb = plugin.getLeaderboards().getKitPB(mode, pattern, player.getUniqueId(), kit.id);
            lore.add(pb > 0 ? ChatColor.GREEN + "Stage " + pb : ChatColor.GRAY + "No personal best yet");
        }
        return item(Material.GOLD_BLOCK, ChatColor.GOLD + "Your Personal Best", lore);
    }

    private void openModes(Player p) {
        Inventory inv = Bukkit.createInventory(null, 27, MODE_TITLE);
        fill(inv);
        MazeMode selected = plugin.getMode();
        int[] slots = {10, 12, 14, 16};
        MazeMode[] modes = MazeMode.values();
        for (int i = 0; i < modes.length; i++) {
            MazeMode m = modes[i];
            inv.setItem(slots[i], item(Material.WATCH, (selected == m ? ChatColor.GREEN + "▶ " : "") + m.color + m.id, Arrays.asList(ChatColor.GRAY + m.description, "", selected == m ? ChatColor.GREEN + "SELECTED" : ChatColor.YELLOW + "Click to select")));
        }
        back(inv); p.openInventory(inv);
    }

    private void openMaps(Player p) {
        Inventory inv = Bukkit.createInventory(null, 54, MAP_TITLE); fill(inv);
        List<String> maps = plugin.getMapManager().knownMaps(); String selected = plugin.getMapManager().getActiveMap();
        int[] slots = {10,11,12,13,14,15,16,19,20,21,22,23,24,25};
        for (int i=0;i<maps.size() && i<slots.length;i++) { String map=maps.get(i); boolean active=map.equalsIgnoreCase(selected); inv.setItem(slots[i],item(Material.GRASS,(active?ChatColor.GREEN+"▶ ":"")+pretty(map),Arrays.asList(active?ChatColor.GREEN+"SELECTED":ChatColor.YELLOW+"Click to select"))); }
        back(inv); p.openInventory(inv);
    }

    private void openKits(Player p) {
        Inventory inv=Bukkit.createInventory(null,27,KIT_TITLE); fill(inv);
        MazeMode mode = plugin.getMode();
        List<KitType> available=KitType.available(mode); KitType selected=kits.getKit(p); int[] slots={10,12,14,16,22};
        for(int i=0;i<available.size()&&i<slots.length;i++){KitType k=available.get(i);boolean active=selected==k;inv.setItem(slots[i],item(k.icon,(active?ChatColor.GREEN+"▶ ":"")+k.display,loreForKit(k,active,mode)));}
        back(inv);p.openInventory(inv);
    }

    private void openPatterns(Player p) {
        Inventory inv=Bukkit.createInventory(null,27,PATTERN_TITLE); fill(inv); int selected=game.getMazeGenerator().getForcedPattern();
        for(int i=0;i<3;i++){boolean active=selected==i;inv.setItem(10+i*2,item(Material.PAPER,(active?ChatColor.GREEN+"▶ ":"")+"Maze "+(i+1),Arrays.asList(active?ChatColor.GREEN+"SELECTED":ChatColor.YELLOW+"Click to select")));}
        inv.setItem(17,item(Material.NETHER_STAR,(selected<0?ChatColor.GREEN+"▶ ":"")+"Random Pattern",Arrays.asList(selected<0?ChatColor.GREEN+"SELECTED":ChatColor.YELLOW+"Click to select")));
        back(inv);p.openInventory(inv);
    }

    private void openMobs(Player p) {
        List<MobTypes.MobType> mobs=MobTypes.all(); Inventory inv=Bukkit.createInventory(null,54,MOB_TITLE); fill(inv);
        String map=plugin.getMapManager().getActiveMap(); String selected=plugin.getMapManager().activeMob(); boolean override=plugin.getMapManager().hasMobOverride(map);
        inv.setItem(4,item(Material.NETHER_STAR,ChatColor.GOLD+"Mob Skin",Arrays.asList(ChatColor.GRAY+"Map: "+ChatColor.WHITE+pretty(map),ChatColor.GRAY+"Current: "+ChatColor.WHITE+prettyMob(selected))));
        inv.setItem(8,item(Material.BOOK,ChatColor.YELLOW+"Map Default",Arrays.asList(ChatColor.GRAY+"Use the map's configured mob:",ChatColor.WHITE+prettyMob(plugin.getMapManager().mob(map)),"",override?ChatColor.YELLOW+"Click to restore default":ChatColor.GREEN+"Currently selected")));
        int slot=18;
        for(MobTypes.MobType mob:mobs){if(slot>=53)break;boolean active=!override?mob.id.equalsIgnoreCase(plugin.getMapManager().mob(map)):mob.id.equalsIgnoreCase(selected);inv.setItem(slot++,item(Material.MONSTER_EGG,(active?ChatColor.GREEN+"▶ ":"")+mob.display,Arrays.asList(ChatColor.GRAY+"Minecraft 1.8.8",active?ChatColor.GREEN+"SELECTED":ChatColor.YELLOW+"Click to select")));}
        back(inv);p.openInventory(inv);
    }

    @EventHandler public void onClick(InventoryClickEvent event){
        if(event.getInventory()==null)return;String title=event.getInventory().getTitle();
        if(!TITLE.equals(title)&&!MODE_TITLE.equals(title)&&!MAP_TITLE.equals(title)&&!KIT_TITLE.equals(title)&&!PATTERN_TITLE.equals(title)&&!MOB_TITLE.equals(title))return;
        event.setCancelled(true);if(!(event.getWhoClicked() instanceof Player))return;Player p=(Player)event.getWhoClicked();
        if(game.getState()==GameState.LIVE){p.closeInventory();p.sendMessage(ChatColor.RED+"The game is already running.");return;} int slot=event.getRawSlot();
        if(TITLE.equals(title)){if(slot==10){openModes(p);return;}if(slot==13){openMaps(p);return;}if(slot==16){openKits(p);return;}if(slot==19){openMobs(p);return;}if(slot==28){openPatterns(p);return;}if(slot==49){p.closeInventory();game.startGame();return;}if(slot==53){p.closeInventory();}return;}
        if(MODE_TITLE.equals(title)){if(slot==0){openHome(p);return;}int[] slots={10,12,14,16};for(int i=0;i<slots.length;i++)if(slot==slots[i]){MazeMode mode=MazeMode.values()[i];plugin.setMode(mode);KitType current=kits.getKit(p);List<KitType> available=KitType.available(mode);if(!available.contains(current))kits.setKit(p,available.get(0));game.rerenderLeaderboardBoard();openHome(p);return;}return;}
        if(MAP_TITLE.equals(title)){if(slot==0){openHome(p);return;}List<String> maps=plugin.getMapManager().knownMaps();int[] slots={10,11,12,13,14,15,16,19,20,21,22,23,24,25};for(int i=0;i<slots.length;i++)if(slot==slots[i]&&i<maps.size()){String map=maps.get(i);if(plugin.getMapManager().setActiveMap(map)){plugin.getMapManager().ensureActiveWorld();game.applyMap();}openHome(p);return;}return;}
        if(KIT_TITLE.equals(title)){if(slot==0){openHome(p);return;}MazeMode mode=plugin.getMode();List<KitType> available=KitType.available(mode);int[] slots={10,12,14,16,22};for(int i=0;i<slots.length;i++)if(slot==slots[i]&&i<available.size()){kits.setKit(p,available.get(i));openHome(p);return;}return;}
        if(PATTERN_TITLE.equals(title)){if(slot==0){openHome(p);return;}if(slot==10||slot==12||slot==14){game.getMazeGenerator().setForcedPattern((slot-10)/2);openHome(p);return;}if(slot==17){game.getMazeGenerator().setForcedPattern(-1);openHome(p);}return;}
        if(MOB_TITLE.equals(title)){if(slot==0){openHome(p);return;}if(slot==8){plugin.getMapManager().setActiveMobOverride(null);game.applyMap();openHome(p);return;}if(slot>=18&&slot<53){List<MobTypes.MobType> mobs=MobTypes.all();int index=slot-18;if(index>=0&&index<mobs.size()){plugin.getMapManager().setActiveMobOverride(mobs.get(index).id);game.applyMap();openHome(p);}}}
    }

    @EventHandler public void onDrag(InventoryDragEvent event){String title=event.getInventory()==null?"":event.getInventory().getTitle();if(TITLE.equals(title)||MODE_TITLE.equals(title)||MAP_TITLE.equals(title)||KIT_TITLE.equals(title)||PATTERN_TITLE.equals(title)||MOB_TITLE.equals(title))event.setCancelled(true);}
    private void back(Inventory inv){inv.setItem(0,item(Material.ARROW,ChatColor.YELLOW+"Back",null));}
    private void fill(Inventory inv){ItemStack pane=item(Material.STAINED_GLASS_PANE," ",null);for(int i=0;i<inv.getSize();i++)inv.setItem(i,pane);}
    private List<String> loreForKit(KitType k,boolean selected,MazeMode mode){List<String> lore=new ArrayList<String>(Arrays.asList(k.description));lore.clear();for(String line:k.description){lore.add(line);}lore.add("");lore.add(selected?ChatColor.GREEN+"SELECTED":ChatColor.YELLOW+"Click to select");return lore;}
    private ItemStack item(Material material,String name,List<String> lore){ItemStack stack=new ItemStack(material,1);ItemMeta meta=stack.getItemMeta();meta.setDisplayName(name);if(lore!=null)meta.setLore(lore);stack.setItemMeta(meta);return stack;}
    private String patternName(int pattern){return pattern<0?"Random":LeaderboardManager.patternName(pattern);}
    private String pretty(String map){if(map==null||map.isEmpty())return"Unknown";if(map.equalsIgnoreCase("eyeofender"))return"Eye of Ender";String[] parts=map.split("_");StringBuilder out=new StringBuilder();for(String part:parts){if(part.isEmpty())continue;if(out.length()>0)out.append(' ');out.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));}return out.toString();}
    private String prettyMob(String mob){MobTypes.MobType type=MobTypes.byId(mob);if(type!=null)return type.display;if(mob==null||mob.isEmpty())return"Snow Golem";String[] parts=mob.split("_");StringBuilder out=new StringBuilder();for(String part:parts){if(out.length()>0)out.append(' ');if(!part.isEmpty())out.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));}return out.toString();}
}
