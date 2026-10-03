package me.monstermaze.cpu;

import me.monstermaze.entity.MonsterManager;
import me.monstermaze.game.GameManager;
import me.monstermaze.game.MazeMode;
import me.monstermaze.game.SafePad;
import me.monstermaze.kit.KitType;
import me.monstermaze.maze.MazeGenerator;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import java.util.Arrays;

/**
 * Server-authoritative encoder for the v2 CPU policy observation.
 *
 * <p>Feature layout is stable:
 * 0-31 player/pad/game state, 32-43 local maze topology and population,
 * 44-75 nearest 8 monsters (dx,dz,dy,distance), 76-95 nearest 4 competitors
 * (dx,dz,distance,velocityX,velocityZ).
 */
public final class CpuObservationBuilder {
    private static final double POS_RANGE = 64.0;
    private static final double MOB_RANGE = 32.0;
    private static final double COMPETITOR_RANGE = 64.0;
    private static final int MONSTER_SLOTS = 8;
    private static final int COMPETITOR_SLOTS = 4;

    private final GameManager game;
    private final double[] mobD2 = new double[MONSTER_SLOTS];
    private final double[] mobDx = new double[MONSTER_SLOTS];
    private final double[] mobDz = new double[MONSTER_SLOTS];
    private final double[] mobDy = new double[MONSTER_SLOTS];
    private final double[] competitorD2 = new double[COMPETITOR_SLOTS];
    private final double[] competitorDx = new double[COMPETITOR_SLOTS];
    private final double[] competitorDz = new double[COMPETITOR_SLOTS];
    private final double[] competitorVx = new double[COMPETITOR_SLOTS];
    private final double[] competitorVz = new double[COMPETITOR_SLOTS];
    private int monstersWithin8;

    public CpuObservationBuilder(GameManager game) {
        if (game == null) throw new IllegalArgumentException("game");
        this.game = game;
    }

    public void build(Player player, CpuObservation out) {
        if (player == null || out == null) return;
        out.clear();

        Location p = player.getLocation();
        Location center = game.getCenter();
        if (center == null) center = p;

        Vector velocity = player.getVelocity();
        double relX = p.getX() - center.getX();
        double relZ = p.getZ() - center.getZ();
        double yawRad = Math.toRadians(p.getYaw());

        out.set(0, norm(relX, POS_RANGE));
        out.set(1, norm(relZ, POS_RANGE));
        out.set(2, norm(p.getY() - center.getY(), 8.0));
        out.set(3, clamp(velocity.getX(), -1.0, 1.0));
        out.set(4, clamp(velocity.getZ(), -1.0, 1.0));
        out.set(5, clamp(velocity.getY(), -1.0, 1.0));
        out.set(6, (float) clamp(Math.sqrt(velocity.getX() * velocity.getX()
                + velocity.getZ() * velocity.getZ()), 0.0, 1.0));
        out.set(7, (float) Math.sin(yawRad));
        out.set(8, (float) Math.cos(yawRad));

        SafePad pad = game.getSafePad();
        Location target = pad == null ? game.getNextSafePadLocation() : pad.getLocation();
        double padDx = 0.0;
        double padDz = 0.0;
        if (target != null) {
            padDx = target.getX() - p.getX();
            padDz = target.getZ() - p.getZ();
        }
        double padDistance = Math.sqrt(padDx * padDx + padDz * padDz);
        double padBearing = Math.atan2(-padDx, padDz);
        double yawError = normaliseDegrees(Math.toDegrees(padBearing) - p.getYaw());

        out.set(9, norm(padDx, POS_RANGE));
        out.set(10, norm(padDz, POS_RANGE));
        out.set(11, norm(padDistance, POS_RANGE));
        out.set(12, (float) Math.sin(padBearing));
        out.set(13, (float) Math.cos(padBearing));
        out.set(14, (float) (yawError / 180.0));
        out.set(15, clamp(game.getStage() / 100.0, 0.0, 1.0));
        out.set(16, clamp(game.getPhaseTimerSeconds() / 60.0, 0.0, 1.0));
        double phaseStart = Math.max(1.0, game.getPhaseTimerStartSeconds());
        out.set(17, (float) clamp(game.getPhaseTimerSeconds() / phaseStart, 0.0, 1.0));

        double maxHealth = Math.max(1.0, player.getMaxHealth());
        out.set(18, (float) clamp(player.getHealth() / maxHealth, 0.0, 1.0));
        out.set(19, (float) clamp(maxHealth / 30.0, 0.0, 1.0));
        out.set(20, player.isOnGround() ? 1.0f : 0.0f);
        out.set(21, game.getSafePad() != null && game.getSafePad().isOn(player) ? 1.0f : 0.0f);
        out.set(22, game.isOnAnyPad(player) ? 1.0f : 0.0f);

        KitType kit = game.getKitManager().getKit(player);
        out.set(23, kitIndex(kit) / 3.0f);
        out.set(24, jumperCharges(player) / 5.0f);
        out.set(25, abilityReady(player, kit) ? 1.0f : 0.0f);
        MazeMode mode = game.getMode();
        out.set(26, mode == MazeMode.SPEED ? 1.0f : 0.0f);
        out.set(27, mode == MazeMode.MODERN ? 1.0f : 0.0f);
        int pattern = game.getPatternIndex();
        out.set(28, pattern == 0 ? 1.0f : 0.0f);
        out.set(29, pattern == 1 ? 1.0f : 0.0f);
        out.set(30, pattern == 2 ? 1.0f : 0.0f);
        out.set(31, clamp((System.currentTimeMillis() - game.getGameLiveTime()) / 180000.0, 0.0, 1.0));

        MazeGenerator maze = game.getMazeGenerator();
        Block b = p.getBlock();
        out.set(32, path(maze, b.getRelative(0, 0, -1)) ? 1.0f : 0.0f);
        out.set(33, path(maze, b.getRelative(0, 0, 1)) ? 1.0f : 0.0f);
        out.set(34, path(maze, b.getRelative(1, 0, 0)) ? 1.0f : 0.0f);
        out.set(35, path(maze, b.getRelative(-1, 0, 0)) ? 1.0f : 0.0f);
        out.set(36, path(maze, b.getRelative(1, 0, -1)) ? 1.0f : 0.0f);
        out.set(37, path(maze, b.getRelative(-1, 0, -1)) ? 1.0f : 0.0f);
        out.set(38, path(maze, b.getRelative(1, 0, 1)) ? 1.0f : 0.0f);
        out.set(39, path(maze, b.getRelative(-1, 0, 1)) ? 1.0f : 0.0f);
        out.set(40, path(maze, b) ? 1.0f : 0.0f);
        out.set(41, clamp(game.getAlivePlayers().size() / 8.0, 0.0, 1.0));
        out.set(42, clamp(game.getAliveHumanPlayers().size() / 8.0, 0.0, 1.0));

        Arrays.fill(mobD2, Double.POSITIVE_INFINITY);
        Arrays.fill(competitorD2, Double.POSITIVE_INFINITY);
        monstersWithin8 = 0;

        MonsterManager monsters = game.getMonsterManager();
        for (LivingEntity mob : monsters.getMonsters()) {
            if (mob == null || !mob.isValid() || mob.isDead()) continue;
            Location ml = mob.getLocation();
            double dx = ml.getX() - p.getX();
            double dz = ml.getZ() - p.getZ();
            double dy = ml.getY() - p.getY();
            if (dx * dx + dz * dz <= 64.0) monstersWithin8++;
            insertMonster(dx, dz, dy);
        }
        out.set(43, clamp(monstersWithin8 / 8.0, 0.0, 1.0));

        for (int i = 0; i < MONSTER_SLOTS; i++) {
            int base = 44 + i * 4;
            double d2 = mobD2[i];
            if (!Double.isFinite(d2)) continue;
            out.set(base, norm(mobDx[i], MOB_RANGE));
            out.set(base + 1, norm(mobDz[i], MOB_RANGE));
            out.set(base + 2, norm(mobDy[i], 4.0));
            out.set(base + 3, norm(Math.sqrt(d2), MOB_RANGE));
        }

        for (Player other : game.getAlivePlayers()) {
            if (other == null || other.equals(player)) continue;
            Location op = other.getLocation();
            Vector ov = other.getVelocity();
            insertCompetitor(
                    op.getX() - p.getX(), op.getZ() - p.getZ(),
                    ov.getX(), ov.getZ());
        }

        for (int i = 0; i < COMPETITOR_SLOTS; i++) {
            int base = 76 + i * 5;
            double d2 = competitorD2[i];
            if (!Double.isFinite(d2)) continue;
            out.set(base, norm(competitorDx[i], COMPETITOR_RANGE));
            out.set(base + 1, norm(competitorDz[i], COMPETITOR_RANGE));
            out.set(base + 2, norm(Math.sqrt(d2), COMPETITOR_RANGE));
            out.set(base + 3, clamp(competitorVx[i], -1.0, 1.0));
            out.set(base + 4, clamp(competitorVz[i], -1.0, 1.0));
        }
    }

    private boolean path(MazeGenerator maze, Block block) {
        return maze != null && maze.isPath(block.getLocation());
    }

    private void insertMonster(double dx, double dz, double dy) {
        double d2 = dx * dx + dz * dz + dy * dy;
        for (int i = 0; i < MONSTER_SLOTS; i++) {
            if (d2 >= mobD2[i]) continue;
            for (int j = MONSTER_SLOTS - 1; j > i; j--) {
                mobD2[j] = mobD2[j - 1];
                mobDx[j] = mobDx[j - 1];
                mobDz[j] = mobDz[j - 1];
                mobDy[j] = mobDy[j - 1];
            }
            mobD2[i] = d2;
            mobDx[i] = dx;
            mobDz[i] = dz;
            mobDy[i] = dy;
            return;
        }
    }

    private void insertCompetitor(double dx, double dz, double vx, double vz) {
        double d2 = dx * dx + dz * dz;
        for (int i = 0; i < COMPETITOR_SLOTS; i++) {
            if (d2 >= competitorD2[i]) continue;
            for (int j = COMPETITOR_SLOTS - 1; j > i; j--) {
                competitorD2[j] = competitorD2[j - 1];
                competitorDx[j] = competitorDx[j - 1];
                competitorDz[j] = competitorDz[j - 1];
                competitorVx[j] = competitorVx[j - 1];
                competitorVz[j] = competitorVz[j - 1];
            }
            competitorD2[i] = d2;
            competitorDx[i] = dx;
            competitorDz[i] = dz;
            competitorVx[i] = vx;
            competitorVz[i] = vz;
            return;
        }
    }

    private int jumperCharges(Player player) {
        org.bukkit.inventory.ItemStack item = player.getInventory().getItem(8);
        return item == null || item.getType() != org.bukkit.Material.FEATHER ? 0 : Math.min(5, item.getAmount());
    }

    private boolean abilityReady(Player player, KitType kit) {
        if (kit == KitType.JUMPER) return jumperCharges(player) > 0;
        org.bukkit.inventory.ItemStack item = player.getInventory().getItem(0);
        return item != null && item.getAmount() > 0;
    }

    private static int kitIndex(KitType kit) {
        if (kit == KitType.SLOWBALL) return 1;
        if (kit == KitType.BODY_BUILDER) return 2;
        return kit == KitType.REPULSOR ? 3 : 0;
    }

    private static float norm(double value, double range) {
        return (float) clamp(value / range, -1.0, 1.0);
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private static float clamp(double value, float min, float max) {
        return (float) Math.max(min, Math.min(max, value));
    }

    private static double normaliseDegrees(double degrees) {
        while (degrees >= 180.0) degrees -= 360.0;
        while (degrees < -180.0) degrees += 360.0;
        return degrees;
    }
}
