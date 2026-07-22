package com.spege.insanetweaks.util;

import java.lang.reflect.Method;

import com.spege.insanetweaks.InsaneTweaksMod;

import net.minecraft.block.state.IBlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.fml.common.Loader;

/**
 * Reflection bridge to SRP's own (1.10.7) infestation-purification internals, so the Sanctuary can
 * reuse SRP's authoritative, anti-lag mechanisms instead of fighting them:
 *
 * <ul>
 *   <li><b>R2 block mapping</b> — {@code PurifyMappings.isSrp} / {@code mapToVanillaState}: SRP's
 *       data-driven infested-&gt;vanilla block map (more complete than our heuristic
 *       {@link SrpPurificationHelper}).</li>
 *   <li><b>R1 biome reset</b> — {@code BlockBiomePurifier.killBiome(World, BlockPos, int radius)}:
 *       resets parasite biomes to natural in a square radius via SRP's throttled
 *       {@code BiomeUpdateQueue}, killing the biome-driven spread at the root. Biome only — it does
 *       not touch blocks, so the block cleanse still runs alongside it.</li>
 * </ul>
 *
 * All calls are null-safe and degrade to no-ops when SRP is absent or a signature drifts; callers
 * keep their existing fallbacks. Verified against SRParasites 1.10.7
 * (notes/decompiled_mods/srp_sourcecode/fulljar).
 */
public final class SrpNativePurifyHelper {

    private static final String PURIFY_MAPPINGS = "com.dhanantry.scapeandrunparasites.block.PurifyMappings";
    private static final String BIOME_PURIFIER = "com.dhanantry.scapeandrunparasites.block.BlockBiomePurifier";
    private static final String MAPPINGS_FILE = "srparasites_purify_mappings.txt";

    private static boolean initialized;
    private static boolean available;
    private static boolean mappingsEnsured;

    private static Method ensureLoadedMethod;   // PurifyMappings.ensureLoaded(World, String)
    private static Method isSrpMethod;           // PurifyMappings.isSrp(IBlockState)
    private static Method mapToVanillaMethod;    // PurifyMappings.mapToVanillaState(IBlockState)
    private static Method killBiomeMethod;       // BlockBiomePurifier.killBiome(World, BlockPos, int)

    private SrpNativePurifyHelper() {
    }

    public static boolean isAvailable() {
        ensureInitialized();
        return available;
    }

    /** Load SRP's block-mapping table once (idempotent; SRP guards it internally too). */
    public static void ensureMappingsLoaded(World world) {
        if (mappingsEnsured || world == null || !isAvailable() || ensureLoadedMethod == null) {
            return;
        }
        try {
            ensureLoadedMethod.invoke(null, world, MAPPINGS_FILE);
            mappingsEnsured = true;
        } catch (Exception e) {
            logFailure("load SRP purify mappings", e);
        }
    }

    /** True if SRP's own mapping treats this as a purifiable infested block. */
    public static boolean isSrpPurifiable(IBlockState state) {
        if (state == null || !isAvailable() || isSrpMethod == null) {
            return false;
        }
        try {
            Object r = isSrpMethod.invoke(null, state);
            return r instanceof Boolean && (Boolean) r;
        } catch (Exception e) {
            logFailure("query SRP purify mapping", e);
            return false;
        }
    }

    /** SRP's authoritative vanilla replacement for an infested state, or null if unmapped. */
    public static IBlockState mapToVanilla(IBlockState state) {
        if (state == null || !isAvailable() || mapToVanillaMethod == null) {
            return null;
        }
        try {
            Object r = mapToVanillaMethod.invoke(null, state);
            return r instanceof IBlockState ? (IBlockState) r : null;
        } catch (Exception e) {
            logFailure("map SRP block to vanilla", e);
            return null;
        }
    }

    /**
     * Reset parasite biomes to natural within {@code radius} (square) of {@code pos}, via SRP's
     * throttled BiomeUpdateQueue. Server-side only. Keep {@code radius} modest (loaded chunks) —
     * SRP's own purifier uses 16, and a large radius would probe unloaded chunks.
     */
    public static void killBiome(World world, BlockPos pos, int radius) {
        if (world == null || world.isRemote || pos == null || radius <= 0
                || !isAvailable() || killBiomeMethod == null) {
            return;
        }
        try {
            killBiomeMethod.invoke(null, world, pos, Integer.valueOf(radius));
        } catch (Exception e) {
            logFailure("run SRP killBiome", e);
        }
    }

    private static void ensureInitialized() {
        if (initialized) {
            return;
        }
        initialized = true;

        if (!Loader.isModLoaded(InsaneTweaksMod.SRP_MODID)) {
            return;
        }
        try {
            Class<?> mappings = Class.forName(PURIFY_MAPPINGS);
            Class<?> purifier = Class.forName(BIOME_PURIFIER);

            ensureLoadedMethod = mappings.getMethod("ensureLoaded", World.class, String.class);
            isSrpMethod = mappings.getMethod("isSrp", IBlockState.class);
            mapToVanillaMethod = mappings.getMethod("mapToVanillaState", IBlockState.class);
            killBiomeMethod = purifier.getMethod("killBiome", World.class, BlockPos.class, int.class);

            available = isSrpMethod != null && mapToVanillaMethod != null && killBiomeMethod != null;
        } catch (Throwable t) {
            available = false;
            logFailure("initialize SRP native purify bridge", t);
        }
    }

    private static void logFailure(String action, Throwable t) {
        if (com.spege.insanetweaks.config.ModConfig.sanctuary.debugLogging) {
            InsaneTweaksMod.LOGGER.warn("[InsaneTweaks] SRP native purify: failed to {}: {}", action, t.getMessage());
        }
    }
}
