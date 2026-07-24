package com.spege.srpwizcore.util;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.util.math.BlockPos;

/**
 * Per-dimension storage for Ice&amp;Fire's "last placed structure" anti-clustering positions.
 *
 * <p>{@code StructureGenerator} keeps ten {@code BlockPos} fields ({@code lastMausoleum},
 * {@code lastDragonRoost}, …) and refuses to place a structure that is closer than
 * {@code worldGenDistance} to the stored one. Those fields belong to the single
 * {@code StructureGenerator} instance registered with {@code GameRegistry.registerWorldGenerator},
 * so they are shared by every dimension. That is harmless while Ice&amp;Fire generates in one
 * dimension only — and actively harmful once it generates in two, because a mausoleum placed in
 * dimension 0 then suppresses the next mausoleum in dimension 150 (the coordinates are compared
 * across worlds as if they were in the same one).
 *
 * <p>{@code MixinIandfStructureGenerator} swaps the values of those fields in at the start of
 * {@code generate} and back out at every return, keyed by dimension, which makes the spacing rule
 * behave per dimension without touching Ice&amp;Fire's placement logic.
 *
 * <p>Lives in {@code util/}, outside the mixin package, per the project rule on helper types
 * referenced from mixin code.
 */
public final class IandfLastPosStore {

    // Field keys, one per last* field in StructureGenerator.
    public static final String MAUSOLEUM = "mausoleum";
    public static final String DRAGON_ROOST = "dragonRoost";
    public static final String DRAGON_CAVE = "dragonCave";
    public static final String CYCLOPS_CAVE = "cyclopsCave";
    public static final String MYRMEX_HIVE = "myrmexHive";
    public static final String SNOW_VILLAGE = "snowVillage";
    public static final String PIXIE_VILLAGE = "pixieVillage";
    public static final String HYDRA_CAVE = "hydraCave";
    public static final String SIREN_ISLAND = "sirenIsland";
    public static final String GORGON_TEMPLE = "gorgonTemple";

    /** dimId -> (fieldKey -> last position). */
    private static final Map<Integer, Map<String, BlockPos>> BY_DIM =
            new ConcurrentHashMap<Integer, Map<String, BlockPos>>();

    private IandfLastPosStore() {
    }

    /** @return the stored position for this dimension/field, or {@code null} if none yet. */
    public static BlockPos get(int dim, String key) {
        Map<String, BlockPos> perDim = BY_DIM.get(Integer.valueOf(dim));
        return perDim == null ? null : perDim.get(key);
    }

    /** Stores (or clears, when {@code pos} is null) the position for this dimension/field. */
    public static void put(int dim, String key, BlockPos pos) {
        Integer dimKey = Integer.valueOf(dim);
        Map<String, BlockPos> perDim = BY_DIM.get(dimKey);
        if (perDim == null) {
            perDim = new ConcurrentHashMap<String, BlockPos>();
            Map<String, BlockPos> existing = BY_DIM.putIfAbsent(dimKey, perDim);
            if (existing != null) {
                perDim = existing;
            }
        }
        if (pos == null) {
            perDim.remove(key);
        } else {
            perDim.put(key, pos);
        }
    }
}
