package com.spege.srpwizcore.mixins.cqrspartan;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.spege.srpwizcore.util.CqrReachHelper;

import net.minecraft.entity.EntityLivingBase;

/**
 * Spartan reach for CQR melee AI. {@code AbstractEntityCQR.getAttackReach} (plain CQR
 * method, unobfuscated) already grants a reach bonus — but only for CQR's own
 * {@code ItemSpearBase}; a gear-swapped mob holding a Spartan pike hits from dagger
 * distance. This RETURN hook adds the Spartan {@code reach} property bonus computed in
 * {@link CqrReachHelper} (flag and factor read live). {@code isInAttackReach} and the
 * standoff logic both flow through this getter, so one hook covers everything.
 */
@Mixin(targets = "team.cqr.cqrepoured.entity.bases.AbstractEntityCQR", remap = false)
public abstract class MixinCqrAttackReach {

    @Inject(
            method = "getAttackReach(Lnet/minecraft/entity/EntityLivingBase;)D",
            at = @At("RETURN"),
            cancellable = true)
    private void srpwizcore$spartanReach(EntityLivingBase target,
            CallbackInfoReturnable<Double> cir) {
        double bonus = CqrReachHelper.reachBonus((EntityLivingBase) (Object) this);
        if (bonus > 0.0D) {
            cir.setReturnValue(cir.getReturnValueD() + bonus);
        }
    }
}
