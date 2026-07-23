package com.spege.insanetweaks.api;

import com.spege.insanetweaks.dormant.DormantWaystoneWorldData;

import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/**
 * Public, GroovyScript-callable facade over the persistent dormant-waystone registry.
 *
 * <p><b>Contract (hard constraints for the GS consumer):</b> every method is {@code public static},
 * directly callable without reflection, and uses only Minecraft classes ({@link BlockPos},
 * {@link World}) in its signatures. {@code null} is a legal answer for "not found" / "no pair" /
 * "registry unavailable" — no method throws on a miss. Stable FQCN:
 * {@code com.spege.insanetweaks.api.DormantWaystoneRegistry}.
 *
 * <p>The native mod owns only the marker block, this registry, Overworld worldgen, and the
 * manual place/break hooks. Teleport, the return-anchor placement in dim 150, the locator, the
 * tooltip and the gate all live in GroovyScript, which consumes this API.
 *
 * <p>Storage is a single global {@link DormantWaystoneWorldData} on the Overworld, so the pair
 * table survives restart and spans dimensions. Waystone lookups are filtered by dimension.
 */
public final class DormantWaystoneRegistry {

    private DormantWaystoneRegistry() {}

    /**
     * Nearest registered waystone in the SAME dimension as {@code world}, to {@code from}.
     * In the Overworld this returns only natural (or manually placed) Overworld waystones.
     *
     * @return the closest waystone {@link BlockPos}, or {@code null} if none is registered in
     *         this dimension (or the registry is unavailable).
     */
    public static BlockPos nearest(World world, BlockPos from) {
        if (world == null || from == null) {
            return null;
        }
        DormantWaystoneWorldData data = DormantWaystoneWorldData.get();
        if (data == null) {
            return null;
        }
        return data.nearest(world.provider.getDimension(), from);
    }

    /**
     * @return the dim-150 return-anchor position paired with the given Overworld waystone,
     *         or {@code null} if unpaired / unavailable.
     */
    public static BlockPos getReturnPos(BlockPos owWaystone) {
        if (owWaystone == null) {
            return null;
        }
        DormantWaystoneWorldData data = DormantWaystoneWorldData.get();
        return data == null ? null : data.getReturnPos(owWaystone);
    }

    /**
     * @return the Overworld waystone position paired with the given dim-150 return anchor,
     *         or {@code null} if unpaired / unavailable.
     */
    public static BlockPos getOverworldPos(BlockPos d150Return) {
        if (d150Return == null) {
            return null;
        }
        DormantWaystoneWorldData data = DormantWaystoneWorldData.get();
        return data == null ? null : data.getOverworldPos(d150Return);
    }

    /**
     * Links an Overworld waystone to a dim-150 return anchor (bidirectional, persisted).
     * Re-linking either end overwrites the previous mapping for that end.
     */
    public static void linkPair(BlockPos owWaystone, BlockPos d150Return) {
        if (owWaystone == null || d150Return == null) {
            return;
        }
        DormantWaystoneWorldData data = DormantWaystoneWorldData.get();
        if (data != null) {
            data.linkPair(owWaystone, d150Return);
        }
    }

    /**
     * Registers a waystone at {@code pos} in {@code world}'s dimension (worldgen, manual place,
     * or the GS-placed dim-150 return anchor). Idempotent.
     */
    public static void registerWaystone(World world, BlockPos pos) {
        if (world == null || pos == null) {
            return;
        }
        DormantWaystoneWorldData data = DormantWaystoneWorldData.get();
        if (data != null) {
            data.addWaystone(world.provider.getDimension(), pos);
        }
    }

    /**
     * Unregisters the waystone at {@code pos} in {@code world}'s dimension AND clears the entire
     * pair it belonged to (both directions), so the pair lookups never point into the void.
     * A no-op if nothing matched.
     */
    public static void unregisterWaystone(World world, BlockPos pos) {
        if (world == null || pos == null) {
            return;
        }
        DormantWaystoneWorldData data = DormantWaystoneWorldData.get();
        if (data != null) {
            data.removeWaystone(world.provider.getDimension(), pos);
        }
    }
}
