package me.monstermaze.game;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;

import java.util.ArrayList;
import java.util.List;

/**
 * Beacon safe pad. Surface sits on the maze path floor (same Y as path blocks).
 * Original used next.clone().subtract(0, 1, 0) so the pad aligns with the path.
 */
public class SafePad implements Listener {

    private final Location center; // walk-level reference (path Y)
    private final int surfaceY;    // block Y of clay/beacon
    private final boolean qol;
    private final List<BlockSnapshot> snapshots = new ArrayList<BlockSnapshot>();
    private int decayCount = 11;
    private boolean active = true;

    public SafePad(Location pathLocation) {
        this(pathLocation, false);
    }

    public SafePad(Location pathLocation, boolean qol) {
        // pathLocation is walk height (top of path block); surface blocks at Y-1
        this.center = pathLocation.clone();
        this.surfaceY = pathLocation.getBlockY() - 1;
        this.qol = qol;
        captureAndBuild();
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() == Action.RIGHT_CLICK_BLOCK) {
            Block block = event.getClickedBlock();
            if (block != null && block.getType() == Material.BEACON) {
                // Check if this beacon belongs to this safe pad instance
                World world = center.getWorld();
                if (block.getWorld().equals(world)
                        && block.getX() == center.getBlockX()
                        && block.getY() == surfaceY
                        && block.getZ() == center.getBlockZ()) {
                    event.setCancelled(true);
                }
            }
        }
    }

    @SuppressWarnings("deprecation")
    private void captureAndBuild() {
        World world = center.getWorld();
        int cx = center.getBlockX();
        int cy = surfaceY;
        int cz = center.getBlockZ();

        // Surface clay ring + beacon at path floor level
        for (int x = -2; x <= 2; x++) {
            for (int z = -2; z <= 2; z++) {
                if (x == 0 && z == 0) continue;
                setBlock(world.getBlockAt(cx + x, cy, cz + z), Material.STAINED_CLAY, (byte) 5);
            }
        }
        setBlock(world.getBlockAt(cx, cy, cz), Material.BEACON, (byte) 0);

        // Iron base under beacon
        for (int x = -1; x <= 1; x++) {
            for (int z = -1; z <= 1; z++) {
                setBlock(world.getBlockAt(cx + x, cy - 1, cz + z), Material.IRON_BLOCK, (byte) 0);
            }
        }

        // Stairs / corners (data matches Mineplex source: 7=+z, 6=-z, 5=+x, 4=-x upside-down)
        for (int x = -1; x <= 1; x++) {
            setBlock(world.getBlockAt(cx + x, cy - 1, cz + 2), Material.QUARTZ_STAIRS, (byte) 7);
            setBlock(world.getBlockAt(cx + x, cy - 1, cz - 2), Material.QUARTZ_STAIRS, (byte) 6);
            setBlock(world.getBlockAt(cx + 2, cy - 1, cz + x), Material.QUARTZ_STAIRS, (byte) 5);
            setBlock(world.getBlockAt(cx - 2, cy - 1, cz + x), Material.QUARTZ_STAIRS, (byte) 4);
        }
        setBlock(world.getBlockAt(cx + 2, cy - 1, cz + 2), Material.QUARTZ_BLOCK, (byte) 1);
        setBlock(world.getBlockAt(cx - 2, cy - 1, cz + 2), Material.QUARTZ_BLOCK, (byte) 1);
        setBlock(world.getBlockAt(cx + 2, cy - 1, cz - 2), Material.QUARTZ_BLOCK, (byte) 1);
        setBlock(world.getBlockAt(cx - 2, cy - 1, cz - 2), Material.QUARTZ_BLOCK, (byte) 1);

        // Clear air above pad so players aren't inside blocks
        for (int x = -2; x <= 2; x++) {
            for (int z = -2; z <= 2; z++) {
                for (int y = 1; y <= 3; y++) {
                    Block air = world.getBlockAt(cx + x, cy + y, cz + z);
                    if (air.getType() != Material.AIR) {
                        setBlock(air, Material.AIR, (byte) 0);
                    }
                }
            }
        }
    }

    @SuppressWarnings("deprecation")
    private void setBlock(Block block, Material mat, byte data) {
        snapshots.add(new BlockSnapshot(block));
        block.setType(mat);
        block.setData(data);
    }

    public Location getLocation() {
        return center.clone();
    }

    public boolean isActive() {
        return active;
    }

    public boolean isOn(Entity entity) {
        Location loc = entity.getLocation();
        int by = surfaceY;

        // QOL fix: use a symmetric box that exactly matches the 5x5 visible clay pad.
        // The original asymmetric box (loc + (2.999,5,2) .. loc + (-2,0,-2.999)) is offset
        // from the visible pad, so standing on an edge block could be judged off-pad.
        if (qol) {
            double dx = loc.getX() - center.getX();
            double dz = loc.getZ() - center.getZ();
            return dx > -2.5 && dx < 2.5
                    && dz > -2.5 && dz < 2.5
                    && loc.getY() > by && loc.getY() < (by + 5);
        }

        // Mirrors Mineplex SafePad.isOn: UtilAlg.inBoundingBox(e.getLocation(),
        //   loc+(2.999,5,2) .. loc+(-2,0,-2.999)) — exclusive box on every axis.
        int bx = center.getBlockX();
        int bz = center.getBlockZ();
        return loc.getX() > (bx - 2) && loc.getX() < (bx + 2.999)
                && loc.getY() > by && loc.getY() < (by + 5)
                && loc.getZ() > (bz - 2.999) && loc.getZ() < (bz + 2);
    }

    @SuppressWarnings("deprecation")
    public boolean decay() {
        if (!active) return true;
        decayCount--;

        byte clayData = 5;
        if (decayCount <= 8 && decayCount > 6) clayData = 4;
        else if (decayCount <= 6 && decayCount > 4) clayData = 1;
        else if (decayCount <= 4) clayData = 14;

        World world = center.getWorld();
        int cx = center.getBlockX();
        int cy = surfaceY;
        int cz = center.getBlockZ();

        for (int x = -2; x <= 2; x++) {
            for (int z = -2; z <= 2; z++) {
                Block b = world.getBlockAt(cx + x, cy, cz + z);
                if (b.getType() == Material.STAINED_CLAY) {
                    b.setData(clayData);
                }
            }
        }

        if (decayCount <= 0) {
            destroy();
            return true;
        }
        return false;
    }

    public void destroy() {
        active = false;
        for (int i = snapshots.size() - 1; i >= 0; i--) {
            snapshots.get(i).restore();
        }
        snapshots.clear();
    }

    public void turnOffBeacon() {
        World world = center.getWorld();
        int cx = center.getBlockX();
        int cy = surfaceY;
        int cz = center.getBlockZ();

        Block beacon = world.getBlockAt(cx, cy, cz);
        if (beacon.getType() == Material.BEACON) {
            beacon.setType(Material.STAINED_CLAY);
            //noinspection deprecation
            beacon.setData((byte) 5);
        }
        for (int x = -1; x <= 1; x++) {
            for (int z = -1; z <= 1; z++) {
                Block iron = world.getBlockAt(cx + x, cy - 1, cz + z);
                if (iron.getType() == Material.IRON_BLOCK) {
                    iron.setType(Material.QUARTZ_BLOCK);
                }
            }
        }
    }

    private static class BlockSnapshot {
        private final Block block;
        private final Material type;
        private final byte data;

        @SuppressWarnings("deprecation")
        BlockSnapshot(Block block) {
            this.block = block;
            this.type = block.getType();
            this.data = block.getData();
        }

        @SuppressWarnings("deprecation")
        void restore() {
            block.setType(type);
            try {
                block.setData(data);
            } catch (Exception ignored) {
            }
        }
    }
}