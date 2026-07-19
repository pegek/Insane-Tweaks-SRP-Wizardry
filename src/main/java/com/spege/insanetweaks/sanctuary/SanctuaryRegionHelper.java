package com.spege.insanetweaks.sanctuary;

import com.spege.insanetweaks.config.ModConfig;

import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

public final class SanctuaryRegionHelper {

    private SanctuaryRegionHelper() {}

    public static boolean isDimensionBlacklisted(World world) {
        int dim = world.provider.getDimension();
        for (int d : ModConfig.sanctuary.dimensionBlacklist) {
            if (d == dim) {
                return true;
            }
        }
        return false;
    }

    /** True when (x,z) in `world` lies inside any active sanctuary and the module is not gated off there. */
    public static boolean isProtected(World world, int x, int z) {
        if (world == null || world.isRemote) {
            return false;
        }
        if (isDimensionBlacklisted(world)) {
            return false;
        }
        return SanctuaryWorldData.get(world).isInside(x, z);
    }

    public static boolean isProtected(World world, BlockPos pos) {
        return pos != null && isProtected(world, pos.getX(), pos.getZ());
    }
}
