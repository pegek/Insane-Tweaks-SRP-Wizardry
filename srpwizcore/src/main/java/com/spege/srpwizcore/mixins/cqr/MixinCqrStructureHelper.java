package com.spege.srpwizcore.mixins.cqr;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import com.spege.srpwizcore.SrpWizCore;
import com.spege.srpwizcore.config.SrpWizCoreConfig;
import com.spege.srpwizcore.util.ForgeWorldAccessor;
import com.spege.srpwizcore.util.OtgStructureFlags;
import com.spege.srpwizcore.util.PerfGlueState;

import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.gen.IChunkGenerator;

/**
 * CQR's {@code StructureHelper.isStructureInRange} falls back to
 * {@code IChunkGenerator.getNearestStructurePos} for any non-vanilla generator. Under OTG that
 * is a spiral ring scan running the full biome layer chain per candidate cell; for a structure
 * DISABLED in the dimension's {@code WorldConfig} it can never find anything and always runs to
 * the attempt limit — measured at 143.6 s, 49.4% of the dim-150 worldgen profile (2026-07-26,
 * {@code notes/cqr_structure_scan_perf_2026-07-26.md} §7).
 *
 * <p>The redirect returns {@code null} ("no such structure nearby") for disabled structures,
 * which is exactly what the scan would have returned had it been allowed to finish, so dungeon
 * placement is unchanged. The method invokes the scan twice — once with
 * {@code findUnexplored=false} and once more in its {@code NullPointerException} handler with
 * {@code findUnexplored=true} — and this redirect binds to both; both {@code null} results fall
 * through to CQR's own {@code return false}.
 *
 * <p>CQR is targeted by string, so this mixin carries no compile dependency on it. OTG types are
 * reached only through {@link OtgStructureFlags}, behind the {@code instanceof} test, so a pack
 * without OTG never loads them.
 */
@Mixin(targets = "team.cqr.cqrepoured.util.StructureHelper", remap = false)
public class MixinCqrStructureHelper {

    @Redirect(
            method = "isStructureInRange(Lnet/minecraft/world/World;"
                    + "Lnet/minecraft/util/math/BlockPos;ILjava/lang/String;)Z",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/gen/IChunkGenerator;func_180513_a"
                            + "(Lnet/minecraft/world/World;Ljava/lang/String;"
                            + "Lnet/minecraft/util/math/BlockPos;Z)"
                            + "Lnet/minecraft/util/math/BlockPos;"),
            remap = false)
    private static BlockPos srpwizcore$skipDisabledOtgScan(
            IChunkGenerator generator, World world, String structureName,
            BlockPos pos, boolean findUnexplored) {
        if (SrpWizCoreConfig.perfGlue.cqrStructureScanGuard
                && generator instanceof ForgeWorldAccessor
                && !OtgStructureFlags.isEnabled(
                        ((ForgeWorldAccessor) generator).insanetweaks$getForgeWorld(),
                        structureName)) {
            int n = ++PerfGlueState.cqrScansSkipped;
            if (n == 1 || (n & 0x3FF) == 0) {
                SrpWizCore.LOGGER.info(
                        "[srpwizcore] CQR structure scan skipped for '{}' (disabled in OTG "
                                + "WorldConfig, dim {}; total skips: {})",
                        structureName, Integer.valueOf(world.provider.getDimension()),
                        Integer.valueOf(n));
            }
            // null == "no such structure in range" — identical to what a completed scan for a
            // structure that cannot exist would have returned.
            return null;
        }
        return generator.getNearestStructurePos(world, structureName, pos, findUnexplored);
    }
}
