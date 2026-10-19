package com.spege.srpwizcore.mixins.bloodmoon;

import com.spege.srpwizcore.util.BloodmoonLightmap;

import net.minecraft.client.renderer.EntityRenderer;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Slot-free replacement for Bloodmoon 1.5.3's red-lightmap patch.
 *
 * <p>Bloodmoon's coremod ({@code lumien.bloodmoon.asm.ClassTransformer.patchEntityRendererClass})
 * finds {@code ISTORE 23} in {@code updateLightmap} and, after it, rewrites locals 21/22/23 through
 * {@code ClientBloodmoonHandler.manipulateRed/Green/Blue(i, c)} with the loop index read from
 * local 5. All four slot numbers are hardcoded. Cleanroom 0.7 recompiles Minecraft from a new
 * decompilation that adds an alpha local ({@code j = 255}) at 22, pushing r/g/b to 23/24/25 — so
 * the anchor now lands on the red store and the inserted {@code ILOAD 21} reads a float:
 * {@code VerifyError: Bad local variable type} at {@code func_78472_g @863} on client init.
 *
 * <p>{@code BloodmoonMixinPlugin.preApply} removes Bloodmoon's inserted instructions by shape,
 * whatever slots they name, and this injection does the same work after the loop instead of
 * inside it: just before {@code lightmapTexture.updateDynamicTexture()} uploads
 * {@code lightmapColors}, every texel is split, passed through the same three handler methods with
 * the same arguments, and repacked. Equivalent to the original because the three channels are the
 * final 0..255 ints by the time Bloodmoon read them — and it is equivalent on Cleanroom 0.6 too,
 * where the strip removes a hook that was still valid, so the pack behaves identically on both.
 *
 * <p>{@code @Shadow} and the {@code @At} target use runtime SRG names with {@code remap = false},
 * so neither matches under {@code runClient}; test with the built jar.
 */
@Mixin(EntityRenderer.class)
public abstract class MixinEntityRendererBloodmoonLightmap {

    @Shadow(remap = false)
    @Final
    private int[] field_78504_Q;

    @Inject(method = {"updateLightmap", "func_78472_g"},
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/texture/DynamicTexture;func_110564_a()V",
                    remap = false),
            remap = false)
    private void srpwizcore$bloodmoonLightmap(float partialTicks, CallbackInfo ci) {
        BloodmoonLightmap.apply(this.field_78504_Q);
    }
}
