package com.spege.insanetweaks.dormant;

import java.util.Random;

import com.spege.insanetweaks.api.DormantWaystoneRegistry;
import com.spege.insanetweaks.config.ModConfig;
import com.spege.insanetweaks.init.ModBlocks;

import net.minecraft.block.state.IBlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.chunk.IChunkProvider;
import net.minecraft.world.gen.IChunkGenerator;
import net.minecraftforge.fml.common.IWorldGenerator;

/**
 * Discreet Overworld-only surface generation for the dormant waystone.
 *
 * <p>Runs only in dimension 0, gated at registration by {@code worldgen.dormantWaystoneEnabled}.
 * Rarity is {@code worldgen.dormantWaystoneChancePerChunk} (read live each chunk). Placement is
 * deliberately cheap — a single {@link World#getTopSolidOrLiquidBlock(BlockPos)} surface probe,
 * no volumetric scans — then {@link DormantWaystoneRegistry#registerWaystone(World, BlockPos)}.
 * Not an OTG CustomObject/BO3 (that path is broken in this pack).
 */
public class DormantWaystoneWorldGen implements IWorldGenerator {

    @Override
    public void generate(Random random, int chunkX, int chunkZ, World world,
            IChunkGenerator chunkGenerator, IChunkProvider chunkProvider) {
        if (world.provider.getDimension() != 0) {
            return;
        }
        if (ModBlocks.DORMANT_WAYSTONE == null) {
            return;
        }
        if (random.nextFloat() >= ModConfig.worldgen.dormantWaystoneChancePerChunk) {
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

        world.setBlockState(surface, ModBlocks.DORMANT_WAYSTONE.getDefaultState(), 2);
        DormantWaystoneRegistry.registerWaystone(world, surface);
    }
}
