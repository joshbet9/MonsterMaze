package me.monstermaze.game;

import me.monstermaze.MonsterMazePlugin;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.type.Stairs;
import org.bukkit.block.BlockState;
import org.bukkit.entity.Entity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;

import java.util.ArrayList;
import java.util.List;

/** Beacon safe pad. */
public class SafePad implements Listener {
    private final Location center;
    private final int surfaceY;
    private final boolean qol;
    private final List<BlockSnapshot> snapshots = new ArrayList<BlockSnapshot>();
    private int decayCount = 11;
    private boolean active = true;

    public SafePad(Location pathLocation) { this(pathLocation, false); }

    public SafePad(Location pathLocation, boolean qol) {
        this.center = pathLocation.clone();
        this.surfaceY = pathLocation.getBlockY() - 1;
        this.qol = qol;

        MonsterMazePlugin plugin = MonsterMazePlugin.getInstance();
        if (plugin != null && plugin.getMapThemeApplier() != null) {
            plugin.getMapThemeApplier().refresh();
        }

        captureAndBuild();
        ensureBeacon();
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        Block block = event.getClickedBlock();
        if (block == null || block.getType() != Material.BEACON) return;
        World world = center.getWorld();
        if (block.getWorld().equals(world) && block.getX() == center.getBlockX()
                && block.getY() == surfaceY && block.getZ() == center.getBlockZ()) event.setCancelled(true);
    }

    private void captureAndBuild() {
        World world = center.getWorld();
        int cx = center.getBlockX(), cy = surfaceY, cz = center.getBlockZ();
        for (int x = -2; x <= 2; x++) for (int z = -2; z <= 2; z++) {
            if (x != 0 || z != 0) setBlock(world.getBlockAt(cx + x, cy, cz + z), Material.LIME_TERRACOTTA);
        }
        setBlock(world.getBlockAt(cx, cy, cz), Material.BEACON);
        for (int x = -1; x <= 1; x++) for (int z = -1; z <= 1; z++)
            setBlock(world.getBlockAt(cx + x, cy - 1, cz + z), Material.IRON_BLOCK);

        for (int x = -1; x <= 1; x++) {
            setBlock(world.getBlockAt(cx + x, cy - 1, cz + 2), stairs(Material.QUARTZ_STAIRS, BlockFace.NORTH));
            setBlock(world.getBlockAt(cx + x, cy - 1, cz - 2), stairs(Material.QUARTZ_STAIRS, BlockFace.SOUTH));
            setBlock(world.getBlockAt(cx + 2, cy - 1, cz + x), stairs(Material.QUARTZ_STAIRS, BlockFace.WEST));
            setBlock(world.getBlockAt(cx - 2, cy - 1, cz + x), stairs(Material.QUARTZ_STAIRS, BlockFace.EAST));
        }
        setBlock(world.getBlockAt(cx + 2, cy - 1, cz + 2), Material.CHISELED_QUARTZ_BLOCK);
        setBlock(world.getBlockAt(cx - 2, cy - 1, cz + 2), Material.CHISELED_QUARTZ_BLOCK);
        setBlock(world.getBlockAt(cx + 2, cy - 1, cz - 2), Material.CHISELED_QUARTZ_BLOCK);
        setBlock(world.getBlockAt(cx - 2, cy - 1, cz - 2), Material.CHISELED_QUARTZ_BLOCK);

        for (int x = -2; x <= 2; x++) for (int z = -2; z <= 2; z++) for (int y = 1; y <= 3; y++) {
            Block air = world.getBlockAt(cx + x, cy + y, cz + z);
            if (air.getType() != Material.AIR) setBlock(air, Material.AIR);
        }
    }

    public void ensureBeacon() {
        World world = center.getWorld();
        if (world == null) return;
        Block beacon = world.getBlockAt(center.getBlockX(), surfaceY, center.getBlockZ());
        if (beacon.getType() != Material.BEACON) beacon.setType(Material.BEACON, false);
        BlockState state = beacon.getState();
        state.update(true, true);
    }

    private static BlockData stairs(Material material, BlockFace face) {
        Stairs stairs = (Stairs) material.createBlockData();
        stairs.setFacing(face);
        stairs.setHalf(Stairs.Half.TOP);
        return stairs;
    }

    private void setBlock(Block block, Material mat) { snapshots.add(new BlockSnapshot(block)); block.setType(mat, false); }
    private void setBlock(Block block, BlockData data) { snapshots.add(new BlockSnapshot(block)); block.setBlockData(data, false); }

    public Location getLocation() { return center.clone(); }
    public boolean isActive() { return active; }

    public boolean isOn(Entity entity) {
        Location loc = entity.getLocation();
        int by = surfaceY;
        if (qol) {
            double dx = loc.getX() - center.getX(), dz = loc.getZ() - center.getZ();
            return dx > -2.5 && dx < 2.5 && dz > -2.5 && dz < 2.5 && loc.getY() > by && loc.getY() < by + 5;
        }
        int bx = center.getBlockX(), bz = center.getBlockZ();
        return loc.getX() > bx - 2 && loc.getX() < bx + 2.999 && loc.getY() > by && loc.getY() < by + 5
                && loc.getZ() > bz - 2.999 && loc.getZ() < bz + 2;
    }

    public boolean decay() {
        if (!active) return true;
        decayCount--;
        Material clay = Material.LIME_TERRACOTTA;
        if (decayCount <= 8 && decayCount > 6) clay = Material.YELLOW_TERRACOTTA;
        else if (decayCount <= 6 && decayCount > 4) clay = Material.ORANGE_TERRACOTTA;
        else if (decayCount <= 4) clay = Material.RED_TERRACOTTA;
        World world = center.getWorld();
        int cx = center.getBlockX(), cy = surfaceY, cz = center.getBlockZ();
        for (int x = -2; x <= 2; x++) for (int z = -2; z <= 2; z++) {
            Block b = world.getBlockAt(cx + x, cy, cz + z);
            if (b.getType() == Material.LIME_TERRACOTTA || b.getType() == Material.YELLOW_TERRACOTTA
                    || b.getType() == Material.ORANGE_TERRACOTTA || b.getType() == Material.RED_TERRACOTTA)
                if (b.getType() != clay) b.setType(clay, false);
        }
        if (decayCount <= 0) { destroy(); return true; }
        return false;
    }

    public void destroy() {
        active = false;
        for (int i = snapshots.size() - 1; i >= 0; i--) snapshots.get(i).restore();
        snapshots.clear();
        MonsterMazePlugin plugin = MonsterMazePlugin.getInstance();
        if (plugin != null && plugin.getMapThemeApplier() != null) plugin.getMapThemeApplier().refresh();
    }

    public void turnOffBeacon() {
        World world = center.getWorld();
        int cx = center.getBlockX(), cy = surfaceY, cz = center.getBlockZ();
        Block beacon = world.getBlockAt(cx, cy, cz);
        if (beacon.getType() == Material.BEACON) beacon.setType(Material.LIME_TERRACOTTA, false);
        // Do not convert the iron base to quartz here. The quartz is only the decorative
        // frame around the pad; changing the 3x3 base to quartz creates a visible flash of
        // quartz when the active pad is removed. The snapshots retain the actual maze blocks
        // that were underneath the pad and restore them when the old pad is finally destroyed.
    }

    private static class BlockSnapshot {
        private final Block block;
        private final BlockData data;
        BlockSnapshot(Block block) { this.block = block; this.data = block.getBlockData().clone(); }
        void restore() { block.setBlockData(data.clone(), false); }
    }
}
