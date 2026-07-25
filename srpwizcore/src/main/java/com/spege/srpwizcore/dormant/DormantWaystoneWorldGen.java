package com.spege.srpwizcore.dormant;

import java.util.Random;

import com.spege.srpwizcore.api.DormantWaystoneRegistry;
import com.spege.srpwizcore.config.SrpWizCoreConfig;

import net.minecraft.block.state.IBlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.chunk.IChunkProvider;
import net.minecraft.world.gen.IChunkGenerator;
import net.minecraftforge.fml.common.IWorldGenerator;

/**
 * Discreet surface-dimension generation for the dormant waystone.
 *
 * <p>Runs only in {@code SrpWizCoreConfig.dormantWaystones.dimSurface}, gated at registration by
 * {@code dormantWaystones.worldgenEnabled}. Rarity is
 * {@code dormantWaystones.worldgenChancePerChunk} (read live each chunk). Placement is
 * deliberately cheap — a single {@link World#getTopSolidOrLiquidBlock(BlockPos)} surface probe,
 * no volumetric scans — then {@link DormantWaystoneRegistry#registerWaystone(World, BlockPos)}.
 */
public class DormantWaystoneWorldGen implements IWorldGenerator {

    @Override
    public void generate(Random random, int chunkX, int chunkZ, World world,
            IChunkGenerator chunkGenerator, IChunkProvider chunkProvider) {
        if (world.provider.getDimension() != SrpWizCoreConfig.dormantWaystones.dimSurface) {
            return;
        }
        if (DormantBlocks.DORMANT_WAYSTONE == null) {
            return;
        }
        if (random.nextFloat() >= SrpWizCoreConfig.dormantWaystones.worldgenChancePerChunk) {
            return;
        }

        int x = chunkX * 16 + random.nextInt(16);
        int z = chunkZ * 16 + random.nextInt(16);
        BlockPos surface = world.getTopSolidOrLiquidBlock(new BlockPos(x, 0, z));
        if (surface.getY() <= 0) {
            return;
        }

        IBlockState below = world.getBlockState(surface.down());
        if (!below.getMaterial().isSolid()) {
            return; // need solid ground to sit on
        }
        IBlockState here = world.getBlockState(surface);
        if (here.getMaterial().isLiquid()) {
            return; // don't drop into water/lava
        }
        if (!here.getBlock().isReplaceable(world, surface)) {
            return; // don't overwrite existing structure/tree blocks
        }

        world.setBlockState(surface, DormantBlocks.DORMANT_WAYSTONE.getDefaultState(), 2);
        DormantWaystoneRegistry.registerWaystone(world, surface);
    }
}
