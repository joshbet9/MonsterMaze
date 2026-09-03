package me.monstermaze.kit;

import me.monstermaze.MonsterMazePlugin;
import me.monstermaze.entity.MonsterManager;
import me.monstermaze.event.AbilityUseEvent;
import me.monstermaze.event.EntityLaunchEvent;
import me.monstermaze.event.MonsterBumpPlayerEvent;
import me.monstermaze.game.GameManager;
import me.monstermaze.game.GameState;
import me.monstermaze.game.MazeMode;
import me.monstermaze.util.TextUtil;
import me.monstermaze.util.UtilAction;
import me.monstermaze.util.UtilAlg;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Particle;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Snowball;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.UUID;

/**
 * Kits matching Mineplex Monster Maze source:
 * KitJumper, KitSlowball, KitBodyBuilder, KitRepulsor, PerkRepulsor, jumpEvent.
 */
public class KitManager implements Listener {

    private final MonsterMazePlugin plugin;
    private final GameManager game;
    private final me.monstermaze.util.GameScoreboard scoreboard;
    private final Map<UUID, KitType> selected = new HashMap<UUID, KitType>();

    /** Jumper: Recharge "MM Player Jump" 750ms */
    private final Map<UUID, Long> jumpRecharge = new HashMap<UUID, Long>();

    /** Slowball constructor: last give time */
    private final Map<UUID, Long> snowballConstructor = new HashMap<UUID, Long>();

    /** Repulsor launched entities (remove when grounded / timeout) */
    private final Map<Entity, Long> launched = new HashMap<Entity, Long>();

    /** Slowball QOL: last time each player was knocked by a mob (ms epoch) */
    private final Map<UUID, Long> lastMobHit = new HashMap<UUID, Long>();

    /** Body Builder "Body Rush" secondary: remaining deflects (0/absent = inactive).
     *  Right-click activates a persistent buff; each mob contact deflects the mob away and
     *  decrements one use, granting the player full bump immunity while the counter is > 0. */
    private final Map<UUID, Long> bodyRushUntil = new HashMap<UUID, Long>();
    private final Map<UUID, Long> bodyRushHitFeedback = new HashMap<UUID, Long>();
    private final Map<UUID, Long> cryoFrozenUntil = new HashMap<UUID, Long>();

    /** Slowballer "Cryo Blitz" secondary: last use timestamp (ms epoch). Q-drop freezes mobs
     *  within CRYO_RADIUS blocks for CRYO_FREEZE_MS, on a CRYO_COOLDOWN_MS cooldown. */
    private final Map<UUID, Long> cryoCooldown = new HashMap<UUID, Long>();

    /** Body Builder secondary config. */
    private static final long BODY_RUSH_DURATION_MS = 10000L;

    /** Slowballer secondary config. */
    private static final int CRYO_COOLDOWN_MS = 30000;
    private static final long CRYO_FREEZE_MS = 3000;
    private static final int CRYO_RADIUS = 6;

    /** Player -> how they view OTHER players (their dye state). Default VISIBLE. */
    private final Map<UUID, VisMode> visMode = new HashMap<UUID, VisMode>();

    /** PERF: last action-bar text sent per player (only resend on change). */
    private final Map<UUID, String> lastJumpBar = new HashMap<UUID, String>();

    /** How a player sees the rest of the field: VISIBLE (solid), INVISIBLE (hidden from them),
     *  or TRANSPARENT (see-through ghosts). */
    private enum VisMode { VISIBLE, INVISIBLE, TRANSPARENT }

    private KitGUI gui;
    private KitSelectorNPCs npcs;

    public KitManager(MonsterMazePlugin plugin, GameManager game, me.monstermaze.util.GameScoreboard scoreboard) {
        this.plugin = plugin;
        this.game = game;
        this.scoreboard = scoreboard;
        Bukkit.getPluginManager().registerEvents(this, plugin);

        this.gui = new KitGUI(plugin, game, this);
        this.npcs = new KitSelectorNPCs(plugin, game, this, gui);

        // Slowball constructor tick (every 2 seconds = PerkConstructor rate)
        Bukkit.getScheduler().runTaskTimer(plugin, new Runnable() {
            @Override
            public void run() {
                if (game.getState() != GameState.LIVE) return;
                constructorSlowball();
                // Re-sync per-observer views so late/returning players show up correctly.
                refreshViews();
            }
        }, 40L, 40L);

        // Jumper jumpEvent + Repulsor launched cleanup every tick
        Bukkit.getScheduler().runTaskTimer(plugin, new Runnable() {
            @Override
            public void run() {
                if (game.getState() != GameState.LIVE) return;
                jumpEvent();
                repulseCleanup();
                tickAbilityBars();
            }
        }, 1L, 1L);
    }

    public void openSelector(Player player) {
        gui.open(player);
    }

    public void spawnSelectors(org.bukkit.Location center) {
        npcs.spawnAt(center);
    }

    public void clearSelectors() {
        npcs.clear();
    }

    public KitType getKit(Player player) {
        KitType k = selected.get(player.getUniqueId());
        if (k == null) k = KitType.JUMPER;
        // Never hand out a QOL-only kit in a non-QOL mode (e.g. Original).
        if (k.qolOnly() && !game.qolEnabled()) {
            k = KitType.JUMPER;
        }
        return k;
    }

    public void setKit(Player player, KitType type) {
        if (type != null && type.qolOnly() && !game.qolEnabled()) {
            player.sendMessage(ChatColor.RED + "That kit is only available in QOL modes.");
            return;
        }
        selected.put(player.getUniqueId(), type);
        player.sendMessage(ChatColor.YELLOW + "Kit: " + type.display);
        if (game.getState() == GameState.STARTING || game.getState() == GameState.LIVE) {
            applyKit(player);
        }
    }

    public void clearAll() {
        selected.clear();
        jumpRecharge.clear();
        snowballConstructor.clear();
        bodyRushUntil.clear();
        bodyRushHitFeedback.clear();
        cryoFrozenUntil.clear();
        cryoCooldown.clear();
        for (Entity e : launched.keySet()) {
            if (e != null && e.isValid()) e.remove();
        }
        launched.clear();
    }

    // -------------------- Give items (exact slots) --------------------

    public void applyKit(Player player) {
        KitType kit = getKit(player);
        player.getInventory().clear();
        player.getInventory().setArmorContents(null);
        player.setAllowFlight(false);
        player.setFlying(false);
        // Fresh kit = fresh secondary-ability state (Body Rush / Cryo Blitz stand down).
        bodyRushUntil.remove(player.getUniqueId());
        bodyRushHitFeedback.remove(player.getUniqueId());
        cryoFrozenUntil.remove(player.getUniqueId());
        cryoCooldown.remove(player.getUniqueId());

        // Default visibility: visible + off every observer's ghost team. Reset any stored view mode
        // so the lime dye and the rendered view always match (a stale TRANSPARENT entry would keep
        // other players invisible even though the item says "Players Visible").
        player.removePotionEffect(PotionEffectType.INVISIBILITY);
        visMode.remove(player.getUniqueId());
        lastJumpBar.remove(player.getUniqueId());
        scoreboard.setGhost(player.getUniqueId(), false);

        // Compass always slot 4
        player.getInventory().setItem(4, named(Material.COMPASS, ChatColor.GREEN + "Safe Pad Locator"));

        // DE visibility toggle: global hotbar dye (slot 7). Default lime = visible.
        giveVisibilityDye(player, Material.LIME_DYE, ChatColor.GREEN + "Viewing: Players Visible");

        switch (kit) {
            case JUMPER:
                // slot 8: 5 feathers "5 Jumps Remaining" (3 in Speed mode)
                setJumpsLeft(player, jumperMaxJumps());
                break;
            case SLOWBALL:
                // PerkConstructor starts empty; first tick will grant snowballs
                // Give a starter stack so they can throw immediately
                player.getInventory().setItem(0, slowballItem(player.getUniqueId(), 1));
                snowballConstructor.put(player.getUniqueId(), System.currentTimeMillis());
                break;
            case BODY_BUILDER:
                // compass only (plus the Body Rush secondary item in QOL modes)
                if (game.qolEnabled()) {
                    player.getInventory().setItem(0, bodyRushItem(2));
                }
                break;
            case REPULSOR:
                // slot 0: 3 coal
                player.getInventory().setItem(0, namedAmount(Material.COAL, 3,
                        ChatColor.YELLOW + "" + ChatColor.BOLD + "Right Click"
                                + ChatColor.WHITE + ChatColor.BOLD + " - "
                                + ChatColor.GREEN + ChatColor.BOLD + "Repulse"));
                break;
        }
        player.updateInventory();
        refreshViews();
    }

    public void resetPlayerState(Player player) {
        player.setAllowFlight(false);
        player.setFlying(false);
        player.setMaxHealth(20.0);
        if (player.getHealth() > 20.0) player.setHealth(20.0);
        player.removePotionEffect(PotionEffectType.JUMP_BOOST);
        player.removePotionEffect(PotionEffectType.INVISIBILITY);
        player.removePotionEffect(PotionEffectType.SPEED);
        AttributeInstance a = player.getAttribute(Attribute.JUMP_STRENGTH);
        if (a != null && Math.abs(a.getBaseValue() - JUMP_STRENGTH_DEFAULT) > 0.0001) {
            a.setBaseValue(JUMP_STRENGTH_DEFAULT);
        }
        AttributeInstance m = player.getAttribute(Attribute.MOVEMENT_SPEED);
        if (m != null && Math.abs(m.getBaseValue() - 0.100D) > 0.0001) {
            m.setBaseValue(0.100D);
        }
        bodyRushUntil.remove(player.getUniqueId());
        bodyRushHitFeedback.remove(player.getUniqueId());
        cryoFrozenUntil.remove(player.getUniqueId());
        cryoCooldown.remove(player.getUniqueId());
        visMode.remove(player.getUniqueId());
        lastJumpBar.remove(player.getUniqueId());
        scoreboard.setGhost(player.getUniqueId(), false);
        refreshViews();
    }

    // -------------------- Jump lock + Jumper jumpEvent --------------------

    /**
     * Jump lock: every non-Jumper (and Jumper without feather charges) cannot jump.
     *
     * <p>Source used a JUMP_BOOST with amplifier -10 ("No jumping") to pin players to the
     * ground. Modern Minecraft clamps effect amplifiers to {@code [0, 255]} ({@code
     * Mth.clamp}) so -10 silently becomes level 1 — i.e. HIGHER jumps — and there is no
     * potion mechanism left that suppresses jumping. The lock is instead PHYSICAL: the
     * {@code jump_strength} attribute is set to zero, so the server never lifts the player
     * whatever the client inputs — this is the 1.21 equivalent of the -10 amp (attribute 0
     * vs effect amp -10), and unlike event cancellation it cannot desync the client's
     * prediction, so space-spam never reads as "stuck".
     *
     * <p>1.8 "speeding" (space-spam giving a ground-level forward dash across gaps) cannot be
     * reproduced on a modern client — a {@code movement_speed} raise reads as a generic speed
     * effect, and both {@code setVelocity} pushes (reverted by the client's authoritative
     * sprint-move packets) and a per-press forward teleport (found jarring) fail to match it.
     * So speeding is DROPPED entirely. In place of the dash, MODERN gives lock-pinned players a
     * plain constant speed-boost (Speed II) so they still outrun the wall the way the 1.8 dash
     * did; that boost also makes 1-block gaps crossable by raw momentum, so no hop is needed.
     * A standing gap was briefly tried (edge-timed arc) but the Speed II boost already clears
     * single-block gaps, so it was removed as redundant.
     */
    /** Vanilla jump strength (attribute default) — restored for Jumpers / out of game. */
    private static final double JUMP_STRENGTH_DEFAULT = 0.42D;

    public void tickJumpLock() {
        if (game.getState() != GameState.LIVE && game.getState() != GameState.STARTING) return;

        for (Player p : game.getAlivePlayers()) {
            boolean allowJump = getKit(p) == KitType.JUMPER && hasJumpsLeft(p);

            // A stale JUMP_BOOST must never stay on anyone (clamped amp would boost jumps).
            if (p.hasPotionEffect(PotionEffectType.JUMP_BOOST)) {
                p.removePotionEffect(PotionEffectType.JUMP_BOOST);
            }

            // The lock itself: jump_strength 0 pins locked players to the ground exactly like
            // the -10 amp did. Setting the base attribute patches the value only when it
            // actually changed (the client is fully in sync, so no snap, no fights).
            double jumpTarget = allowJump ? JUMP_STRENGTH_DEFAULT : 0.0D;
            AttributeInstance a = p.getAttribute(Attribute.JUMP_STRENGTH);
            if (a != null && Math.abs(a.getBaseValue() - jumpTarget) > 0.0001) {
                a.setBaseValue(jumpTarget);
            }

            // Constant speed-boost ("speeding") for lock-pinned players, MODERN only, to echo
            // the 1.8 ground-level dash as a plain run-speed lift (Speed II ≈ 1.4x sprint).
            // Applied to players who currently have no jumps (non-Jumper / no-charge Jumper),
            // kept off Jumpers with charges and off in every other mode. Re-applied each tick
            // so it never expires and never rides a stale potion icon.
            boolean boosting = !allowJump && game.getMode() == MazeMode.MODERN && !isPlayerLaunched(p);
            if (boosting) {
                PotionEffect speed = p.getPotionEffect(PotionEffectType.SPEED);
                if (speed == null || speed.getAmplifier() != 1 || speed.getDuration() < 400) {
                    p.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, Integer.MAX_VALUE, 1, false, false, false), true);
                }
            } else if (p.hasPotionEffect(PotionEffectType.SPEED)) {
                p.removePotionEffect(PotionEffectType.SPEED);
            }

            // Guarantee the movement-speed attribute is always its vanilla base (never left
            // boosted by any prior approach).
            AttributeInstance m = p.getAttribute(Attribute.MOVEMENT_SPEED);
            if (m != null && Math.abs(m.getBaseValue() - 0.100D) > 0.0001) {
                m.setBaseValue(0.100D);
            }
        }
    }

    /**
     * Exact MonsterMaze.jumpEvent — consume a jump charge while airborne above maze.
     * NOT a double-jump boost; vanilla jump only when charges remain.
     */
    private void jumpEvent() {
        org.bukkit.Location center = game.getCenter();
        if (center == null) return;

        for (Player p : game.getAlivePlayers()) {
            if (getKit(p) != KitType.JUMPER) continue;

            // Must have "Jumps Remaining" feather
            ItemStack feather = p.getInventory().getItem(8);
            if (feather == null || feather.getType() != Material.FEATHER || feather.getAmount() < 1) {
                continue;
            }
            // Name check (contains Jumps Remaining)
            if (feather.hasItemMeta() && feather.getItemMeta().hasDisplayName()) {
                String n = ChatColor.stripColor(feather.getItemMeta().getDisplayName());
                if (!n.contains("Jumps Remaining")) continue;
            }

            // Above maze floor
            if (p.getLocation().getY() - center.getY() <= 0) continue;

            // Recharge 750ms
            Long last = jumpRecharge.get(p.getUniqueId());
            if (last != null && System.currentTimeMillis() - last < 750L) continue;

            // Don't consume while launched by bump (isLaunched)
            if (isPlayerLaunched(p)) continue;

            // QOL: jumping while standing on a Safe Pad does not cost a charge.
            // (Speed/Modern only; Original keeps the source behaviour.)
            if (game.qolEnabled() && game.isOnAnyPad(p)) continue;

            setJumpsLeft(p, feather.getAmount() - 1);
            jumpRecharge.put(p.getUniqueId(), System.currentTimeMillis());

            p.playSound(p.getLocation(), Sound.ENTITY_CHICKEN_EGG, 1.0f, 1.0f);

            // Step sounds under player
            for (int i = 0; i < 3; i++) {
                Block under = p.getLocation().clone().subtract(0, i, 0).getBlock();
                if (under.getType() == Material.AIR) continue;
                p.getWorld().playSound(p.getLocation(), under.getSoundGroup().getStepSound(), 0.5f, 1.4f);
                for (int dx = -1; dx <= 1; dx++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        if (dx == 0 && dz == 0) continue;
                        Block adj = under.getRelative(dx, 0, dz);
                        if (adj.getType() == Material.AIR) continue;
                        adj.getWorld().playSound(adj.getLocation(), adj.getSoundGroup().getStepSound(), 0.5f, 1.4f);
                    }
                }
                break;
            }

            Bukkit.getPluginManager().callEvent(new AbilityUseEvent(p));
        }
    }

    private boolean isPlayerLaunched(Player p) {
        // Grace window after a monster bump: while being knocked back, don't consume a jump
        // charge. The old check only covered the immediate upward launch (vy > 0.6 && !onGround),
        // so once a bumped player topped out or started falling the grace dropped and spam jumping
        // consumed a charge they never earned. A 2s post-hit window comfortably covers the whole
        // knock+landing, so a knocked Jumper cannot have a jump carved off.
        Long hit = lastMobHit.get(p.getUniqueId());
        if (hit != null && System.currentTimeMillis() - hit < 2000L) return true;
        return p.getVelocity().getY() > 0.6 && !p.isOnGround();
    }

    /** True if the block at this location (feet level) would collide with a player. */
    private boolean isSolid(org.bukkit.Location loc) {
        Block b = loc.getBlock();
        if (b == null || b.getType() == Material.AIR) return false;
        return b.getType().isSolid() || b.getType() == Material.WATER || b.getType() == Material.LAVA;
    }

    private void setJumpsLeft(Player player, int jumps) {
        if (jumps <= 0) {
            player.getInventory().setItem(8, null);
        } else {
            player.getInventory().setItem(8, namedAmount(Material.FEATHER, jumps,
                    ChatColor.YELLOW + "" + ChatColor.BOLD + jumps + " Jumps Remaining"));
        }
        player.updateInventory();
    }

    private boolean hasJumpsLeft(Player p) {
        ItemStack feather = p.getInventory().getItem(8);
        return feather != null && feather.getType() == Material.FEATHER && feather.getAmount() > 0;
    }

    /** Action-bar jumps left (PerkJumpsDisplay). */
    public void tickJumperFlight() {
        if (game.getState() != GameState.LIVE) return;
        for (Player p : game.getAlivePlayers()) {
            if (getKit(p) != KitType.JUMPER) continue;
            ItemStack feather = p.getInventory().getItem(8);
            String text;
            if (feather == null || feather.getType() != Material.FEATHER || feather.getAmount() <= 0) {
                text = ChatColor.WHITE + "0 Jumps Remaining";
            } else {
                text = ChatColor.YELLOW + "" + ChatColor.BOLD
                        + feather.getAmount() + " Jumps Remaining";
            }
            // PERF: the count only changes when a jump is used, so only send the action-bar
            // packet when the rendered text actually changes.
            String last = lastJumpBar.get(p.getUniqueId());
            if (text.equals(last)) continue;
            lastJumpBar.put(p.getUniqueId(), text);
            TextUtil.actionBar(p, text);
        }
    }

    // -------------------- Slowball --------------------

    /** PerkConstructor("Slowballer", 2, 16, SNOW_BALL, "Slowball", true) — every 2s up to 16. */
    private void constructorSlowball() {
        for (Player p : game.getAlivePlayers()) {
            if (getKit(p) != KitType.SLOWBALL) continue;

            ItemStack slot = p.getInventory().getItem(0);
            if (slot != null && slot.getType() == Material.SNOWBALL && slot.getAmount() >= 16) continue;

            int amount = (slot != null && slot.getType() == Material.SNOWBALL) ? slot.getAmount() : 0;
            // Always rebuild with the live Cryo Blitz cooldown lore.
            p.getInventory().setItem(0, slowballItem(p.getUniqueId(), amount + 1));
            p.updateInventory();
        }
    }

    /**
     * KitSlowball.SnowballHit — Slow condition 2s amp 1 on hit entity.
     * NOTE: the kit slows OTHER PLAYERS, not monsters.
     */
    @EventHandler
    public void onSnowballHit(EntityDamageByEntityEvent event) {
        if (game.getState() != GameState.LIVE) return;
        if (!(event.getDamager() instanceof Snowball)) return;
        Snowball ball = (Snowball) event.getDamager();
        if (!(ball.getShooter() instanceof Player)) return;
        Player shooter = (Player) ball.getShooter();
        if (getKit(shooter) != KitType.SLOWBALL) return;
        // The kit slows OTHER PLAYERS, not monsters.
        if (!(event.getEntity() instanceof Player)) return;
        Player target = (Player) event.getEntity();
        if (target == shooter) return;
        if (!game.getAlivePlayers().contains(target)) return;

        event.setDamage(0);
        ball.remove();

        // QOL: slowball doesn't affect players who were just hit by a mob, or who are
        // above the exact floor level (e.g. jumping).
        if (game.qolEnabled()) {
            Long hitTime = lastMobHit.get(target.getUniqueId());
            boolean recentlyMobHit = hitTime != null && (System.currentTimeMillis() - hitTime) < 1000L;
            boolean aboveFloor = target.getLocation().getY() > (game.getCenter().getY() + 0.2);
            if (recentlyMobHit || aboveFloor) return;
        }

        // Manager.GetCondition().Factory().Slow(..., 2, 1, ...) → 2 seconds, amplifier 1
        target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 40, 1), true);
        target.getWorld().playSound(target.getLocation(), Sound.ENTITY_SLIME_SQUISH, 1f, 1.2f);
    }

    /** QOL: record when a mob knocks a player so slowballs can't punish them right after. */
    @EventHandler
    public void onMobBump(MonsterBumpPlayerEvent event) {
        lastMobHit.put(event.getPlayer().getUniqueId(), System.currentTimeMillis());
    }

    // -------------------- Body Builder + shared pad heals --------------------

    /**
     * Called when a player steps onto the active pad this phase.
     * Source Maze.checkPlayersOnSafePad:
     * - first player: +4 health, announce, Body Builder +2 max (cap 30)
     * - others: +2 health
     */
    public void onReachedPad(Player player, boolean first) {
        if (first) {
            // UtilPlayer.health(p, 4.0)
            heal(player, 4.0);
            player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f);
            TextUtil.title(player, "", ChatColor.YELLOW + "" + ChatColor.BOLD + "You got to the Safe Pad first!", 5, 40, 5);

            if (getKit(player) == KitType.BODY_BUILDER) {
                double max = player.getMaxHealth();
                if (max < 30.0) {
                    player.setMaxHealth(Math.min(30.0, max + 2.0));
                    // hearts particle approx
                    player.getWorld().spawnParticle(Particle.HEART, player.getEyeLocation().add(0, 0.5, 0), 6, 0.2, 0.2, 0.2, 0);
                }
            }
        } else {
            heal(player, 2.0);
            player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f);
            TextUtil.title(player, "", ChatColor.YELLOW + "" + ChatColor.BOLD + "You got to the Safe Pad!", 5, 40, 5);
        }

        // Speed/Modern (QOL) modes: Jumper jumps reset to full on every Safe Pad.
        if (game.qolEnabled() && getKit(player) == KitType.JUMPER) {
            setJumpsLeft(player, jumperMaxJumps());
        }
    }

    /** Jumper jump charges for the active mode. QOL modes (Speed/Modern) = 3 leaps, Original = 5. */
    private int jumperMaxJumps() {
        return game.qolEnabled() ? 3 : 5;
    }

    /** Legacy name used by GameManager. */
    public void onFirstToPad(Player player) {
        onReachedPad(player, true);
    }

    private void heal(Player p, double amount) {
        double nh = Math.min(p.getMaxHealth(), p.getHealth() + amount);
        p.setHealth(nh);
    }

    // -------------------- Repulsor (PerkRepulsor) --------------------

    @EventHandler
    public void onRepulse(PlayerInteractEvent event) {
        if (game.getState() != GameState.LIVE) return;
        Action a = event.getAction();
        if (a != Action.RIGHT_CLICK_AIR && a != Action.RIGHT_CLICK_BLOCK) return;
        // Modern clients right-clicking a non-usable item (coal) send a MAIN hand AND an
        // OFF hand interact in the same tick; without this the repulse would fire twice,
        // consuming two coal per click.
        if (event.getHand() == EquipmentSlot.OFF_HAND) return;

        Player player = event.getPlayer();
        if (getKit(player) != KitType.REPULSOR) return;
        if (!game.getAlivePlayers().contains(player)) return;

        ItemStack hand = player.getInventory().getItemInMainHand();
        if (hand == null || hand.getType() != Material.COAL) return;

        event.setCancelled(true);

        // Consume 1 coal
        if (hand.getAmount() <= 1) {
            player.getInventory().setItemInMainHand(null);
        } else {
            hand.setAmount(hand.getAmount() - 1);
            player.getInventory().setItemInMainHand(hand);
        }
        player.updateInventory();

        // UtilFirework BALL_LARGE AQUA
        try {
            org.bukkit.entity.Firework fw = player.getWorld().spawn(
                    player.getLocation().add(0, 0.5, 0), org.bukkit.entity.Firework.class);
            // Attribute it to the caster (firework blasts exempt their shooter from damage).
            try {
                fw.setShooter(player);
            } catch (Throwable ignored) {
            }
            org.bukkit.inventory.meta.FireworkMeta meta = fw.getFireworkMeta();
            meta.addEffect(org.bukkit.FireworkEffect.builder()
                    .with(org.bukkit.FireworkEffect.Type.BALL_LARGE)
                    .withColor(org.bukkit.Color.AQUA)
                    .build());
            meta.setPower(0);
            fw.setFireworkMeta(meta);
            Bukkit.getScheduler().runTaskLater(plugin, new Runnable() {
                @Override public void run() {
                    if (fw.isValid()) fw.detonate();
                }
            }, 2L);
        } catch (Throwable ignored) {
            player.getWorld().spawnParticle(Particle.FIREWORK, player.getLocation().add(0, 0.5, 0), 20, 0.2, 0.2, 0.2, 0.05);
        }

        // Radius 6 — living non-players
        for (Entity ent : player.getNearbyEntities(6, 6, 6)) {
            if (ent instanceof Player) continue;
            if (!(ent instanceof LivingEntity)) continue;
            if (player.getLocation().distanceSquared(ent.getLocation()) > 36) continue;

            ent.playEffect(org.bukkit.EntityEffect.HURT);
            // Exact: UtilAction.velocity(ent, UtilAlg.getTrajectory2d(player, ent), 1, true, 0, 0.8, 2, true);
            UtilAction.velocity(ent, UtilAlg.getTrajectory2d(player, ent), 1, true, 0, 0.8, 2, true);

            launched.put(ent, System.currentTimeMillis());
            Bukkit.getPluginManager().callEvent(new EntityLaunchEvent(ent));

            // Notify MonsterManager so path AI stops tracking if needed
            MonsterManager mm = game.getMonsterManager();
            if (mm != null) {
                mm.launch((LivingEntity) ent, ent.getVelocity());
            }
        }

        Bukkit.getPluginManager().callEvent(new AbilityUseEvent(player));
    }

    private void repulseCleanup() {
        if (launched.isEmpty()) return;
        Iterator<Entry<Entity, Long>> it = launched.entrySet().iterator();
        long now = System.currentTimeMillis();
        while (it.hasNext()) {
            Entry<Entity, Long> e = it.next();
            Entity en = e.getKey();
            long start = e.getValue();

            if (en == null || !en.isValid()) {
                it.remove();
                continue;
            }
            // On ground after 500ms
            if (en.isOnGround() && now - start >= 500L) {
                removeLaunched(en);
                it.remove();
                continue;
            }
            // Timeout 1500ms
            if (now - start >= 1500L) {
                removeLaunched(en);
                it.remove();
            }
        }
    }

    private void removeLaunched(Entity en) {
        if (en != null && en.isValid()) {
            try {
                org.bukkit.entity.Firework fw = en.getWorld().spawn(en.getLocation(), org.bukkit.entity.Firework.class);
                org.bukkit.inventory.meta.FireworkMeta meta = fw.getFireworkMeta();
                meta.addEffect(org.bukkit.FireworkEffect.builder()
                        .with(org.bukkit.FireworkEffect.Type.BALL)
                        .withColor(org.bukkit.Color.BLACK)
                        .build());
                meta.setPower(0);
                fw.setFireworkMeta(meta);
                Bukkit.getScheduler().runTaskLater(plugin, new Runnable() {
                    @Override public void run() {
                        if (fw.isValid()) fw.detonate();
                    }
                }, 2L);
            } catch (Throwable ignored) {
            }
            en.remove();
        }
    }

    // -------------------- Body Builder secondary: Body Rush --------------------
    // QOL-mode only. Two apples = two activations; each activation consumes one apple.
    // Body Rush lasts 10 seconds and each real mob contact removes exactly 2 seconds.

    @EventHandler
    public void onBodyRush(PlayerInteractEvent event) {
        if (game.getState() != GameState.LIVE) return;
        if (!game.qolEnabled()) return;
        Action a = event.getAction();
        if (a != Action.RIGHT_CLICK_AIR && a != Action.RIGHT_CLICK_BLOCK) return;

        Player player = event.getPlayer();
        if (getKit(player) != KitType.BODY_BUILDER) return;
        if (!game.getAlivePlayers().contains(player)) return;

        ItemStack hand = player.getInventory().getItemInMainHand();
        if (hand == null || hand.getType() != Material.APPLE) return;
        long now = System.currentTimeMillis();
        Long until = bodyRushUntil.get(player.getUniqueId());
        if (until != null && until.longValue() > now) {
            event.setCancelled(true);
            return;
        }
        event.setCancelled(true);
        if (hand.getAmount() <= 1) player.getInventory().setItemInMainHand(null);
        else { hand.setAmount(hand.getAmount()-1); player.getInventory().setItemInMainHand(hand); }
        bodyRushUntil.put(player.getUniqueId(), now + BODY_RUSH_DURATION_MS);
        bodyRushHitFeedback.remove(player.getUniqueId());
        bodyRushVisual(player);
        TextUtil.actionBar(player, ChatColor.RED + ChatColor.BOLD.toString() + "BODY RUSH 10.0s");
        Bukkit.getPluginManager().callEvent(new AbilityUseEvent(player));
    }

    public boolean isBodyRushActive(Player player) {
        if (!game.qolEnabled()) return false;
        Long until=bodyRushUntil.get(player.getUniqueId());
        if (until==null) return false;
        if (until.longValue()<=System.currentTimeMillis()) { bodyRushUntil.remove(player.getUniqueId()); return false; }
        return true;
    }

    /** A real mob contact removes exactly 2 seconds from Body Rush. */
    public int consumeBodyRushUse(Player player) {
        UUID id=player.getUniqueId();
        long now=System.currentTimeMillis();
        Long until=bodyRushUntil.get(id);
        if (until==null || until.longValue()<=now) { bodyRushUntil.remove(id); return 0; }
        long remaining=until.longValue()-2000L;
        bodyRushHitFeedback.put(id,now+750L);
        bodyRushImpactVisual(player);
        if (remaining<=now) { bodyRushUntil.remove(id); return 0; }
        bodyRushUntil.put(id,remaining);
        return (int)Math.max(0L,remaining-now);
    }

    private static ItemStack bodyRushItem(int apples) {
        ItemStack item=new ItemStack(Material.APPLE,Math.max(1,apples));
        ItemMeta meta=item.getItemMeta();
        meta.setDisplayName(ChatColor.RED+""+ChatColor.BOLD+"Body Rush");
        meta.setLore(java.util.Arrays.asList(ChatColor.GRAY+"Right Click to activate.",ChatColor.GRAY+"10 seconds of mob-contact immunity.",ChatColor.GRAY+"Each contact removes 2 seconds.",ChatColor.RED+"Activations: "+apples));
        item.setItemMeta(meta);
        return item;
    }

    private void bodyRushVisual(Player player) { player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.25f); player.getWorld().spawnParticle(Particle.FLAME, player.getLocation().add(0,1,0), 24, .35,.55,.35,.03); }
    private void bodyRushImpactVisual(Player player) { player.playSound(player.getLocation(), Sound.ENTITY_IRON_GOLEM_HURT, 1.0f, 1.35f); player.getWorld().spawnParticle(Particle.CRIT, player.getLocation().add(0,1,0), 12, .25,.35,.25,.05); }

    private void tickAbilityBars() {
        if (game.getState()!=GameState.LIVE) return;
        long now=System.currentTimeMillis();
        for(Player p:game.getAlivePlayers()) {
            UUID id=p.getUniqueId();
            Long body=bodyRushUntil.get(id);
            if(body!=null && body.longValue()>now) {
                double secs=(body.longValue()-now)/1000.0;
                Long fb=bodyRushHitFeedback.get(id);
                String suffix=fb!=null && fb.longValue()>now ? "  "+ChatColor.YELLOW+"-2.0s" : "";
                TextUtil.actionBar(p,ChatColor.RED+ChatColor.BOLD.toString()+"BODY RUSH "+String.format(java.util.Locale.US,"%.1fs",secs)+suffix);
                continue;
            }
            if(body!=null) bodyRushUntil.remove(id);
            bodyRushHitFeedback.remove(id);
            if(getKit(p)!=KitType.SLOWBALL) continue;
            Long frozen=cryoFrozenUntil.get(id);
            if(frozen!=null && frozen.longValue()>now) {
                TextUtil.actionBar(p,ChatColor.AQUA+ChatColor.BOLD.toString()+"FROZEN "+String.format(java.util.Locale.US,"%.1fs",(frozen.longValue()-now)/1000.0));
                continue;
            }
            cryoFrozenUntil.remove(id);
            Long last=cryoCooldown.get(id);
            if(last==null) TextUtil.actionBar(p,ChatColor.AQUA+ChatColor.BOLD.toString()+"CRYO BLITZ READY");
            else {
                long left=(last.longValue()+CRYO_COOLDOWN_MS)-now;
                if(left<=0) { cryoCooldown.remove(id); TextUtil.actionBar(p,ChatColor.AQUA+ChatColor.BOLD.toString()+"CRYO BLITZ READY"); }
                else TextUtil.actionBar(p,ChatColor.AQUA+ChatColor.BOLD.toString()+"CRYO BLITZ "+String.format(java.util.Locale.US,"%.1fs",left/1000.0));
            }
        }
    }

    // -------------------- Slowballer secondary: Cryo Blitz --------------------
    // QOL-mode only. MID-GAME Q-drop (the global drop-cancel otherwise eats it) freezes every
    // monster within CRYO_RADIUS blocks for CRYO_FREEZE_MS on a CRYO_COOLDOWN_MS cooldown.
    // The cooldown is surfaced as lore on the Slowball slot-0 snowballs.

    @EventHandler
    public void onCryoBlitz(PlayerDropItemEvent event) {
        if (game.getState() != GameState.LIVE) return;
        if (!game.qolEnabled()) return;
        Player player = event.getPlayer();
        if (getKit(player) != KitType.SLOWBALL) return;
        if (!game.getAlivePlayers().contains(player)) return;

        event.setCancelled(true);

        long now = System.currentTimeMillis();
        Long last = cryoCooldown.get(player.getUniqueId());
        if (last != null && now - last < CRYO_COOLDOWN_MS) {
            long left = (CRYO_COOLDOWN_MS - (now - last)) / 1000L;
            player.sendMessage(ChatColor.AQUA + "Cryo Blitz on cooldown (" + left + "s).");
            return;
        }
        cryoCooldown.put(player.getUniqueId(), now);

        MonsterManager mm = game.getMonsterManager();
        int frozenCount = 0;
        if (mm != null) {
            java.util.List<LivingEntity> list = new java.util.ArrayList<LivingEntity>();
            for (LivingEntity ent : mm.getMonsters()) list.add(ent);
            for (LivingEntity ent : list) {
                if (ent == null || !ent.isValid()) continue;
                if (player.getLocation().distanceSquared(ent.getLocation()) > CRYO_RADIUS * CRYO_RADIUS) continue;
                mm.freeze(ent, now + CRYO_FREEZE_MS);
                ent.getWorld().spawnParticle(Particle.SNOWFLAKE, ent.getLocation(), 12, 0.3, 0.6, 0.3, 0.02);
                frozenCount++;
            }
        }
        player.playSound(player.getLocation(), Sound.ENTITY_SNOWBALL_THROW, 1.0f, 0.5f);
        TextUtil.title(player, "", ChatColor.AQUA + "" + ChatColor.BOLD + "Cryo Blitz!"
                        + ChatColor.WHITE + " " + frozenCount + " mob(s) frozen", 5, 30, 5);
        if (frozenCount > 0) cryoFrozenUntil.put(player.getUniqueId(), now + CRYO_FREEZE_MS);
        cryoVisual(player);
        // NOTE: do not rewrite slot 0 here. The dropped item is restored by the cancelled drop,
        // and swapping the slot's ItemStack before the restore could make 1.8 place the restored
        // item in the next free slot (an extra snowball). The PerkConstructor tick refreshes the
        // cooldown lore within 2s.
        Bukkit.getPluginManager().callEvent(new AbilityUseEvent(player));
    }

    private void cryoVisual(Player player) { player.playSound(player.getLocation(), Sound.BLOCK_GLASS_BREAK, 1.0f, .7f); player.getWorld().spawnParticle(Particle.SNOWFLAKE, player.getLocation().add(0,1,0), 80, 3,1,3,.04); }

    private ItemStack slowballItem(UUID playerId, int amount) {
        ItemStack item = new ItemStack(Material.SNOWBALL, Math.max(1, amount));
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(ChatColor.AQUA + "Slowball");
        meta.setLore(cooldownLore(playerId));
        item.setItemMeta(meta);
        return item;
    }

    private java.util.List<String> cooldownLore(UUID playerId) {
        long now = System.currentTimeMillis();
        long rem = -1;
        Long t = cryoCooldown.get(playerId);
        if (t != null) {
            rem = (t.longValue() + CRYO_COOLDOWN_MS) - now;
        }
        java.util.List<String> lore = new java.util.ArrayList<String>();
        lore.add(ChatColor.GRAY + "Q-drop: Cryo Blitz (freeze " + CRYO_RADIUS + " blocks).");
        if (rem >= 0) {
            lore.add(ChatColor.AQUA + "Cryo Blitz ready in " + (rem / 1000L) + "s");
        } else {
            lore.add(ChatColor.AQUA + "Cryo Blitz ready!");
        }
        return lore;
    }

    // -------------------- DE view-of-others dye toggle --------------------
    // Global hotbar item (slot 7). Right-click cycles how THIS player views every other player:
    //   LIME  (data 10) -> visible: see the rest of the field normally (solid)
    //   GREY  (data  8) -> invisible: other players are HIDDEN from you
    //   PURPLE(data  5) -> transparent: see other players as see-through ghosts
    // The maze snowmen bump by proximity, so this never helps you avoid mobs.

    @EventHandler
    public void onDyeToggle(PlayerInteractEvent event) {
        if (game.getState() != GameState.LIVE && game.getState() != GameState.STARTING) return;
        Action a = event.getAction();
        if (a != Action.RIGHT_CLICK_AIR && a != Action.RIGHT_CLICK_BLOCK) return;
        if (event.getHand() == EquipmentSlot.OFF_HAND) return;

        Player player = event.getPlayer();
        if (!game.getAlivePlayers().contains(player)) return;

        ItemStack item = player.getInventory().getItemInMainHand();
        if (item == null) return;
        Material type = item.getType();
        if (type != Material.LIME_DYE && type != Material.GRAY_DYE && type != Material.PURPLE_DYE) return;

        event.setCancelled(true);

        // Lime -> visible, grey -> hidden, purple -> transparent
        Material[] order = { Material.LIME_DYE, Material.GRAY_DYE, Material.PURPLE_DYE };
        String[] names = {
                ChatColor.GREEN + "Viewing: Players Visible",
                ChatColor.GRAY + "Viewing: Players Hidden",
                ChatColor.LIGHT_PURPLE + "Viewing: Players Transparent"
        };
        int idx = 0;
        for (int i = 0; i < order.length; i++) if (order[i] == type) { idx = i; break; }
        int next = (idx + 1) % order.length;

        giveVisibilityDye(player, order[next], names[next]);
        applyVisibilityState(player, order[next]);
    }

    private void giveVisibilityDye(Player player, Material dye, String name) {
        player.getInventory().setItem(7, namedAmount(dye, 1, name));
        player.updateInventory();
    }

    /** Record the observer's chosen view of others and re-render everyone's view. */
    private void applyVisibilityState(Player observer, Material dye) {
        VisMode mode;
        if (dye == Material.GRAY_DYE) mode = VisMode.INVISIBLE;
        else if (dye == Material.PURPLE_DYE) mode = VisMode.TRANSPARENT;
        else mode = VisMode.VISIBLE;
        if (mode == VisMode.VISIBLE) visMode.remove(observer.getUniqueId());
        else visMode.put(observer.getUniqueId(), mode);
        refreshViews();
    }

    /** Current view mode for a player (default VISIBLE). */
    private VisMode visModeFor(Player p) {
        VisMode m = visMode.get(p.getUniqueId());
        return m == null ? VisMode.VISIBLE : m;
    }

    /** Re-render what every player sees. Players in TRANSPARENT mode need their targets to carry
     *  INVISIBILITY amp 1 (a global effect) so their body shows through as a see-through ghost;
     *  the observer picks each target up on their own mm_ghosts team to render it. We apply the
     *  effect to exactly the set of "targets of any transparent observer" and clear it from
     *  everyone else, then set each observer's own show/hide + per-observer ghost team. */
    private void refreshViews() {
        java.util.List<Player> alive = game.getAlivePlayers();
        if (alive.isEmpty()) return;

        // Which players must carry INVISIBILITY? Every player that is a target of some transparent
        // observer (i.e. every OTHER player of a transparent observer). A sole player has no targets.
        Set<UUID> needInvis = new HashSet<UUID>();
        for (Player ob : alive) {
            if (visModeFor(ob) != VisMode.TRANSPARENT) continue;
            for (Player t : alive) {
                if (t == ob) continue;
                needInvis.add(t.getUniqueId());
            }
        }
        for (Player p : alive) {
            // Transparent observers need their targets to render a see-through body, which 1.8
            // needs INVISIBILITY at amplifier 1 (level II). Hide-only (grey) observers use
            // hidePlayer for their own view, so the target's effect doesn't need amp 0.
            setInvisible(p, needInvis.contains(p.getUniqueId()));
        }

        // Apply each observer's view of the rest of the field.
        for (Player ob : alive) {
            VisMode mode = visModeFor(ob);
            for (Player t : alive) {
                if (t == ob) continue;
                switch (mode) {
                    case INVISIBLE:
                        ob.hidePlayer(t);
                        scoreboard.setGhostFor(ob.getUniqueId(), t.getUniqueId(), false);
                        break;
                    case TRANSPARENT:
                        ob.showPlayer(t);
                        scoreboard.setGhostFor(ob.getUniqueId(), t.getUniqueId(), true);
                        break;
                    default: // VISIBLE
                        ob.showPlayer(t);
                        scoreboard.setGhostFor(ob.getUniqueId(), t.getUniqueId(), false);
                        break;
                }
            }
        }
    }

    /** INVISIBILITY amp 1 (level II) = transparent ghost body (see-through). Remove when false. */
    private void setInvisible(Player p, boolean invisible) {
        if (invisible) {
            // Amp 1 / level II: the player's body stays faintly visible (see-through), unlike
            // amp 0 which fully hides it. Transparent observers SE this body via their ghost team.
            p.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY, 9999999, 1, true, false), true);
        } else {
            p.removePotionEffect(PotionEffectType.INVISIBILITY);
        }
    }

    // -------------------- Inventory lock --------------------

    @EventHandler
    public void onDrop(PlayerDropItemEvent event) {
        if (game.getState() == GameState.LIVE || game.getState() == GameState.STARTING) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onInvClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;
        if (game.getState() != GameState.LIVE && game.getState() != GameState.STARTING) return;
        Player p = (Player) event.getWhoClicked();
        if (!game.getAlivePlayers().contains(p)) return;
        // Allow kit GUI only
        if (event.getInventory() != null && event.getView().getTitle() != null
                && event.getView().getTitle().equals(KitGUI.TITLE)) {
            return;
        }
        event.setCancelled(true);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID id = event.getPlayer().getUniqueId();
        jumpRecharge.remove(id);
        snowballConstructor.remove(id);
        bodyRushUntil.remove(id);
        cryoCooldown.remove(id);
        visMode.remove(id);
        scoreboard.setGhost(id, false);
        // Re-render remaining players' views of each other now that this player has left.
        Bukkit.getScheduler().runTaskLater(plugin, new Runnable() {
            @Override public void run() { refreshViews(); }
        }, 1L);
    }

    // -------------------- Helpers --------------------

    private static ItemStack named(Material mat, String name) {
        return namedAmount(mat, 1, name);
    }

    private static ItemStack namedAmount(Material mat, int amount, String name) {
        ItemStack item = new ItemStack(mat, amount);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(name);
        item.setItemMeta(meta);
        return item;
    }
}
