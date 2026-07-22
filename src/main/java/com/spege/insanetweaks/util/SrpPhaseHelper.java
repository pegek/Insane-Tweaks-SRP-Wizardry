package com.spege.insanetweaks.util;

import com.spege.insanetweaks.InsaneTweaksMod;

import net.minecraft.world.World;
import net.minecraftforge.fml.common.Loader;

/**
 * Reads the current SRP evolution phase (0-10) via SRParasites' {@code SRPSaveData}, guarded so it
 * is completely inert when SRParasites is absent (returns 0, never classloads SRP types). Uses the
 * canonical save-data id 104 used elsewhere in the codebase (see {@code EntitySimWizard} phase
 * scaling and {@code SrpWizardryAssimilationHelper}).
 */
public final class SrpPhaseHelper {

    /** Canonical SRP save-data / sortmap id used across the codebase. */
    private static final int SRP_SAVE_DATA_ID = 104;

    private static Boolean srpLoaded;

    private SrpPhaseHelper() {
    }

    private static boolean isSrpLoaded() {
        if (srpLoaded == null) {
            srpLoaded = Boolean.valueOf(Loader.isModLoaded(InsaneTweaksMod.SRP_MODID));
        }
        return srpLoaded.booleanValue();
    }

    /** Current SRP evolution phase (0-10), or 0 when SRP is absent or the data is unavailable. */
    public static int getEvolutionPhase(World world) {
        if (world == null || world.isRemote || !isSrpLoaded()) {
            return 0;
        }
        return Access.read(world);
    }

    /** Isolated so SRP classes only load when SRParasites is actually present. */
    private static final class Access {
        private Access() {
        }

        static int read(World world) {
            try {
                com.dhanantry.scapeandrunparasites.world.SRPSaveData data =
                        com.dhanantry.scapeandrunparasites.world.SRPSaveData.get(world, SRP_SAVE_DATA_ID);
                if (data == null) {
                    return 0;
                }
                return data.getEvolutionPhase(SRP_SAVE_DATA_ID) & 0xFF;
            } catch (Exception ex) {
                return 0;
            }
        }
    }
}
