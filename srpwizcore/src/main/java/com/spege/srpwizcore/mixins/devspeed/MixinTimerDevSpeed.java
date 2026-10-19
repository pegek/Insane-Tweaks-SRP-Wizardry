package com.spege.srpwizcore.mixins.devspeed;

import com.spege.srpwizcore.devspeed.DevSpeedState;

import net.minecraft.util.Timer;

import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Dev-only game speed, client half: makes the frame timer step at the server's rate.
 *
 * <p>Without it the client keeps interpolating and ticking its own world at 20 TPS while the
 * server runs faster — entities rubber-band and the client-side day/night lags behind.
 * {@code Timer.updateTimer} ({@code func_74275_a}) reads {@code tickLength}
 * ({@code field_194149_e}) exactly once (javap on the SRG Forge jar); this redirects that read.
 *
 * <p>A redirect rather than writing the field at HEAD (which is what the third-party TickRate
 * mod does): nothing is mutated, so dropping back to x1 is exact by construction, and a
 * {@code Timer} that some mod built with a step other than 50 ms is left alone. No other mod in
 * the pack mixes {@code Timer} (cleanmix.log 2026-10-17), and Cleanroom ships no patch for it.
 *
 * <p>{@code Timer} is client-only, hence the {@code client} list. SRG names with
 * {@code remap = false}, so this matches only in the obfuscated runtime — under
 * {@code runClient} the shadow does not resolve. Test with the built jar.
 */
@Mixin(Timer.class)
public abstract class MixinTimerDevSpeed {

    @Shadow(remap = false)
    private float field_194149_e;

    @Redirect(method = "func_74275_a",
            at = @At(value = "FIELD", opcode = Opcodes.GETFIELD,
                    target = "Lnet/minecraft/util/Timer;field_194149_e:F"),
            remap = false, require = 0)
    private float srpwizcore$devSpeedTickLength(Timer self) {
        DevSpeedState.clientHookFired = true;
        float override = DevSpeedState.clientMsPerTick;
        float own = this.field_194149_e;
        if (override > 0.0F && own == (float) DevSpeedState.VANILLA_MS_PER_TICK) {
            return override;
        }
        return own;
    }
}
