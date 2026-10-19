package com.spege.insanetweaks.worldgen.sanctum;

import java.util.Random;

import com.spege.insanetweaks.config.ModConfig;

import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraft.world.chunk.IChunkProvider;
import net.minecraft.world.gen.IChunkGenerator;
import net.minecraftforge.fml.common.IWorldGenerator;

/**
 * Builds every sanctum chunk by chunk: each populate call writes only the part of nearby layouts
 * inside its own populate box, so no neighbouring chunk is ever forced to generate.
 */
public class SanctumGenerator implements IWorldGenerator {

    @Override
    public void generate(Random random, int chunkX, int chunkZ, World world, IChunkGenerator generator,
            IChunkProvider provider) {
        if (!(world instanceof WorldServer) || !SanctumPlacement.dimensionAllowed(world)) {
            return;
        }
        int size = ModConfig.arcaneSanctum.regionSize;
        SanctumBox clip = SanctumPlacement.writerBox(chunkX, chunkZ);
        int minRX = SanctumRegions.regionOf(chunkX - SanctumPlacement.REACH, size);
        int maxRX = SanctumRegions.regionOf(chunkX + SanctumPlacement.REACH, size);
        int minRZ = SanctumRegions.regionOf(chunkZ - SanctumPlacement.REACH, size);
        int maxRZ = SanctumRegions.regionOf(chunkZ + SanctumPlacement.REACH, size);
        for (int rx = minRX; rx <= maxRX; rx++) {
            for (int rz = minRZ; rz <= maxRZ; rz++) {
                int[] start = SanctumPlacement.candidate(world, rx, rz);
                if (start == null || Math.abs(start[0] - chunkX) > SanctumPlacement.REACH
                        || Math.abs(start[1] - chunkZ) > SanctumPlacement.REACH) {
                    continue;
                }
                SanctumLayout layout = SanctumPlacement.layout(world, start[0], start[1]);
                if (!layout.bounds.intersects(clip)
                        || !SanctumPlacement.isValid(world, start[0], start[1], layout, chunkX, chunkZ)) {
                    continue;
                }
                SanctumBuilder.build(world, layout, clip);
            }
        }
    }
}
