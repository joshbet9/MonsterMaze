package me.monstermaze.cpu;

import me.monstermaze.MonsterMazePlugin;
import me.monstermaze.game.GameManager;
import me.monstermaze.game.GameState;
import me.monstermaze.kit.KitType;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Owns live CPU competitors.
 *
 * <p>The manager owns per-bot reusable observation/action buffers and a
 * swappable direct brain. Movement/mechanics remain delegated to the real
 * player-shaped avatar.
 */
public final class CpuOpponentManager {
    private final MonsterMazePlugin plugin;
    private final Map<UUID, Controller> controllers = new HashMap<UUID, Controller>();
    private final CpuObservationBuilder observations;
    private BukkitTask tickTask;

    public CpuOpponentManager(MonsterMazePlugin plugin, GameManager game) {
        if (plugin == null) throw new IllegalArgumentException("plugin");
        if (game == null) throw new IllegalArgumentException("game");
        this.plugin = plugin;
        this.observations = new CpuObservationBuilder(game);

        tickTask = Bukkit.getScheduler().runTaskTimer(plugin, new Runnable() {
            @Override
            public void run() {
                tick();
            }
        }, 1L, 1L);
    }

    public void spawnForMatch() {
        GameManager game = plugin.getGameManager();
        if (game == null || !game.isRunning()) return;
        if (!plugin.getConfig().getBoolean("cpu-opponents.enabled", false)) return;
        if (!controllers.isEmpty()) return;

        int count = Math.max(1, Math.min(
                8, plugin.getConfig().getInt("cpu-opponents.count", 1)));
        String prefix = plugin.getConfig().getString("cpu-opponents.name-prefix", "CPU");
        KitType kit = KitType.byName(
                plugin.getConfig().getString("cpu-opponents.kit", "JUMPER"));
        if (kit == null) kit = KitType.JUMPER;

        Location center = game.getCenter();
        if (center == null) return;

        long baseSeed = plugin.getConfig().getLong("cpu-opponents.seed", 1337L);
        for (int i = 0; i < count; i++) {
            String id = "cpu-" + (i + 1);
            UUID uuid = UUID.nameUUIDFromBytes(
                    ("monstermaze:cpu:" + id).getBytes(StandardCharsets.UTF_8));

            Location spawn = center.clone().add(
                    0.5 + ((i % 3) - 1) * 1.25,
                    1.0,
                    0.5 + ((i / 3) * 1.25));

            CpuAvatar avatar = new NmsCpuAvatar(
                    plugin, id, prefix + (i + 1), uuid);
            Player player = game.addCpuParticipant(avatar, kit, spawn);

            Controller controller = new Controller(
                    avatar, player, new ReferencePadSeekBrain(), baseSeed + i);
            controllers.put(player.getUniqueId(), controller);

            plugin.getLogger().info("[MonsterMaze] Spawned CPU opponent "
                    + player.getName() + " kit=" + kit);
        }
    }

    /** Clear controllers at the end of one match while keeping the manager alive for the next match. */
    public void shutdownMatch() {
        controllers.clear();
    }

    public void shutdown() {
        if (tickTask != null) {
            tickTask.cancel();
            tickTask = null;
        }
        controllers.clear();
    }

    private void tick() {
        GameManager game = plugin.getGameManager();
        if (game == null || game.getState() != GameState.LIVE || controllers.isEmpty()) return;

        ArrayList<UUID> dead = new ArrayList<UUID>();
        for (Map.Entry<UUID, Controller> entry : controllers.entrySet()) {
            Controller controller = entry.getValue();
            if (!controller.avatar.isSpawned()) {
                dead.add(entry.getKey());
                continue;
            }
            controller.tick();
        }
        for (UUID id : dead) controllers.remove(id);
    }

    private final class Controller {
        private final CpuAvatar avatar;
        private final Player player;
        private final CpuBrain brain;
        private final CpuObservation observation = new CpuObservation();
        private final CpuAvatarAction action = new CpuAvatarAction();

        private Controller(CpuAvatar avatar, Player player, CpuBrain brain, long seed) {
            this.avatar = avatar;
            this.player = player;
            this.brain = brain;
            brain.reset(seed);
        }

        private void tick() {
            observations.build(player, observation);
            brain.decide(observation, action);
            avatar.apply(action);
            if (action.useAbility) {
                plugin.getGameManager().getKitManager().tryUseAbility(player);
            }
        }
    }
}
