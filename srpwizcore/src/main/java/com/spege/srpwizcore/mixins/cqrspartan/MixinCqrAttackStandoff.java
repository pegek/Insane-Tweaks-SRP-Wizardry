package com.spege.srpwizcore.mixins.cqrspartan;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.spege.srpwizcore.util.CqrReachHelper;

import net.minecraft.entity.EntityLivingBase;

/**
 * Standoff behaviour for reach weapons. {@code EntityAIAttack.updatePath} paths the mob
 * straight into the target every tick, so even with extended reach it ends up hugging the
 * player like every other mob. This HEAD hook cancels the path update (and clears the
 * current path) while the target is comfortably inside the weapon's extended reach —
 * {@link CqrReachHelper#shouldStandoff} owns the checks, the flag is read live. Subclasses
 * (e.g. EntityAIBackstab) inherit the injected method body unless they override it.
 */
@Mixin(targets = "team.cqr.cqrepoured.entity.ai.attack.EntityAIAttack", remap = false)
public class MixinCqrAttackStandoff {

    @Inject(
            method = "updatePath(Lnet/minecraft/entity/EntityLivingBase;)V",
            at = @At("HEAD"),
            cancellable = true)
    private void srpwizcore$standoff(EntityLivingBase target, CallbackInfo ci) {
        if (CqrReachHelper.shouldStandoff(this, target)) {
            ci.cancel();
        }
    }
}
