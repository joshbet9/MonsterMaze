package me.monstermaze.cosmetics;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Effect;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Pig;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

/**
 * Isolated server-side visual prototype harness. It exposes methods a future
 * game adapter can call; this repository does not contain the four source games.
 */
public final class MinigameCosmeticPrototypes extends JavaPlugin implements CommandExecutor {
    private static final String ADMIN_PERMISSION = "minigamecosmetics.admin";
    private static final int DEFAULT_RUNNER_RED = 14;

    private static final Map<String, RunnerColour> RUNNER_COLOURS = new LinkedHashMap<String, RunnerColour>();
    private static final Map<String, Byte> GLASS_COLOURS = new LinkedHashMap<String, Byte>();
    private static final Map<String, Material> PIG_ACCESSORIES = new LinkedHashMap<String, Material>();
    private static final List<String> EVOLUTION_CHOICES = Collections.unmodifiableList(
            Arrays.asList("dragon-egg"));

    private final Map<UUID, Pig> baconPigs = new LinkedHashMap<UUID, Pig>();
    private final Map<UUID, Integer> baconKills = new LinkedHashMap<UUID, Integer>();
    private final Map<UUID, ArmorStand> baconAccessories = new LinkedHashMap<UUID, ArmorStand>();
    private final Map<String, BeaconTint> beaconTints = new LinkedHashMap<String, BeaconTint>();
    private final Map<UUID, List<Entity>> previewEntities = new LinkedHashMap<UUID, List<Entity>>();

    private UUID baconLeader;

    static {
        // 16 stained-clay colours grouped into eight complementary/inverse pairs.
        addRunnerColour("white", 0, 15);
        addRunnerColour("orange", 1, 11);
        addRunnerColour("magenta", 2, 13);
        addRunnerColour("lightblue", 3, 14);
        addRunnerColour("yellow", 4, 9);
        addRunnerColour("lime", 5, 10);
        addRunnerColour("pink", 6, 8);
        addRunnerColour("gray", 7, 12);

        addGlassColour("white", 0);
        addGlassColour("orange", 1);
        addGlassColour("magenta", 2);
        addGlassColour("lightblue", 3);
        addGlassColour("yellow", 4);
        addGlassColour("lime", 5);
        addGlassColour("pink", 6);
        addGlassColour("gray", 7);
        addGlassColour("silver", 8);
        addGlassColour("cyan", 9);
        addGlassColour("purple", 10);
        addGlassColour("blue", 11);
        addGlassColour("brown", 12);
        addGlassColour("green", 13);
        addGlassColour("red", 14);
        addGlassColour("black", 15);

        PIG_ACCESSORIES.put("crown", Material.GOLD_HELMET);
        PIG_ACCESSORIES.put("top_hat", Material.OBSIDIAN);
        PIG_ACCESSORIES.put("chef", Material.CAKE);
        PIG_ACCESSORIES.put("diamond", Material.DIAMOND_BLOCK);
    }

    private static void addRunnerColour(String name, int base, int inverse) {
        RUNNER_COLOURS.put(name, new RunnerColour(name, (byte) base, (byte) inverse));
    }

    private static void addGlassColour(String name, int data) {
        GLASS_COLOURS.put(name, (byte) data);
    }

    @Override
    public void onEnable() {
        saveDefaultConfig();
        if (getCommand("cosmeticproto") != null) {
            getCommand("cosmeticproto").setExecutor(this);
        }
        getLogger().info("Server-side cosmetic prototype harness loaded. It does not hook into the production Solo packer.");
    }

    @Override
    public void onDisable() {
        for (UUID id : new ArrayList<UUID>(baconAccessories.keySet())) {
            removePigAccessory(id);
        }
        for (BeaconTint tint : new ArrayList<BeaconTint>(beaconTints.values())) {
            restoreTint(tint);
        }
        for (List<Entity> entities : previewEntities.values()) {
            for (Entity entity : entities) {
                if (entity != null && entity.isValid()) entity.remove();
            }
        }
        previewEntities.clear();
    }

    /**
     * Returns the stained-clay data value a Runner game adapter should place.
     * The no-cosmetic behaviour deliberately remains generic red clay (data 14).
     * If the selected base colour is part of the map palette, use its inverse.
     */
    public byte getRunnerClayData(UUID playerId, Set<Byte> mapPalette) {
        String selected = getEquipped(playerId, "runner");
        RunnerColour colour = RUNNER_COLOURS.get(selected);
        if (colour == null) return (byte) DEFAULT_RUNNER_RED;
        if (mapPalette != null && mapPalette.contains(colour.base)) return colour.inverse;
        return colour.base;
    }

    /** Registers the pig/entity used to represent this Bacon Brawl player. */
    public void registerBaconBrawlPig(UUID playerId, Pig pig) {
        if (playerId == null || pig == null) return;
        baconPigs.put(playerId, pig);
        if (!baconKills.containsKey(playerId)) baconKills.put(playerId, Integer.valueOf(0));
        refreshBaconBrawlLeader();
    }

    /** Call this after a Bacon Brawl kill has been confirmed by the game. */
    public void recordBaconBrawlKill(UUID killerId) {
        if (killerId == null) return;
        Integer current = baconKills.get(killerId);
        baconKills.put(killerId, Integer.valueOf(current == null ? 1 : current.intValue() + 1));
        refreshBaconBrawlLeader();
    }

    /** Resets kill counts and removes the leader accessory between rounds. */
    public void resetBaconBrawlRound() {
        for (UUID id : new ArrayList<UUID>(baconAccessories.keySet())) removePigAccessory(id);
        baconKills.clear();
        baconLeader = null;
        for (UUID id : baconPigs.keySet()) baconKills.put(id, Integer.valueOf(0));
    }

    private void refreshBaconBrawlLeader() {
        UUID best = null;
        int bestKills = -1;
        for (Map.Entry<UUID, Pig> entry : baconPigs.entrySet()) {
            Pig pig = entry.getValue();
            if (pig == null || !pig.isValid() || pig.isDead()) continue;
            int count = baconKills.containsKey(entry.getKey()) ? baconKills.get(entry.getKey()).intValue() : 0;
            if (count > bestKills || (count == bestKills && entry.getKey().equals(baconLeader))) {
                best = entry.getKey();
                bestKills = count;
            }
        }
        if (best == null) {
            for (UUID id : new ArrayList<UUID>(baconAccessories.keySet())) removePigAccessory(id);
            baconLeader = null;
            return;
        }

        UUID previous = baconLeader;
        baconLeader = best;
        if (previous != null && !previous.equals(best)) removePigAccessory(previous);
        for (UUID id : new ArrayList<UUID>(baconAccessories.keySet())) {
            if (!id.equals(best)) removePigAccessory(id);
        }

        Pig pig = baconPigs.get(best);
        String selected = getEquipped(best, "baconbrawl");
        if (selected == null || selected.isEmpty() || !PIG_ACCESSORIES.containsKey(selected)) {
            removePigAccessory(best);
        } else if (!baconAccessories.containsKey(best)) {
            attachPigAccessory(best, pig, selected);
        } else {
            ArmorStand stand = baconAccessories.get(best);
            if (stand != null && stand.isValid()) stand.setHelmet(new ItemStack(PIG_ACCESSORIES.get(selected)));
        }
    }

    /**
     * Colours the block immediately above a beacon with the player's equipped
     * Dragon Escape stained-glass choice. Returns false if no safe glass-filter
     * location is available. The calling game adapter owns the beacon itself.
     */
    public boolean applyDragonEscapeBeaconColour(Block beacon, UUID ownerId) {
        if (beacon == null || ownerId == null || beacon.getType() != Material.BEACON) return false;
        Byte data = GLASS_COLOURS.get(getEquipped(ownerId, "dragonescape"));
        if (data == null) return false;

        Block filter = beacon.getRelative(BlockFace.UP);
        if (filter.getType() != Material.AIR && filter.getType() != Material.GLASS
                && filter.getType() != Material.STAINED_GLASS) return false;

        String key = blockKey(beacon);
        if (!beaconTints.containsKey(key)) {
            beaconTints.put(key, new BeaconTint(beacon, filter.getState(), (byte) Material.STAINED_GLASS.getId(), data.byteValue()));
        }
        filter.setType(Material.STAINED_GLASS, false);
        filter.setData(data.byteValue(), false);
        return true;
    }

    /** Restores the pre-cosmetic glass/filter block saved for this beacon. */
    public void restoreDragonEscapeBeaconColour(Block beacon) {
        if (beacon == null) return;
        BeaconTint tint = beaconTints.remove(blockKey(beacon));
        if (tint != null) restoreTint(tint);
    }

    /**
     * Call when an Evolution game confirms the player has evolved. The dragon-egg
     * cosmetic uses the portal/teleport particle effect emitted by a dragon egg.
     */
    public void onPlayerEvolved(UUID playerId, Location location) {
        if (playerId == null || location == null) return;
        if ("dragon-egg".equals(getEquipped(playerId, "evolution"))) {
            spawnDragonEggParticles(location);
        }
    }

    private void spawnDragonEggParticles(Location location) {
        if (location.getWorld() == null) return;
        location.getWorld().spigot().playEffect(location, Effect.PORTAL, 0, 0,
                0.45f, 0.6f, 0.45f, 0.06f, 48, 64);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0 || "help".equalsIgnoreCase(args[0])) {
            sendHelp(sender);
            return true;
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        if ("list".equals(sub)) {
            showChoices(sender);
            return true;
        }
        if ("status".equals(sub)) {
            showStatus(sender);
            return true;
        }
        if ("unlock".equals(sub)) {
            handleUnlock(sender, args);
            return true;
        }
        if ("equip".equals(sub)) {
            handleEquip(sender, args);
            return true;
        }
        if ("off".equals(sub)) {
            handleOff(sender, args);
            return true;
        }
        if ("preview".equals(sub)) {
            handlePreview(sender, args);
            return true;
        }
        sendHelp(sender);
        return true;
    }

    private void handleUnlock(CommandSender sender, String[] args) {
        if (!sender.hasPermission(ADMIN_PERMISSION)) {
            sender.sendMessage(ChatColor.RED + "Only an operator can grant prototype unlocks.");
            return;
        }
        if (args.length < 4) {
            sender.sendMessage(ChatColor.YELLOW + "Usage: /cosmeticproto unlock <online-player> <game> <choice|all>");
            return;
        }
        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            sender.sendMessage(ChatColor.RED + "That player must be online for a test unlock.");
            return;
        }
        String game = normalizeGame(args[2]);
        if (game == null) {
            sender.sendMessage(ChatColor.RED + "Unknown game. Use runner, baconbrawl, dragonescape or evolution.");
            return;
        }
        List<String> choices = choicesFor(game);
        String requested = normalizeChoice(args[3]);
        if ("all".equals(requested)) {
            for (String choice : choices) setUnlocked(target.getUniqueId(), game, choice, true);
            sender.sendMessage(ChatColor.GREEN + "Unlocked all " + game + " prototype cosmetics for " + target.getName() + ".");
            target.sendMessage(ChatColor.GREEN + "All " + game + " prototype cosmetics have been unlocked for you.");
            return;
        }
        if (!choices.contains(requested)) {
            sender.sendMessage(ChatColor.RED + "Unknown choice for " + game + ". Available: " + join(choices));
            return;
        }
        setUnlocked(target.getUniqueId(), game, requested, true);
        sender.sendMessage(ChatColor.GREEN + "Unlocked " + requested + " for " + target.getName() + " (" + game + ").");
        target.sendMessage(ChatColor.GREEN + "Prototype cosmetic unlocked: " + game + " / " + requested + ".");
    }

    private void handleEquip(CommandSender sender, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("Only an in-game player can equip a prototype cosmetic.");
            return;
        }
        if (args.length < 3) {
            sender.sendMessage(ChatColor.YELLOW + "Usage: /cosmeticproto equip <game> <choice>");
            return;
        }
        Player player = (Player) sender;
        String game = normalizeGame(args[1]);
        String choice = normalizeChoice(args[2]);
        if (game == null || !choicesFor(game).contains(choice)) {
            sender.sendMessage(ChatColor.RED + "Unknown game or choice. Use /cosmeticproto list.");
            return;
        }
        if (!isUnlocked(player.getUniqueId(), game, choice)) {
            sender.sendMessage(ChatColor.RED + "That prototype cosmetic is not unlocked for you.");
            return;
        }
        setEquipped(player.getUniqueId(), game, choice);
        sender.sendMessage(ChatColor.GREEN + "Equipped " + choice + " for " + game + ".");
        if ("baconbrawl".equals(game)) refreshBaconBrawlLeader();
    }

    private void handleOff(CommandSender sender, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("Only an in-game player can unequip a prototype cosmetic.");
            return;
        }
        if (args.length < 2) {
            sender.sendMessage(ChatColor.YELLOW + "Usage: /cosmeticproto off <game>");
            return;
        }
        String game = normalizeGame(args[1]);
        if (game == null) {
            sender.sendMessage(ChatColor.RED + "Unknown game. Use runner, baconbrawl, dragonescape or evolution.");
            return;
        }
        Player player = (Player) sender;
        setEquipped(player.getUniqueId(), game, null);
        sender.sendMessage(ChatColor.GREEN + "Unequipped prototype cosmetics for " + game + ".");
        if ("baconbrawl".equals(game)) refreshBaconBrawlLeader();
    }

    private void handlePreview(CommandSender sender, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("Only an in-game player can preview these cosmetics.");
            return;
        }
        if (args.length < 2) {
            sender.sendMessage(ChatColor.YELLOW + "Usage: /cosmeticproto preview <runner|baconbrawl|dragonescape|evolution> [map-palette]");
            return;
        }
        Player player = (Player) sender;
        String game = normalizeGame(args[1]);
        if (game == null) {
            sender.sendMessage(ChatColor.RED + "Unknown game. Use /cosmeticproto list.");
            return;
        }
        if ("runner".equals(game)) {
            previewRunner(player, args.length > 2 ? args[2] : "");
        } else if ("baconbrawl".equals(game)) {
            previewBaconBrawl(player);
        } else if ("dragonescape".equals(game)) {
            previewDragonEscape(player);
        } else {
            spawnDragonEggParticles(player.getLocation().add(0, 1.0, 0));
            player.sendMessage(ChatColor.GREEN + "Triggered the Evolution dragon-egg particle preview.");
        }
    }

    private void previewRunner(final Player player, String paletteText) {
        Set<Byte> palette = parsePalette(paletteText);
        byte data = getRunnerClayData(player.getUniqueId(), palette);
        RunnerColour selected = RUNNER_COLOURS.get(getEquipped(player.getUniqueId(), "runner"));
        Location start = previewStart(player.getLocation(), 4.0, 2.0);
        int dx = dominantX(player.getLocation().getDirection());
        int dz = dx == 0 ? dominantZ(player.getLocation().getDirection()) : 0;
        if (dx == 0 && dz == 0) dx = 1;

        final List<BlockSnapshot> snapshots = new ArrayList<BlockSnapshot>();
        for (int i = 0; i < 8; i++) {
            Block block = start.getWorld().getBlockAt(start.getBlockX() + dx * i, start.getBlockY(), start.getBlockZ() + dz * i);
            placeTemporary(block, Material.STAINED_CLAY, data, snapshots);
        }
        restoreAfter(snapshots, 20 * 12);
        String choice = selected == null ? "default red" : selected.name;
        String inversion = selected != null && palette.contains(selected.base) ? " (inverted for map palette)" : "";
        player.sendMessage(ChatColor.GREEN + "Runner clay preview: " + choice + inversion + ", stained-clay data " + data + ". Temporary sample lasts 12 seconds.");
    }

    private void previewBaconBrawl(final Player player) {
        Location location = previewStart(player.getLocation(), 4.0, 0.0);
        final Pig pig = (Pig) location.getWorld().spawnEntity(location, EntityType.PIG);
        pig.setCustomName(ChatColor.GOLD + "Bacon Brawl Leader");
        pig.setCustomNameVisible(true);
        String cosmetic = getEquipped(player.getUniqueId(), "baconbrawl");
        if (cosmetic == null || !PIG_ACCESSORIES.containsKey(cosmetic)) cosmetic = "crown";
        attachPigAccessory(player.getUniqueId(), pig, cosmetic);
        player.sendMessage(ChatColor.GREEN + "Bacon Brawl leader pig preview: " + cosmetic + ". Temporary pig lasts 20 seconds.");

        new BukkitRunnable() {
            @Override public void run() {
                removePigAccessory(player.getUniqueId());
                if (pig.isValid()) pig.remove();
            }
        }.runTaskLater(this, 20 * 20);
    }

    private void previewDragonEscape(final Player player) {
        Location start = previewStart(player.getLocation(), 6.0, 2.0);
        final List<BlockSnapshot> snapshots = new ArrayList<BlockSnapshot>();
        int x = start.getBlockX();
        int y = start.getBlockY();
        int z = start.getBlockZ();
        for (int ox = -1; ox <= 1; ox++) {
            for (int oz = -1; oz <= 1; oz++) {
                placeTemporary(start.getWorld().getBlockAt(x + ox, y, z + oz), Material.IRON_BLOCK, (byte) 0, snapshots);
            }
        }
        placeTemporary(start.getWorld().getBlockAt(x, y + 1, z), Material.BEACON, (byte) 0, snapshots);
        Byte data = GLASS_COLOURS.get(getEquipped(player.getUniqueId(), "dragonescape"));
        if (data == null) data = Byte.valueOf((byte) 0);
        placeTemporary(start.getWorld().getBlockAt(x, y + 2, z), Material.STAINED_GLASS, data.byteValue(), snapshots);
        restoreAfter(snapshots, 20 * 15);
        player.sendMessage(ChatColor.GREEN + "Dragon Escape beacon-filter preview: " + nameForGlassData(data.byteValue()) + ". Temporary sample lasts 15 seconds.");
    }

    private void attachPigAccessory(UUID owner, Pig pig, String choice) {
        if (owner == null || pig == null || !pig.isValid()) return;
        removePigAccessory(owner);
        if (pig.getPassenger() != null) return;
        ArmorStand stand = (ArmorStand) pig.getWorld().spawnEntity(pig.getLocation(), EntityType.ARMOR_STAND);
        stand.setVisible(false);
        stand.setGravity(false);
        stand.setSmall(true);
        stand.setBasePlate(false);
        Material helmet = PIG_ACCESSORIES.get(choice);
        if (helmet != null) stand.setHelmet(new ItemStack(helmet));
        pig.setPassenger(stand);
        baconAccessories.put(owner, stand);
    }

    private void removePigAccessory(UUID owner) {
        ArmorStand stand = baconAccessories.remove(owner);
        if (stand == null) return;
        Entity vehicle = stand.getVehicle();
        if (vehicle instanceof Pig && vehicle.getPassenger() == stand) vehicle.eject();
        if (stand.isValid()) stand.remove();
    }

    private void restoreAfter(final List<BlockSnapshot> snapshots, long ticks) {
        new BukkitRunnable() {
            @Override public void run() {
                for (BlockSnapshot snapshot : snapshots) snapshot.restoreIfUnchanged();
            }
        }.runTaskLater(this, ticks);
    }

    private void placeTemporary(Block block, Material material, byte data, List<BlockSnapshot> snapshots) {
        snapshots.add(new BlockSnapshot(block, block.getState(), material, data));
        block.setType(material, false);
        block.setData(data, false);
    }

    private Location previewStart(Location source, double forward, double up) {
        org.bukkit.util.Vector direction = source.getDirection().clone().setY(0);
        if (direction.lengthSquared() < 0.001) direction.setX(1);
        direction.normalize().multiply(forward);
        return source.clone().add(direction).add(0, up, 0);
    }

    private int dominantX(org.bukkit.util.Vector direction) {
        return Math.abs(direction.getX()) > Math.abs(direction.getZ()) ? (direction.getX() >= 0 ? 1 : -1) : 0;
    }

    private int dominantZ(org.bukkit.util.Vector direction) {
        return Math.abs(direction.getZ()) > 0.001 ? (direction.getZ() >= 0 ? 1 : -1) : 0;
    }

    private Set<Byte> parsePalette(String text) {
        if (text == null || text.trim().isEmpty()) return Collections.emptySet();
        Set<Byte> result = new LinkedHashSet<Byte>();
        for (String token : text.split(",")) {
            String name = normalizeChoice(token);
            RunnerColour runner = RUNNER_COLOURS.get(name);
            if (runner != null) {
                result.add(runner.base);
                continue;
            }
            Byte glass = GLASS_COLOURS.get(name);
            if (glass != null) result.add(glass);
        }
        return result;
    }

    private String normalizeGame(String value) {
        String game = value.toLowerCase(Locale.ROOT).replace("-", "").replace("_", "");
        if ("runner".equals(game)) return "runner";
        if ("baconbrawl".equals(game)) return "baconbrawl";
        if ("dragonescape".equals(game)) return "dragonescape";
        if ("evolution".equals(game)) return "evolution";
        return null;
    }

    private String normalizeChoice(String value) {
        return value.toLowerCase(Locale.ROOT).replace("-", "_").replace(" ", "_");
    }

    private List<String> choicesFor(String game) {
        if ("runner".equals(game)) return new ArrayList<String>(RUNNER_COLOURS.keySet());
        if ("baconbrawl".equals(game)) return new ArrayList<String>(PIG_ACCESSORIES.keySet());
        if ("dragonescape".equals(game)) return new ArrayList<String>(GLASS_COLOURS.keySet());
        if ("evolution".equals(game)) return new ArrayList<String>(EVOLUTION_CHOICES);
        return Collections.emptyList();
    }

    private boolean isUnlocked(UUID id, String game, String choice) {
        return getConfig().getBoolean("unlocks." + id.toString() + "." + game + "." + choice, false);
    }

    private void setUnlocked(UUID id, String game, String choice, boolean value) {
        getConfig().set("unlocks." + id.toString() + "." + game + "." + choice, Boolean.valueOf(value));
        saveConfig();
    }

    private String getEquipped(UUID id, String game) {
        String value = getConfig().getString("equipped." + id.toString() + "." + game, "");
        if (value == null || value.trim().isEmpty()) return null;
        return value.toLowerCase(Locale.ROOT);
    }

    private void setEquipped(UUID id, String game, String choice) {
        getConfig().set("equipped." + id.toString() + "." + game, choice);
        saveConfig();
    }

    private void showChoices(CommandSender sender) {
        sender.sendMessage(ChatColor.GOLD + "Minigame cosmetic prototype choices");
        sender.sendMessage(ChatColor.YELLOW + "Runner (8 clay pairs): " + join(choicesFor("runner")));
        sender.sendMessage(ChatColor.YELLOW + "Bacon Brawl (leader-pig accessories): " + join(choicesFor("baconbrawl")));
        sender.sendMessage(ChatColor.YELLOW + "Dragon Escape (beacon colours): " + join(choicesFor("dragonescape")));
        sender.sendMessage(ChatColor.YELLOW + "Evolution: " + join(choicesFor("evolution")));
        sender.sendMessage(ChatColor.GRAY + "Micro Battles is a separate client-side prototype in JNP Mod.");
    }

    private void showStatus(CommandSender sender) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("Status is player-specific; run this in-game.");
            return;
        }
        UUID id = ((Player) sender).getUniqueId();
        for (String game : Arrays.asList("runner", "baconbrawl", "dragonescape", "evolution")) {
            String equipped = getEquipped(id, game);
            int unlocked = 0;
            for (String choice : choicesFor(game)) if (isUnlocked(id, game, choice)) unlocked++;
            sender.sendMessage(ChatColor.YELLOW + game + ChatColor.GRAY + ": equipped="
                    + (equipped == null ? "none" : equipped) + ", unlocked=" + unlocked + "/" + choicesFor(game).size());
        }
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage(ChatColor.GOLD + "Minigame Cosmetic Prototype commands");
        sender.sendMessage(ChatColor.YELLOW + "/cosmeticproto list" + ChatColor.GRAY + " - list available choices");
        sender.sendMessage(ChatColor.YELLOW + "/cosmeticproto unlock <player> <game> <choice|all>" + ChatColor.GRAY + " - operator-only unlock");
        sender.sendMessage(ChatColor.YELLOW + "/cosmeticproto equip <game> <choice>" + ChatColor.GRAY + " - equip an unlocked choice");
        sender.sendMessage(ChatColor.YELLOW + "/cosmeticproto off <game>" + ChatColor.GRAY + " - unequip a game cosmetic");
        sender.sendMessage(ChatColor.YELLOW + "/cosmeticproto status" + ChatColor.GRAY + " - show current unlocks/equipment");
        sender.sendMessage(ChatColor.YELLOW + "/cosmeticproto preview <game> [map-colours]" + ChatColor.GRAY + " - preview a visual effect");
    }

    private String join(List<String> values) {
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < values.size(); i++) {
            if (i > 0) result.append(", ");
            result.append(values.get(i));
        }
        return result.toString();
    }

    private String nameForGlassData(byte data) {
        for (Map.Entry<String, Byte> entry : GLASS_COLOURS.entrySet()) {
            if (entry.getValue().byteValue() == data) return entry.getKey();
        }
        return "white";
    }

    private String blockKey(Block block) {
        return block.getWorld().getName() + ":" + block.getX() + ":" + block.getY() + ":" + block.getZ();
    }

    private void restoreTint(BeaconTint tint) {
        if (tint == null || tint.beacon == null || tint.beacon.getWorld() == null) return;
        Block filter = tint.beacon.getRelative(BlockFace.UP);
        if (filter.getType() == Material.STAINED_GLASS && filter.getData() == tint.appliedData) {
            tint.original.update(true, false);
        }
    }

    private static final class RunnerColour {
        final String name;
        final byte base;
        final byte inverse;

        RunnerColour(String name, byte base, byte inverse) {
            this.name = name;
            this.base = base;
            this.inverse = inverse;
        }
    }

    private static final class BeaconTint {
        final Block beacon;
        final BlockState original;
        final byte appliedType;
        final byte appliedData;

        BeaconTint(Block beacon, BlockState original, byte appliedType, byte appliedData) {
            this.beacon = beacon;
            this.original = original;
            this.appliedType = appliedType;
            this.appliedData = appliedData;
        }
    }

    private static final class BlockSnapshot {
        final Block block;
        final BlockState original;
        final Material expectedMaterial;
        final byte expectedData;

        BlockSnapshot(Block block, BlockState original, Material expectedMaterial, byte expectedData) {
            this.block = block;
            this.original = original;
            this.expectedMaterial = expectedMaterial;
            this.expectedData = expectedData;
        }

        void restoreIfUnchanged() {
            if (block.getType() == expectedMaterial && block.getData() == expectedData) {
                original.update(true, false);
            }
        }
    }
}