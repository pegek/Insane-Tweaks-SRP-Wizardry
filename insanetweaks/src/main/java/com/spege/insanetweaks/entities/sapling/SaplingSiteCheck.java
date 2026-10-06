package com.spege.insanetweaks.entities.sapling;

import java.util.UUID;

import javax.annotation.Nullable;

import com.spege.insanetweaks.config.ModConfig;
import com.spege.insanetweaks.config.categories.BaubleFruitsCategory;
import com.spege.insanetweaks.entities.EntityCorruptedSapling;
import com.spege.insanetweaks.sanctuary.SanctuaryRegionHelper;
import com.spege.insanetweaks.util.SrpPhaseHelper;

import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.Entity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.DimensionManager;

/**
 * Where a Corrupted Sapling may stand (spec §3). {@code base} is always the block the sapling's
 * feet occupy, i.e. {@code new BlockPos(sapling)} or the air block above the clicked face.
 *
 * <p>Checked once at planting (refusal, seed kept) and every 100 ticks while the defense runs
 * (pause). Server side only.
 */
public final class SaplingSiteCheck {

    private static final String SRP_DOMAIN = "srparasites";

    private SaplingSiteCheck() {
    }

    /** The first failing site rule, or null when the site is fine. Owner rules are the entity's. */
    @Nullable
    public static SiteProblem checkSite(World world, BlockPos base) {
        BaubleFruitsCategory cfg = ModConfig.baubleFruits;
        if (isBlacklistedDimension(world, cfg)) {
            return SiteProblem.DIMENSION;
        }
        if (SrpPhaseHelper.getEvolutionPhase(world) < 1) {
            return SiteProblem.PHASE_TOO_LOW;
        }
        if (!hasInfestation(world, base, cfg.saplingInfestationRadius, cfg.saplingInfestationHeight)) {
            return SiteProblem.NO_INFESTATION;
        }
        if (!world.getBlockState(base.down()).isSideSolid(world, base.down(), EnumFacing.UP)) {
            return SiteProblem.NO_GROUND;
        }
        if (!isColumnClear(world, base, cfg.saplingColumnClearance)) {
            return SiteProblem.ROOFED;
        }
        if (crowdCount(world, base, cfg.saplingCrowdRadius, cfg.saplingCrowdHeight) > cfg.saplingCrowdTolerance) {
            return SiteProblem.CROWDED;
        }
        if (SanctuaryRegionHelper.isNear(world, base, cfg.saplingSanctuaryMargin)) {
            return SiteProblem.SANCTUARY;
        }
        return null;
    }

    /** True when {@code owner} already has a living Corrupted Sapling in any loaded world. */
    public static boolean hasActiveSapling(UUID owner) {
        for (WorldServer world : DimensionManager.getWorlds()) {
            for (Entity entity : world.loadedEntityList) {
                if (entity instanceof EntityCorruptedSapling && entity.isEntityAlive()
                        && owner.equals(((EntityCorruptedSapling) entity).getOwnerId())) {
                    return true;
                }
            }
        }
        return false;
    }

    /** A block that stops movement. Grass, flowers and other pass-through blocks do not count. */
    public static boolean collides(World world, BlockPos pos) {
        IBlockState state = world.getBlockState(pos);
        return state.getCollisionBoundingBox(world, pos) != Block.NULL_AABB;
    }

    private static boolean isBlacklistedDimension(World world, BaubleFruitsCategory cfg) {
        int dim = world.provider.getDimension();
        for (int blocked : cfg.saplingDimensionBlacklist) {
            if (blocked == dim) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasInfestation(World world, BlockPos base, int radius, int height) {
        for (BlockPos pos : BlockPos.getAllInBoxMutable(base.add(-radius, -height, -radius),
                base.add(radius, height, radius))) {
            ResourceLocation name = world.getBlockState(pos).getBlock().getRegistryName();
            if (name != null && SRP_DOMAIN.equals(name.getResourceDomain())) {
                return true;
            }
        }
        return false;
    }

    /** Layers base+0 .. base+clearance of the sapling's own column, no exceptions. */
    private static boolean isColumnClear(World world, BlockPos base, int clearance) {
        for (int dy = 0; dy <= clearance; dy++) {
            if (collides(world, base.up(dy))) {
                return false;
            }
        }
        return true;
    }

    /** Solid blocks in the (2r+1)^2 square, layers base+0 .. base+height, own column excluded. */
    private static int crowdCount(World world, BlockPos base, int radius, int height) {
        int count = 0;
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                if (dx == 0 && dz == 0) {
                    continue;
                }
                for (int dy = 0; dy <= height; dy++) {
                    if (collides(world, base.add(dx, dy, dz))) {
                        count++;
                    }
                }
            }
        }
        return count;
    }
}
