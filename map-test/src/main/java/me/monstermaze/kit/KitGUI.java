package me.monstermaze.kit;

import me.monstermaze.MonsterMazePlugin;
import me.monstermaze.game.GameManager;
import me.monstermaze.game.GameState;
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

/**
 * Simple chest GUI for kit selection (1.8-safe).
 */
public class KitGUI implements Listener {

    public static final String TITLE = ChatColor.DARK_GREEN + "Select a Kit";

    private final MonsterMazePlugin plugin;
    private final GameManager game;
    private final KitManager kits;

    public KitGUI(MonsterMazePlugin plugin, GameManager game, KitManager kits) {
        this.plugin = plugin;
        this.game = game;
        this.kits = kits;
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    public void open(Player player) {
        Inventory inv = Bukkit.createInventory(null, 27, TITLE);

        // Glass pane border
        ItemStack pane = named(Material.STAINED_GLASS_PANE, (short) 7, " ");
        for (int i = 0; i < 27; i++) {
            if (i != 22 && (i < 9 || i > 17 || i % 9 == 0 || i % 9 == 8)) {
                inv.setItem(i, pane);
            }
        }

        // Dynamically map middle-row slots (10 through 16) so Maverick is always visible
        java.util.List<KitType> kitsList = Arrays.asList(KitType.values());
        int size = kitsList.size();
        int[] slots;
        if (size == 1) slots = new int[]{13};
        else if (size == 2) slots = new int[]{12, 14};
        else if (size == 3) slots = new int[]{11, 13, 15};
        else if (size == 4) slots = new int[]{10, 12, 14, 16};
        else if (size == 5) slots = new int[]{10, 11, 13, 15, 16};
        else if (size == 6) slots = new int[]{10, 11, 12, 14, 15, 16};
        else {
            slots = new int[size];
            for (int i = 0; i < size; i++) slots[i] = 10 + i;
        }

        for (int i = 0; i < kitsList.size() && i < slots.length; i++) {
            inv.setItem(slots[i], kitIcon(kitsList.get(i), player));
        }

        // Info
        inv.setItem(22, named(Material.BOOK, (short) 0,
                ChatColor.YELLOW + "Click a kit to select it",
                Arrays.asList(
                        ChatColor.GRAY + "Current: " + kits.getKit(player).display,
                        ChatColor.DARK_GRAY + "You can change until the game goes live."
                )));

        player.openInventory(inv);
    }

    private ItemStack kitIcon(KitType type, Player player) {
        boolean selected = kits.getKit(player) == type;
        List<String> lore = new ArrayList<String>();
        for (String line : type.description) {
            lore.add(line);
        }
        lore.add("");
        if (selected) {
            lore.add(ChatColor.GREEN + "" + ChatColor.BOLD + "SELECTED");
        } else {
            lore.add(ChatColor.YELLOW + "Click to select");
        }

        Material mat = type.icon;
        short data = 0;
        ItemStack item = new ItemStack(mat, 1, data);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName((selected ? ChatColor.GREEN + "▶ " : "") + type.display);
        meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (event.getInventory() == null) return;
        String title = event.getInventory().getTitle();
        if (title == null || !title.equals(TITLE)) return;

        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player)) return;
        Player player = (Player) event.getWhoClicked();

        if (game.getState() == GameState.LIVE) {
            player.sendMessage(ChatColor.RED + "Can't change kit mid-game.");
            player.closeInventory();
            return;
        }

        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || clicked.getType() == Material.AIR) return;
        if (!clicked.hasItemMeta() || !clicked.getItemMeta().hasDisplayName()) return;

        String name = ChatColor.stripColor(clicked.getItemMeta().getDisplayName())
                .replace("▶ ", "").trim();

        KitType chosen = null;
        for (KitType k : KitType.values()) {
            if (ChatColor.stripColor(k.display).equalsIgnoreCase(name)
                    || k.id.equalsIgnoreCase(name)
                    || k.name().equalsIgnoreCase(name)) {
                chosen = k;
                break;
            }
        }
        if (chosen == null) {
            for (KitType k : KitType.values()) {
                if (k.icon == clicked.getType()) {
                    chosen = k;
                    break;
                }
            }
        }

        if (chosen == null) return;

        kits.setKit(player, chosen);
        player.playSound(player.getLocation(), org.bukkit.Sound.CLICK, 1f, 1.2f);
        open(player);
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (event.getInventory() != null
                && event.getInventory().getTitle() != null
                && event.getInventory().getTitle().equals(TITLE)) {
            event.setCancelled(true);
        }
    }

    private static ItemStack named(Material mat, short data, String name) {
        return named(mat, data, name, null);
    }

    private static ItemStack named(Material mat, short data, String name, List<String> lore) {
        ItemStack item = new ItemStack(mat, 1, data);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(name);
        if (lore != null) meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
    }
}