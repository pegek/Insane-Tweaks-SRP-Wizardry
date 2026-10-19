package com.spege.srpwizcore.mixins.devspeed;

import com.spege.srpwizcore.devspeed.DevSpeedState;

import net.minecraft.server.MinecraftServer;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

/**
 * Dev-only game speed, server half: swaps the tick length of the main loop.
 *
 * <p>{@code MinecraftServer.run()} hardcodes the 50 ms step four times — the "ticks behind"
 * division in the can't-keep-up warning, {@code while (i > 50L)}, {@code i -= 50L} and
 * {@code Thread.sleep(Math.max(1L, 50L - i))}. Replacing all four with one value is a real
 * tick-rate change: {@code getTotalWorldTime()}, day/night, SRP's per-day evolution and every
 * other tick counter advance faster, which {@code /time set} cannot do. Verified on Cleanroom
 * 0.6.13's patched class (binpatch applied to the vanilla jar, 2026-10-17): the loop is vanilla,
 * all four {@code ldc2_w 50} are there.
 *
 * <p>{@code run} is the {@code Runnable} method, not obfuscated, so the selector matches in dev
 * and in the SRG runtime alike. {@code require = 0} is deliberate: a missing constant must not
 * take the mixin (or the boot) down. {@code DevSpeedMixinPlugin.postApply} counts the wired
 * constants on the patched bytecode and logs an ERROR when it is not 4, and the command refuses
 * to run when the handler has never fired. Zero fields, no {@code <clinit>}.
 */
@Mixin(MinecraftServer.class)
public abstract class MixinMinecraftServerDevSpeed {

    @ModifyConstant(method = "run()V", constant = @Constant(longValue = 50L), remap = false,
            require = 0)
    private long srpwizcore$devSpeedMsPerTick(long vanilla) {
        DevSpeedState.serverHookFired = true;
        return DevSpeedState.serverMsPerTick;
    }
}
