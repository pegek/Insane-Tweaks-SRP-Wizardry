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

    private static final String SRP_PARASITE_BASE =
            "com.dhanantry.scapeandrunparasites.entity.ai.misc.EntityParasiteBase";

    /** True if the entity's class chain includes SRP's EntityParasiteBase (covers SRP, SRPExtra, SimWizard). */
    public static boolean isSrpParasite(net.minecraft.entity.Entity e) {
        if (e == null) {
            return false;
        }
        Class<?> c = e.getClass();
        while (c != null) {
            if (c.getName().equals(SRP_PARASITE_BASE)) {
                return true;
            }
            c = c.getSuperclass();
        }
        return false;
    }

    /** True when (x,z) is inside any active sanctuary within min(regionRadius, purgeFireRadiusCap). */
    public static boolean isInPurgeRange(World world, int x, int z) {
        if (world == null || world.isRemote) {
            return false;
        }
        if (isDimensionBlacklisted(world)) {
            return false;
        }
        return SanctuaryWorldData.get(world).isInsideCapped(x, z, ModConfig.sanctuary.purgeFireRadiusCap);
    }
}
