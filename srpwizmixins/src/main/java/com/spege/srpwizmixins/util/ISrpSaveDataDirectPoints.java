package com.spege.srpwizmixins.util;

/**
 * Duck-type interface implemented by {@code SRPSaveData} via {@code MixinSrpSaveDataPoints}.
 *
 * <p>Lets the static {@code createData} redirect handler write a dimension's evolution points
 * directly into the target instance, bypassing the broken {@code setTotalKills} branching.
 *
 * <p>Lives in a NON-mixin package on purpose: classes inside a Mixin config's package
 * ({@code com.spege.srpwizmixins.mixins.*}) are removed from normal classloading and cannot be
 * referenced directly (throws {@code IllegalClassLoadError}). Duck-type accessors must live outside
 * the mixin package tree.
 */
public interface ISrpSaveDataDirectPoints {

    /** Directly set the stored evolution points for {@code dim}, then mark the data dirty. */
    void insanetweaks$setDimPointsDirect(int dim, int points);
}
