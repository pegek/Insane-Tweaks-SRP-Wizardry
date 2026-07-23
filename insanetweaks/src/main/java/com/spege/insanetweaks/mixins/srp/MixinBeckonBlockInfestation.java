package com.spege.insanetweaks.mixins.srp;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.spege.insanetweaks.config.ModConfig;
import com.spege.insanetweaks.sanctuary.SanctuaryRegionHelper;

import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/**
 * Sanctuary Dome - veto SRP's block-infestation conversion when the origin block is inside a
 * protected sanctuary region.
 *
 * <p>{@code BeckonBlockInfestation.beckonInfestation} is SRP's own static utility (not a
 * Minecraft method), invoked from Beckon/infestation logic to convert nearby blocks into their
 * infested variants. We inject at {@code HEAD} and cancel the call outright when the target
 * position falls inside a sanctuary, before any block conversions happen.
 *
 * <p>Confirmed via {@code javap -p -classpath libs/SRParasites-1.10.7.jar
 * com.dhanantry.scapeandrunparasites.util.convert.BeckonBlockInfestation} (2026-07-19):
 * {@code public static void beckonInfestation(World, BlockPos, Random, int, boolean)} - not
 * overloaded, so the plain method name is unambiguous. Gated on
 * {@link ModConfig#sanctuary}.vetoBlockInfestation.
 */
@Mixin(targets = "com.dhanantry.scapeandrunparasites.util.convert.BeckonBlockInfestation", remap = false)
public class MixinBeckonBlockInfestation {

    @Inject(method = "beckonInfestation", at = @At("HEAD"), cancellable = true, remap = false)
    private static void insanetweaks$vetoInSanctuary(World world, BlockPos pos, java.util.Random rand,
            int a, boolean b, CallbackInfo ci) {
        if (!ModConfig.sanctuary.vetoBlockInfestation) {
            return;
        }
        if (SanctuaryRegionHelper.isProtected(world, pos)) {
            ci.cancel();
        }
    }
}
