package com.spege.srpwizcore.mixins.doomlike;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import com.spege.srpwizcore.SrpWizCore;
import com.spege.srpwizcore.config.SrpWizCoreConfig;
import com.spege.srpwizcore.util.PerfGlueState;

import jaredbgreat.dldungeons.builder.Builder;
import jaredbgreat.dldungeons.planner.Dungeon;
import jaredbgreat.dldungeons.planner.mapping.MapMatrix;

/**
 * Doomlike Dungeons: {@code Builder.buildDungeonChunk} null-checks the cached {@code Dungeon}
 * but not {@code Dungeon.map}, so a plan whose map was never filled throws a
 * {@code NullPointerException} once per chunk it spans — 364 exceptions and 6393 lines of
 * synchronous log output in a single three-minute burst in dim 150 (2026-07-26, see
 * {@code notes/flare_dim150_spike_report_2026-07-25.md} §6c).
 *
 * <p>The redirect skips the build call for such plans instead. It adds and removes no dungeons:
 * the original code could not build them either, it only threw on the way out.
 *
 * <p>Gated on {@code perfGlue.doomlikeNullMapGuard}, read live — with the gate off the original
 * NPE is restored so the flag means something and the problem stays visible in the log.
 */
@Mixin(value = Builder.class, remap = false)
public class MixinDoomlikeBuilder {

    @Redirect(
            method = "buildDungeonChunk",
            at = @At(
                    value = "INVOKE",
                    target = "Ljaredbgreat/dldungeons/planner/mapping/MapMatrix;"
                            + "buildInChunk(Ljaredbgreat/dldungeons/planner/Dungeon;II)V"),
            remap = false)
    private static void srpwizcore$skipNullMap(MapMatrix map, Dungeon dungeon, int x, int z) {
        if (map != null || !SrpWizCoreConfig.perfGlue.doomlikeNullMapGuard) {
            // Normal case, or gate disabled: original behaviour (a null map NPEs, as before).
            map.buildInChunk(dungeon, x, z);
            return;
        }
        int n = ++PerfGlueState.doomlikeNullMapSkips;
        if (n == 1 || (n & 0xFF) == 0) {
            SrpWizCore.LOGGER.warn(
                    "[srpwizcore] Doomlike dungeon plan with null map skipped at chunk {},{} "
                            + "(total skips: {})", Integer.valueOf(x), Integer.valueOf(z),
                    Integer.valueOf(n));
        }
    }
}
