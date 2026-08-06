package com.spege.srpwizcore.mixins;

import com.spege.srpwizcore.bbcompat.BrokenHeartProvider;

import net.minecraft.entity.EntityLivingBase;
import net.minecraft.util.DamageSource;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Lets the Bountiful Baubles Broken Heart trinket answer the vanilla death-protection question.
 *
 * <p>This is the only hook that still reaches a dying player in this pack. FirstAid cancels
 * {@code LivingHurtEvent} for every real player, which stops {@code LivingDamageEvent} from ever
 * being posted for one, so the trinket's own listener can never run — the reasoning in full lives
 * on {@link BrokenHeartProvider}. FirstAid does, however, call
 * {@code checkTotemDeathProtection} deliberately from {@code CommonUtils.killPlayer}, and honours
 * a {@code true} answer by restoring the body parts. So the trinket is re-expressed as a totem.
 *
 * <p><b>HEAD, not RETURN.</b> Two other mods already inject here — BaublesEX redirects
 * {@code getHeldItem} so its own totem bauble is found, and SoManyEnchantments injects at HEAD for
 * its Rune of Resurrection. Injecting at RETURN would be the tidier "only if nobody else saved
 * them" ordering, but Mixin implements a cancelling HEAD callback by inserting a fresh return
 * instruction, so whether our RETURN injector sees it depends on which mixin was applied first.
 * HEAD is order-independent in the only way that matters: every injector here declines unless its
 * own item is present, so they can only compete when the player carries two death saves at once,
 * and then either answer is correct — the player lives. Spending the reusable trinket (it costs
 * max health and sleep gives it back) before a consumable totem is the friendlier of the two.
 *
 * <p>No {@code @Overwrite} of this method exists anywhere in the pack — checked by decompressing
 * and scanning every jar for {@code func_190628_d}, which found only the two injectors above.
 *
 * <p>No fields: nothing to merge through {@code <clinit>} into {@code EntityLivingBase}, which is
 * the VerifyError this repo has already paid for once. All state lives in
 * {@link BrokenHeartProvider}, outside the mixin package.
 */
@Mixin(EntityLivingBase.class)
public class MixinEntityLivingBaseBrokenHeart {

    @Inject(method = { "checkTotemDeathProtection", "func_190628_d" }, at = @At("HEAD"),
            cancellable = true, remap = false)
    private void srpwizcore$brokenHeartDeathSave(DamageSource source,
            CallbackInfoReturnable<Boolean> cir) {
        if (BrokenHeartProvider.tryDeathSave((EntityLivingBase) (Object) this, source)) {
            cir.setReturnValue(Boolean.TRUE);
        }
    }
}
