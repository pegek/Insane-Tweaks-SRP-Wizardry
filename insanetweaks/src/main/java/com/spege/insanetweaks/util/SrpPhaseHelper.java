package com.spege.insanetweaks.util;

import java.lang.reflect.Field;
import java.util.List;

import com.spege.insanetweaks.InsaneTweaksMod;
import com.spege.insanetweaks.config.ModConfig;

import net.minecraft.world.World;
import net.minecraftforge.fml.common.Loader;

/**
 * Reads SRP evolution phases for content gates (Corrupted Sapling planting and wave pools, the
 * Petrified Anchor's wake-up), completely inert when SRParasites is absent (never classloads SRP
 * types).
 *
 * <h3>Which phase a gate sees</h3>
 * {@link #getGatingPhase} follows {@code tweaks.srpPhaseSource}:
 * <ul>
 *   <li>{@code GLOBAL_HIGHEST} (default) - the highest phase among the dimensions SRP tracks. DEv 1.2
 *   freezes the overworld at -2 by config while dimension 111 evolves, so a per-dimension read would
 *   lock every overworld gate forever.</li>
 *   <li>{@code DIMENSION} - the phase of the gate's own dimension; -1/-2 count as below every
 *   threshold.</li>
 * </ul>
 *
 * <h3>The bug this replaced (fixed 2026-10-09)</h3>
 * The old version called {@code getEvolutionPhase(104)}, passing the save-data id where SRP expects a
 * DIMENSION id (the same mistake EntitySimWizard fixed earlier). For an untracked dimension SRP's
 * {@code getEvolutionPhase} calls {@code addDim} - so it created a fake dimension 104 in every
 * world's SRP data (phase -1), and {@code -1 & 0xFF} made every gate read 255: the sapling could be
 * planted anywhere and its waves always drew the Late pool. Hence the rule here: never ask SRP for a
 * dimension it does not already track.
 */
public final class SrpPhaseHelper {

    /** SRPSaveData.get only reads this on the client branch; any int works server-side. */
    private static final int SRP_SAVE_DATA_HINT = 104;

    private static Boolean srpLoaded;

    private SrpPhaseHelper() {
    }

    private static boolean isSrpLoaded() {
        if (srpLoaded == null) {
            srpLoaded = Boolean.valueOf(Loader.isModLoaded(InsaneTweaksMod.SRP_MODID));
        }
        return srpLoaded.booleanValue();
    }

    /** The phase a content gate should compare against, per {@code tweaks.srpPhaseSource}. 0 without SRP. */
    public static int getGatingPhase(World world) {
        if (world == null || world.isRemote || !isSrpLoaded()) {
            return 0;
        }
        if (ModConfig.tweaks.srpPhaseSource == SrpPhaseSource.DIMENSION) {
            return Access.dimension(world);
        }
        return Access.highest(world);
    }

    /** Where a gate reads the SRP phase from. */
    public enum SrpPhaseSource {
        GLOBAL_HIGHEST,
        DIMENSION
    }

    /** Isolated so SRP classes only load when SRParasites is actually present. */
    private static final class Access {

        private static Field evolutionField;
        private static boolean evolutionFieldFailed;

        private Access() {
        }

        private static com.dhanantry.scapeandrunparasites.world.SRPSaveData data(World world) {
            return com.dhanantry.scapeandrunparasites.world.SRPSaveData.get(world, SRP_SAVE_DATA_HINT);
        }

        /** Signed phase of the world's own dimension; SRP passes the dimension id at all its call sites. */
        static int dimension(World world) {
            try {
                com.dhanantry.scapeandrunparasites.world.SRPSaveData data = data(world);
                return data == null ? 0 : data.getEvolutionPhase(world.provider.getDimension());
            } catch (Exception ex) {
                return 0;
            }
        }

        /**
         * Highest phase over the dimensions SRP already tracks, read from its private
         * {@code dimEPevolution} list (SRP's own field names are not obfuscated). Reading the list
         * instead of calling getEvolutionPhase per dimension is the point: that call registers
         * any dimension it does not know. Falls back to the own dimension if reflection fails.
         */
        static int highest(World world) {
            try {
                com.dhanantry.scapeandrunparasites.world.SRPSaveData data = data(world);
                if (data == null) {
                    return 0;
                }
                Field field = evolutionField();
                if (field == null) {
                    return dimension(world);
                }
                Object raw = field.get(data);
                if (!(raw instanceof List)) {
                    return dimension(world);
                }
                Object[] phases = ((List<?>) raw).toArray();
                int best = Integer.MIN_VALUE;
                for (Object phase : phases) {
                    if (phase instanceof Byte) {
                        best = Math.max(best, ((Byte) phase).intValue());
                    }
                }
                return best == Integer.MIN_VALUE ? dimension(world) : best;
            } catch (Exception ex) {
                return dimension(world);
            }
        }

        private static synchronized Field evolutionField() {
            if (evolutionField == null && !evolutionFieldFailed) {
                try {
                    Field f = com.dhanantry.scapeandrunparasites.world.SRPSaveData.class
                            .getDeclaredField("dimEPevolution");
                    f.setAccessible(true);
                    evolutionField = f;
                } catch (Exception ex) {
                    evolutionFieldFailed = true;
                    InsaneTweaksMod.LOGGER.warn("[InsaneTweaks] Cannot read SRP's per-dimension phases ({}); "
                            + "phase gates fall back to the gate's own dimension.", ex.toString());
                }
            }
            return evolutionField;
        }
    }
}
