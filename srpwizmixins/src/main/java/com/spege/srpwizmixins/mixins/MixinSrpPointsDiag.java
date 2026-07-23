package com.spege.srpwizmixins.mixins;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.dhanantry.scapeandrunparasites.util.config.SRPConfigSystems;
import com.dhanantry.scapeandrunparasites.world.SRPSaveData;
import com.spege.srpwizmixins.config.SrpWizMixinsConfig;

import net.minecraft.world.World;

/**
 * Milestone 0 diagnostic (read-only): why does a dimension get the wrong starting points?
 *
 * <p>Live debug showed dim 111 (configured {@code 111;8;600000000}) ending up with ~100 / the
 * default -300 instead of 600M, even though the decompiled {@link SRPSaveData#get} applies the
 * 3rd "points" token via {@code setTotalKills(..., p3=false, ...)} for phases >= 0 and -1. The
 * suspected causes are (a) init ORDER - the per-dim record being seeded with the global default
 * by {@code addDim} before the starting list runs - and (b) the {@code choice} multiplier
 * (x0.5 / x3 / x10) scaling positive values inside setTotalKills.
 *
 * <p>This traces both: {@code addDim} (default seed), both {@code setTotalKills} overloads (raw
 * config-init entry -> internal worker), and the stored result after the choice multiplier.
 * Purely observational. Gated on {@link ModConfig#srpCompat}.debugLogging (read live).
 */
@Mixin(value = SRPSaveData.class, remap = false)
public abstract class MixinSrpPointsDiag {

    private static final Logger LOGGER = LogManager.getLogger("insanetweaks");

    @Shadow(remap = false)
    public abstract int getTotalKills(int dim);

    @Shadow(remap = false)
    public abstract int getChoice();

    @Shadow(remap = false)
    public abstract byte getEvolutionPhase(int dim);

    @Inject(method = "addDim(I)V", at = @At("HEAD"), remap = false)
    private static void insanetweaks$logAddDim(int dim, CallbackInfo ci) {
        if (!SrpWizMixinsConfig.srpCompat.debugLogging) {
            return;
        }
        LOGGER.info("[InsaneTweaks] SRP-diag addDim: dim={} seeding defaultPhase={} defaultPoints={}",
                dim, SRPConfigSystems.defaultEvoPhase, SRPConfigSystems.defaultEvoPoints);
    }

    // 6-arg overload: the entry point the config-init loop in get() actually calls.
    @Inject(method = "setTotalKills(IIZLnet/minecraft/world/World;ZI)Z", at = @At("HEAD"), remap = false)
    private void insanetweaks$logSetKills6(int dim, int value, boolean canChangePhase, World world,
            boolean flag, int code, CallbackInfoReturnable<Boolean> cir) {
        if (!SrpWizMixinsConfig.srpCompat.debugLogging || world == null || world.isRemote) {
            return;
        }
        LOGGER.info(
                "[InsaneTweaks] SRP-diag setTotalKills(6) IN: dim={} value={} canChangePhase={} code={} choice={} phaseNow={} pointsBefore={}",
                dim, value, canChangePhase, code, getChoice(), getEvolutionPhase(dim), getTotalKills(dim));
    }

    // 7-arg worker overload that applies the choice multiplier and stores the value.
    @Inject(method = "setTotalKills(IIZLnet/minecraft/world/World;ZZI)Z", at = @At("HEAD"), remap = false)
    private void insanetweaks$logSetKills7(int dim, int value, boolean canChangePhase, World world,
            boolean flag5, boolean flag6, int code, CallbackInfoReturnable<Boolean> cir) {
        if (!SrpWizMixinsConfig.srpCompat.debugLogging || world == null || world.isRemote) {
            return;
        }
        LOGGER.info(
                "[InsaneTweaks] SRP-diag setTotalKills(7) IN: dim={} value={} code={} choice={} pointsBefore={}",
                dim, value, code, getChoice(), getTotalKills(dim));
    }

    @Inject(method = "setTotalKills(IIZLnet/minecraft/world/World;ZZI)Z", at = @At("RETURN"), remap = false)
    private void insanetweaks$logSetKills7Ret(int dim, int value, boolean canChangePhase, World world,
            boolean flag5, boolean flag6, int code, CallbackInfoReturnable<Boolean> cir) {
        if (!SrpWizMixinsConfig.srpCompat.debugLogging || world == null || world.isRemote) {
            return;
        }
        LOGGER.info(
                "[InsaneTweaks] SRP-diag setTotalKills(7) RESULT: dim={} accepted={} pointsAfter={} phaseAfter={}",
                dim, cir.getReturnValue(), getTotalKills(dim), getEvolutionPhase(dim));
    }
}
