package me.monstermaze.game;

import me.monstermaze.MonsterMazePlugin;
import me.monstermaze.entity.MonsterManager;
import me.monstermaze.event.FirstToSafepadEvent;
import me.monstermaze.event.SafepadBuildEvent;
import me.monstermaze.kit.KitManager;
import me.monstermaze.maze.MazeGenerator;
import me.monstermaze.stats.StatTracker;
import me.monstermaze.stats.LeaderboardBoard;
import me.monstermaze.util.GameScoreboard;
import me.monstermaze.util.TextUtil;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Full game loop closer to original Mineplex Monster Maze.
 */
public class GameManager implements Listener {

    private final MonsterMazePlugin plugin;
    private final MazeGenerator mazeGenerator;
    private final MonsterManager monsterManager;
    private final KitManager kitManager;
    private final StatTracker stats;
    private final GameScoreboard scoreboard = new GameScoreboard();
    private final LeaderboardBoard leaderboardBoard;

    private GameState state = GameState.IDLE;
    private Location center;
    private long liveStartMs;

    /** Active game mode (Original / Speed / Modern). */
    private MazeMode mode = MazeMode.ORIGINAL;

    private final Set<UUID> alive = new HashSet<UUID>();
    private final Set<UUID> spectators = new HashSet<UUID>();
    private final List<Player> playersOnPad = new ArrayList<Player>();

    private SafePad safePad;          // active pad
    private SafePad nextSafePad;      // preview pad (spawns at t=2)
    private final LinkedList<SafePad> oldSafePads = new LinkedList<SafePad>();

    private int curSafe = 1;
    private int phaseTimer = 60;
    private int phaseTimerStart = 60;
    private int centerSafeZoneDecay = 11;
    private boolean firstClaimedThisPhase;
    private boolean soloMode;

    private BukkitTask mainTask;
    private BukkitTask secondTask;
    private BukkitTask startingTask;   // runs the jump lock during STARTING (pre-glass fall)

    /** PERF: cached per-tick packet targets so we only send what actually changed. */
    private int liveTick;
    private float lastExpPct = -1;
    private int lastLevel = -1;
    private Location lastCompassTarget;

    public GameManager(MonsterMazePlugin plugin) {
        this.plugin = plugin;
        this.mazeGenerator = new MazeGenerator(plugin);
        this.monsterManager = new MonsterManager(plugin, this);
        this.kitManager = new KitManager(plugin, this, scoreboard);
        this.stats = new StatTracker(this, plugin);
        this.leaderboardBoard = new LeaderboardBoard(plugin);
        Bukkit.getPluginManager().registerEvents(this, plugin);
        new ArenaListener(plugin, this);
    }

    public GameState getState() { return state; }
    public boolean isRunning() { return state != GameState.IDLE && state != GameState.ENDING; }
    public boolean isLive() { return state == GameState.LIVE; }
    public int getStage() { return curSafe; }
    public void setCenter(Location loc) {
        this.center = loc.clone();
        // Empty world + small pre-game lobby box only
        mazeGenerator.buildLobby(center);
        // Kit NPCs in lobby while idle
        if (state == GameState.IDLE) {
            kitManager.clearSelectors();
            kitManager.spawnSelectors(mazeGenerator.getLobbyCenter());
            refreshLeaderboardBoard();
        }
    }

    /** Call on enable: void spawn becomes lobby center. */
    public void bootstrapLobby(Location voidSpawn) {
        if (voidSpawn == null) return;
        this.center = voidSpawn.clone();
        mazeGenerator.buildLobby(center);
        kitManager.clearSelectors();
        kitManager.spawnSelectors(mazeGenerator.getLobbyCenter());
        refreshLeaderboardBoard();
    }

    public Location getLobbySpawn() {
        if (mazeGenerator.getLobbyCenter() != null) {
            // Feet on the elevated lobby platform
            return mazeGenerator.getLobbyCenter().add(0, 1, 0);
        }
        if (center != null) {
            return center.clone().add(0.5, 1, 0.5);
        }
        return null;
    }

    public void sendToLobby(Player player) {
        if (center == null) {
            // Should have been bootstrapped; last resort
            return;
        }
        if (state == GameState.IDLE || state == GameState.ENDING) {
            player.teleport(getLobbySpawn());
            player.setGameMode(GameMode.SURVIVAL);
            kitManager.resetPlayerState(player);
            player.getInventory().clear();
            // Ensure NPCs exist
            if (state == GameState.IDLE) {
                kitManager.spawnSelectors(mazeGenerator.getLobbyCenter());
            }
            player.sendMessage(ChatColor.GOLD + "Monster Maze lobby");
            player.sendMessage(ChatColor.YELLOW + "Pick a kit (villagers or /mm kit), then an admin runs /mm start");
            // Open kit GUI shortly after TP
            final Player p = player;
            Bukkit.getScheduler().runTaskLater(plugin, new Runnable() {
                @Override public void run() {
                    if (p.isOnline() && state == GameState.IDLE) {
                        kitManager.openSelector(p);
                    }
                }
            }, 10L);
        }
    }
    public Location getCenter() { return center; }
    public MonsterManager getMonsterManager() { return monsterManager; }
    public KitManager getKitManager() { return kitManager; }
    public MazeGenerator getMazeGenerator() { return mazeGenerator; }
    public long getGameLiveTime() { return liveStartMs; }

    /** Index (0/1/2) of the maze pattern loaded for the current game, or -1 if not live. */
    public int getPatternIndex() {
        return state == GameState.LIVE || state == GameState.STARTING
                ? mazeGenerator.getPatternIndex()
                : -1;
    }

    public MazeMode getMode() {
        return mode;
    }

    /** True for any mode that receives gameplay QOL fixes (everything except Original). */
    public boolean qolEnabled() {
        return mode != MazeMode.ORIGINAL;
    }

    // -------------------- Start / Stop --------------------

public void startGame() {
    startGame(null, -1);
}

public void startGame(int requestedPattern) {
    startGame(null, requestedPattern);
}

/** @param preferCenter optional player location when /mm start is used without setcenter */
public void startGame(Location preferCenter) {
    startGame(preferCenter, -1);
}

public void startGame(Location preferCenter, int requestedPattern) {
    if (state != GameState.IDLE && state != GameState.ENDING) return;

    if (center == null) {
        if (preferCenter != null) {
            center = preferCenter.clone();
        } else if (!Bukkit.getOnlinePlayers().isEmpty()) {
            center = Bukkit.getOnlinePlayers().iterator().next().getLocation();
        } else {
            broadcast(ChatColor.RED + "[MonsterMaze] No center set. Stand somewhere and /mm setcenter");
            return;
        }
    }

        // MazeGenerator needs its own center + lobby before generate
        if (mazeGenerator.getCenter() == null) {
            mazeGenerator.buildLobby(center);
        }

        cleanupEntities();
        alive.clear();
        spectators.clear();
        playersOnPad.clear();
        curSafe = 1;
        phaseTimer = 60;
        phaseTimerStart = 60;
        centerSafeZoneDecay = 11;
        firstClaimedThisPhase = false;
        soloMode = false;
        stats.reset();

        // Sync active mode from the plugin's persisted config each game.
        this.mode = plugin.getMode();
        phaseTimer = stageTimer(1);
        phaseTimerStart = phaseTimer;

        state = GameState.STARTING;
        broadcast(ChatColor.GOLD + "" + ChatColor.BOLD + "=== Monster Maze ===");
        broadcast(ChatColor.YELLOW + "Generating maze... (server stays responsive)");

        // Chunked generation — continues when maze is ready
        mazeGenerator.generateMazeAsync(requestedPattern, new Runnable() {
            @Override
            public void run() {
                if (state != GameState.STARTING) return;

                for (Player p : Bukkit.getOnlinePlayers()) {
                    if (p.getGameMode() == GameMode.SPECTATOR) continue;
                    alive.add(p.getUniqueId());
                    preparePlayer(p);
                    p.teleport(center.clone().add(0.5, 1, 0.5));
                }

                kitManager.spawnSelectors(mazeGenerator.getLobbyCenter());
                for (Player p : getAlivePlayers()) {
                    kitManager.openSelector(p);
                    p.sendMessage(ChatColor.YELLOW + "Choose a kit! Click a villager or use the menu.");
                }

                if (alive.isEmpty()) {
                    forceStop();
                    return;
                }

                soloMode = alive.size() == 1;
                if (soloMode) {
                    broadcast(ChatColor.AQUA + "Solo mode: survive as long as you can!");
                }

                spawnSafePad();
                safePad = nextSafePad;
                nextSafePad = null;

                // Point every alive player's compass at the active safe pad / beacon from spawn.
                updateCompasses();

                // --- FIX: INITIALIZE & APPLY SCOREBOARD UPFRONT (Issue #5) ---
                scoreboard.create();
                int pattern = getPatternIndex();
                for (Player p : getAlivePlayers()) {
                    String pbText = null;
                    if (pattern >= 0) {
                        me.monstermaze.stats.LeaderboardManager.PBInfo best =
                                plugin.getLeaderboards().getBest(plugin.getMode(), pattern, p.getUniqueId());
                        if (best != null) {
                            String kitName = best.kit != null ? " (" + best.kit + ")" : "";
                            pbText = best.stage + kitName;
                        }
                    }
                    scoreboard.update(p, getAlivePlayers().size(), curSafe, phaseTimer, safePad != null,
                            getMode().color + getMode().id, pbText);
                }
                scoreboard.apply(getAlivePlayers());
                // -------------------------------------------------------------

                // Spawn maze monsters now (behind the containment glass) so players see them
                // during the countdown; their movement only begins once the game goes LIVE
                // (MonsterManager.move() is gated on live state, and dropContainment() runs
                // at the top of beginLive() so the glass is gone before they start advancing).
                monsterManager.start(mazeGenerator);

                // Lock jumping during the STARTING countdown too (pre-glass-fall): only the
                // Jumper kit can move around the spawn; everyone else is grounded. The LIVE
                // mainTask calls tickJumpLock() once the game starts, so we stop this one then.
                startingTask = Bukkit.getScheduler().runTaskTimer(plugin, new Runnable() {
                    @Override public void run() {
                        if (state != GameState.STARTING) return;
                        kitManager.tickJumpLock();
                    }
                }, 1L, 1L);

                new BukkitRunnable() {
                    int t = 3;
                    @Override public void run() {
                        if (state != GameState.STARTING) { cancel(); return; }
                        if (t <= 0) { beginLive(); cancel(); return; }
                        broadcast(ChatColor.YELLOW + "Starting in " + ChatColor.WHITE + t + "...");
                        TextUtil.titleAll("", ChatColor.YELLOW + "" + t, 0, 15, 5);
                        t--;
                    }
                }.runTaskTimer(plugin, 10L, 20L);
            }
        });
    }

    private void beginLive() {
        state = GameState.LIVE;
        if (startingTask != null) { startingTask.cancel(); startingTask = null; }
        liveStartMs = System.currentTimeMillis();
        mazeGenerator.dropContainment();
        kitManager.clearSelectors();
        leaderboardBoard.clear();
        for (Player p : getAlivePlayers()) p.closeInventory();

        TextUtil.titleAll("", ChatColor.YELLOW + "" + ChatColor.BOLD + "Get to the Safe Pad!", 5, 40, 5);
        broadcast(ChatColor.GREEN + "" + ChatColor.BOLD + "GO!");

        secondTask = Bukkit.getScheduler().runTaskTimer(plugin, new Runnable() {
            @Override public void run() {
                if (state != GameState.LIVE) return;
                decrementSafePadTime();
                decrementPhaseTime();
                if (System.currentTimeMillis() - liveStartMs >= 20000L) {
                    deteriorateCenter();
                }
            }
        }, 20L, 20L);

        mainTask = Bukkit.getScheduler().runTaskTimer(plugin, new Runnable() {
            @Override public void run() {
                if (state != GameState.LIVE) return;
                liveTick++;
                List<Player> aliveNow = getAlivePlayers();
                checkPlayersOnSafePad();
                updateCompasses();
                updateExpBars(aliveNow);
                kitManager.tickJumperFlight();
                kitManager.tickJumpLock();

                // PERF: the scoreboard/KB/pad info only changes on stage/phase transitions
                // (and the PB lookup is per player), so update it 2x/s instead of 20x/s.
                if ((liveTick % 10) == 1) {
                    int pattern = getPatternIndex();
                    int aliveCount = aliveNow.size();
                    for (Player p : aliveNow) {
                        String pbText = null;
                        if (pattern >= 0) {
                            me.monstermaze.stats.LeaderboardManager.PBInfo best =
                                    plugin.getLeaderboards().getBest(plugin.getMode(), pattern, p.getUniqueId());
                            if (best != null) {
                                String kitName = best.kit != null ? " (" + best.kit + ")" : "";
                                pbText = best.stage + kitName;
                            }
                        }
                        scoreboard.update(p, aliveCount, curSafe, phaseTimer, safePad != null,
                                getMode().color + getMode().id, pbText);
                    }
                    scoreboard.apply(aliveNow);
                }
            }
        }, 1L, 1L);
    }

    public void forceStop() {
        state = GameState.ENDING;
        if (mainTask != null) { mainTask.cancel(); mainTask = null; }
        if (secondTask != null) { secondTask.cancel(); secondTask = null; }
        if (startingTask != null) { startingTask.cancel(); startingTask = null; }

        monsterManager.stop();
        kitManager.clearSelectors();
        destroyAllPads();
        mazeGenerator.returnToLobby();
        scoreboard.clear(new ArrayList<Player>(Bukkit.getOnlinePlayers()));

        for (Player p : Bukkit.getOnlinePlayers()) {
            p.setExp(0); p.setLevel(0);
            kitManager.resetPlayerState(p);
            p.setGameMode(GameMode.SURVIVAL);
            
            // Restore visibility for all players in lobby
            for (Player target : Bukkit.getOnlinePlayers()) {
                p.showPlayer(target);
            }

            Location lobby = getLobbySpawn();
            if (lobby != null) {
                p.teleport(lobby);
            }
        }
        alive.clear();
        spectators.clear();
        state = GameState.IDLE;
        // Restore lobby kit NPCs
        if (center != null) {
            kitManager.spawnSelectors(mazeGenerator.getLobbyCenter());
            refreshLeaderboardBoard();
            for (Player p : Bukkit.getOnlinePlayers()) {
                kitManager.openSelector(p);
            }
        }
        broadcast(ChatColor.GREEN + "Back in lobby. Pick a kit, then /mm start");
    }

    /** Rebuild + redraw the lobby leaderboard hologram. Safe to call outside a live game. */
    private void refreshLeaderboardBoard() {
        if (mazeGenerator.getLobbyCenter() != null) {
            leaderboardBoard.place(mazeGenerator.getLobbyCenter());
            leaderboardBoard.render(plugin.getMode());
        } else {
            leaderboardBoard.remove();
        }
    }

    /** Public hook to redraw the lobby board (e.g. after a mode change). */
    public void rerenderLeaderboardBoard() {
        if (state == GameState.IDLE || state == GameState.ENDING) {
            refreshLeaderboardBoard();
        }
    }

    private void cleanupEntities() {
        monsterManager.stop();
        destroyAllPads();
    }

    // -------------------- Safe pad pipeline (original-style) --------------------

    private List<Location> avoidPadLocations() {
        List<Location> avoid = new ArrayList<Location>();
        if (safePad != null) avoid.add(safePad.getLocation());
        for (SafePad p : oldSafePads) avoid.add(p.getLocation());
        if (nextSafePad != null) avoid.add(nextSafePad.getLocation());
        return avoid;
    }

    /** Build next pad (preview). */
    public void spawnSafePad() {
        Location next = mazeGenerator.randomPadLocation(avoidPadLocations());
        nextSafePad = new SafePad(next, qolEnabled());
        mazeGenerator.disablePadArea(next);

        // Remove mobs standing on the new pad
        monsterManager.removeMonstersOn(nextSafePad);

        Bukkit.getPluginManager().callEvent(new SafepadBuildEvent());
    }

    private void stopSafePad() {
        if (safePad != null) {
            safePad.turnOffBeacon();
            oldSafePads.add(safePad);
            safePad = null;
        }
    }

    private void decrementSafePadTime() {
        Iterator<SafePad> it = oldSafePads.iterator();
        while (it.hasNext()) {
            SafePad pad = it.next();
            if (!pad.decay()) continue;
            mazeGenerator.enablePadArea(pad.getLocation());
            it.remove();
        }
    }

    /** Timer length (seconds) for a given stage, depending on the active mode.
     *  Original: 60 -> 15 floor (minus 2s per prior stage).
     *  Modern:  35 -> 15 floor, reached by stage 10.
     */
    private int stageTimer(int stage) {
        if (mode == MazeMode.MODERN) {
            return Math.max(15, 35 - ((stage - 1) * 20 / 9));
        }
        return Math.max(15, 60 - ((stage - 1) * 2));
    }

    private void decrementPhaseTime() {
        if (safePad == null) return;
        if (phaseTimer == -1) return;

        phaseTimer--;

        if (phaseTimer == 20 || phaseTimer == 15 || phaseTimer == 10
                || phaseTimer == 5 || phaseTimer == 4) {
            TextUtil.titleAll("", ChatColor.GREEN + "" + ChatColor.BOLD + phaseTimer, 5, 40, 5);
        }
        if (phaseTimer == 3) {
            TextUtil.titleAll("", ChatColor.YELLOW + "" + ChatColor.BOLD + phaseTimer, 5, 40, 5);
        }
        if (phaseTimer == 2) {
            TextUtil.titleAll("", ChatColor.GOLD + "" + ChatColor.BOLD + phaseTimer, 5, 40, 5);
            spawnSafePad(); // next pad appears
        }
        if (phaseTimer == 1) {
            TextUtil.titleAll("", ChatColor.RED + "" + ChatColor.BOLD + phaseTimer, 5, 40, 5);
        }

        if (phaseTimer == 0) {
            for (Player p : getAlivePlayers()) {
                // Survival requires being ON the pad right now. (See note: the older
                // playersOnPad "ever touched" check wrongly let players who stepped on the
                // pad then walked off it survive the round end.)
                boolean onPad = safePad != null && safePad.isOn(p);

                if (onPad) {
                    TextUtil.title(p, "", ChatColor.YELLOW + "" + ChatColor.BOLD + "Get to the Next Safe Pad!", 5, 40, 5);
                } else {
                    TextUtil.title(p, "", ChatColor.RED + "" + ChatColor.BOLD + "You weren't on the Safe Pad!", 5, 40, 5);
                    p.sendMessage(ChatColor.RED + "You weren't on the Safe Pad!");
                    eliminate(p, ChatColor.RED + p.getName() + " missed the Safe Pad!");
                }
            }

            monsterManager.spawnMore(getMode() == MazeMode.MODERN ? 30 : 15);
            stopSafePad();
            playersOnPad.clear();
            firstClaimedThisPhase = false;

            phaseTimerStart = stageTimer(curSafe + 1);
            phaseTimer = phaseTimerStart;

            if (nextSafePad == null) spawnSafePad();

            monsterManager.removeMonstersOn(nextSafePad);

            curSafe++;
            safePad = nextSafePad;
            nextSafePad = null;

            checkWin();
        }
    }

    private void checkPlayersOnSafePad() {
        if (safePad == null || state != GameState.LIVE) return;

        boolean allOn = true;
        for (Player p : getAlivePlayers()) {
            if (!safePad.isOn(p)) {
                allOn = false;
                if (playersOnPad.contains(p)) {
                    TextUtil.title(p, "", ChatColor.RED + "" + ChatColor.BOLD + "Get back to the Safe Pad!", 0, 5, 0);
                }
                continue;
            }

            // Always check Pilot when standing on the safe pad
            stats.checkPilotLand(p, true);

            if (!playersOnPad.contains(p)) {
                playersOnPad.add(p);
                boolean first = !firstClaimedThisPhase;
                if (first) {
                    firstClaimedThisPhase = true;
                    Bukkit.getPluginManager().callEvent(new FirstToSafepadEvent(p));
                    broadcast(ChatColor.GREEN + p.getName() + ChatColor.YELLOW + " reached the Safe Pad first!");
                    int decreased = Math.max(6, 16 - (curSafe - 1));
                    phaseTimer = Math.min(decreased, phaseTimer);
                    for (Player other : getAlivePlayers()) {
                        if (other.equals(p)) continue;
                        other.sendMessage(ChatColor.YELLOW + "You have " + ChatColor.WHITE
                                + decreased + " Seconds" + ChatColor.YELLOW + " to make it to the Safe Pad!");
                    }
                } else {
                    p.sendMessage(ChatColor.YELLOW + "You made it to the Safe Pad!");
                }
                kitManager.onReachedPad(p, first);
            }
        }

        if (allOn && !getAlivePlayers().isEmpty()) {
            phaseTimer = Math.min(4, phaseTimer);
        }
    }

    // -------------------- Center deterioration --------------------

    @SuppressWarnings("deprecation")
    private void deteriorateCenter() {
        List<Location> zone = mazeGenerator.getCenterSafeZone();
        if (zone.isEmpty() || centerSafeZoneDecay == -1) return;

        centerSafeZoneDecay--;
        Material clay = Material.LIME_TERRACOTTA;
        if (centerSafeZoneDecay <= 8 && centerSafeZoneDecay > 6) clay = Material.YELLOW_TERRACOTTA;
        else if (centerSafeZoneDecay <= 6 && centerSafeZoneDecay > 4) clay = Material.ORANGE_TERRACOTTA;
        else if (centerSafeZoneDecay <= 4 && centerSafeZoneDecay > 1) clay = Material.RED_TERRACOTTA;

        Iterator<Location> it = zone.iterator();
        while (it.hasNext()) {
            Location cur = it.next();
            Block floor = cur.getBlock().getRelative(0, -1, 0);
            if (centerSafeZoneDecay == 1) {
                it.remove();
                if (mazeGenerator.getCenterSafeZonePaths().contains(cur)) {
                    // Real path cell: rebuild as maze block and re-enable its waypoint
                    // (the maze shows through underneath).
                    floor.setType(Material.QUARTZ_BLOCK, false);
                    mazeGenerator.enableWaypoint(cur);
                } else {
                    // Decorative/barrier center cell: falls away into the void.
                    floor.setType(Material.AIR, false);
                }
            } else if (floor.getType() == Material.LIME_TERRACOTTA
                    || floor.getType() == Material.YELLOW_TERRACOTTA
                    || floor.getType() == Material.ORANGE_TERRACOTTA
                    || floor.getType() == Material.RED_TERRACOTTA
                    || floor.getType() == Material.QUARTZ_BLOCK) {
                floor.setType(clay, false);
            }
        }

        if (centerSafeZoneDecay == 1) centerSafeZoneDecay = -1;
    }

    // -------------------- Players --------------------

    public boolean isOnAnyPad(Player player) {
        if (safePad != null && safePad.isActive() && safePad.isOn(player)) return true;
        if (nextSafePad != null && nextSafePad.isActive() && nextSafePad.isOn(player)) return true;
        for (SafePad pad : oldSafePads) {
            if (pad.isActive() && pad.isOn(player)) return true;
        }
        return false;
    }

    /** The pad mobs should direct a Maverick player toward (active pad, else the preview pad). */
    public Location getMobKnockTarget() {
        SafePad t = safePad != null ? safePad : nextSafePad;
        return t == null ? null : t.getLocation();
    }

    private void destroyAllPads() {
        if (safePad != null) { safePad.destroy(); safePad = null; }
        if (nextSafePad != null) { nextSafePad.destroy(); nextSafePad = null; }
        for (SafePad pad : oldSafePads) pad.destroy();
        oldSafePads.clear();
    }

    private void preparePlayer(Player p) {
        p.setGameMode(GameMode.SURVIVAL);
        kitManager.resetPlayerState(p);
        p.setHealth(p.getMaxHealth());
        p.setFoodLevel(20);
        try { p.setSaturation(20f); } catch (Throwable ignored) {}
        p.setFireTicks(0);
        p.setExp(0.99f);
        p.setLevel(0);
        
        // Ensure all alive players are visible to each other
        for (Player online : Bukkit.getOnlinePlayers()) {
            p.showPlayer(online);
            online.showPlayer(p);
        }

        kitManager.applyKit(p);
    }

    public List<Player> getAlivePlayers() {
        List<Player> list = new ArrayList<Player>();
        for (UUID id : new HashSet<UUID>(alive)) {
            Player p = Bukkit.getPlayer(id);
            if (p != null && p.isOnline()) list.add(p);
            else alive.remove(id);
        }
        return list;
    }

    /** Record this player's highest stage reached for the active mode+pattern (persisted). */
    private void recordPB(Player player) {
        int pattern = getPatternIndex();
        if (pattern < 0) return;
        String kit = null;
        me.monstermaze.kit.KitType k = kitManager.getKit(player);
        if (k != null) kit = k.id;
        plugin.getLeaderboards().recordRun(plugin.getMode(), pattern, player.getUniqueId(), curSafe, kit);
    }

    private void eliminate(Player player, String message) {
        if (!alive.remove(player.getUniqueId())) return;
        recordPB(player);
        spectators.add(player.getUniqueId());
        broadcast(message);
        kitManager.resetPlayerState(player);
        player.setGameMode(GameMode.SPECTATOR);
        player.getInventory().clear();
        player.playSound(player.getLocation(), Sound.ENTITY_WITHER_SPAWN, 0.5f, 1.5f);

        // Hide eliminated player from all living players so they don't block screens
        for (UUID aliveId : alive) {
            Player alivePlayer = Bukkit.getPlayer(aliveId);
            if (alivePlayer != null && alivePlayer.isOnline()) {
                alivePlayer.hidePlayer(player);
            }
        }
        
        // Ensure other spectators can still see them
        for (UUID specId : spectators) {
            Player specPlayer = Bukkit.getPlayer(specId);
            if (specPlayer != null && specPlayer.isOnline()) {
                specPlayer.showPlayer(player);
                player.showPlayer(specPlayer);
            }
        }

        // Put eliminated spectators on the maze centre platform (has a floor) so they
        // don't fall into the void; they can fly over to watch the remaining survivors.
        if (center != null) player.teleport(center.clone().add(0, 1, 0));
        checkWin();
    }

    private void checkWin() {
        if (state != GameState.LIVE) return;
        List<Player> remaining = getAlivePlayers();

        // Solo: keep going until the player dies (0 remaining)
        if (soloMode) {
            if (remaining.isEmpty()) {
                state = GameState.ENDING;
                broadcast(ChatColor.GOLD + "Solo run over — reached stage " + curSafe + "!");
                Bukkit.getScheduler().runTaskLater(plugin, new Runnable() {
                    @Override public void run() { forceStop(); }
                }, 80L);
            }
            return;
        }

        // Multiplayer: last player standing wins
        if (remaining.size() <= 1) {
            if (remaining.size() == 1) {
                Player winner = remaining.get(0);
                
                // Record winner's PB BEFORE setting state to ENDING
                recordPB(winner);
                
                broadcast(ChatColor.GOLD + "" + ChatColor.BOLD + winner.getName()
                        + " wins Monster Maze! (Stage " + curSafe + ")");
                TextUtil.title(winner, ChatColor.GOLD + "Victory!", ChatColor.YELLOW + "Stage " + curSafe, 10, 60, 10);
                stats.announceWinners(winner);
                winner.playSound(winner.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1f);
            } else {
                broadcast(ChatColor.GOLD + "No winners...");
            }
            
            // Transition state after recording
            state = GameState.ENDING;
            Bukkit.getScheduler().runTaskLater(plugin, new Runnable() {
                @Override public void run() { forceStop(); }
            }, 100L);
        }
    }

    public boolean tryBumpCooldown(Player player) { return true; }

    private void updateCompasses() {
        SafePad target = safePad != null ? safePad : nextSafePad;
        if (target == null) return;
        Location loc = target.getLocation();
        // PERF: the pad moves once per stage; only resend the compass packet on change.
        if (lastCompassTarget != null
                && lastCompassTarget.getWorld() == loc.getWorld()
                && lastCompassTarget.getBlockX() == loc.getBlockX()
                && lastCompassTarget.getBlockY() == loc.getBlockY()
                && lastCompassTarget.getBlockZ() == loc.getBlockZ()) {
            return;
        }
        lastCompassTarget = loc;
        for (Player p : getAlivePlayers()) p.setCompassTarget(loc);
    }

    private void updateExpBars(List<Player> aliveNow) {
        float pct;
        if (safePad == null) {
            pct = 0;
        } else if (playersOnPad.isEmpty()) {
            pct = (float) Math.min(Math.max(phaseTimer * (1.0 / Math.max(1, phaseTimerStart)), 0), 0.999);
        } else {
            pct = (float) Math.min(Math.max(phaseTimer / (double) Math.max(6, 16 - (curSafe - 1)), 0), 0.999);
        }
        int lvl = Math.max(0, phaseTimer);
        // PERF: values change at 1Hz at most; only send the packets when they actually change.
        if (pct == lastExpPct && lvl == lastLevel) return;
        lastExpPct = pct;
        lastLevel = lvl;
        for (Player p : aliveNow) {
            p.setExp(pct);
            p.setLevel(lvl);
        }
    }

    private void broadcast(String msg) { Bukkit.broadcastMessage(msg); }

    // -------------------- Events --------------------

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        UUID id = player.getUniqueId();
        
        for (Player online : Bukkit.getOnlinePlayers()) {
            online.showPlayer(player);
        }

        if (alive.remove(id)) {
            recordPB(player);
            broadcast(ChatColor.GRAY + player.getName() + " left.");
            checkWin();
        }
        spectators.remove(id);
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        if (state != GameState.LIVE) return;
        Player p = event.getEntity();
        if (!alive.contains(p.getUniqueId())) return;
        event.getDrops().clear();
        event.setDeathMessage(null);
        eliminate(p, ChatColor.RED + p.getName() + " was eliminated!");
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent event) {
        if (!spectators.contains(event.getPlayer().getUniqueId())) return;
        if (center != null) event.setRespawnLocation(center.clone().add(0, 1, 0));
        final Player p = event.getPlayer();
        Bukkit.getScheduler().runTaskLater(plugin, new Runnable() {
            @Override public void run() {
                if (p.isOnline()) p.setGameMode(GameMode.SPECTATOR);
            }
        }, 1L);
    }

    @EventHandler
    @SuppressWarnings("deprecation")
    public void onDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player)) return;
        if (state != GameState.LIVE && state != GameState.STARTING) return;
        Player p = (Player) event.getEntity();
        if (!alive.contains(p.getUniqueId())) { event.setCancelled(true); return; }
        if (state == GameState.STARTING) event.setCancelled(true);
        if (event.getCause() == EntityDamageEvent.DamageCause.FALL) event.setCancelled(true);
    }

    @EventHandler
    public void onMove(PlayerMoveEvent event) {
        if (state != GameState.LIVE) return;
        Player p = event.getPlayer();
        if (!alive.contains(p.getUniqueId())) return;
        if (center == null) return;

        if (p.getLocation().getY() < center.getY() - 3) {
            eliminate(p, ChatColor.RED + p.getName() + " fell off the maze!");
        }
        // Pilot check: only award when the player actually lands ON a safe pad after a bump.
        // (Previously passed "true" unconditionally, so any grounded landing after a mob hit —
        // even far from a pad — triggered Pilot.)
        if (p.isOnGround()) {
            stats.checkPilotLand(p, isOnAnyPad(p));
        }
    }
}
