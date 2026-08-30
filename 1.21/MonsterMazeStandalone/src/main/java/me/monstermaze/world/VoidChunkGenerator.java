package me.monstermaze.world;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.generator.BlockPopulator;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.generator.ChunkGenerator.ChunkData;

import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * Completely empty chunks for a void lobby/arena world (Paper 1.21 ChunkData API).
 */
public class VoidChunkGenerator extends ChunkGenerator {

    @Override
    public ChunkData generateChunkData(World world, Random random, int x, int z, BiomeGrid biome) {
        return createChunkData(world); // all air (void)
    }

    @Override
    public List<BlockPopulator> getDefaultPopulators(World world) {
        return Collections.emptyList();
    }

    @Override
    public Location getFixedSpawnLocation(World world, Random random) {
        return new Location(world, 0.5, 64, 0.5);
    }

    @Override
    public boolean canSpawn(World world, int x, int z) {
        return true;
    }
}