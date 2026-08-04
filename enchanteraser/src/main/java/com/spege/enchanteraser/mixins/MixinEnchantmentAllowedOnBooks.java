package com.spege.enchanteraser.mixins;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.spege.enchanteraser.util.EraserState;

import net.minecraft.enchantment.Enchantment;

/**
 * Backstop: make {@code Enchantment.isAllowedOnBooks()} report false for an erased enchantment.
 *
 * <p>Everything else in this mod patches <b>callers</b>, because that is the only way to intercept a
 * virtual method no matter which subclass implements it. This one deliberately patches the flag
 * itself, to cover a mod that asks {@code isAllowedOnBooks()} directly instead of going through any of
 * the four vanilla paths we close. Forge added the method and only ever reads it in
 * {@code EnchantmentHelper.getEnchantmentDatas}, which we already filter, so this changes nothing on
 * its own — it exists purely to shrink the surface a third-party roller could use.
 *
 * <p>🚨 <b>This is a partial layer and must never be mistaken for a complete one.</b> The method is
 * virtual, so an injection into {@code Enchantment} is skipped entirely for any enchantment that
 * overrides it — which is exactly why the mod's main mechanism patches callers instead. Measured
 * against the pack's current list with {@code javap}: Electroblob's Wizardry
 * ({@code EnchantmentMagicProtection}) and CQR ({@code EnchantmentLightningProtection}) do not
 * override it, so those four are covered; SoManyEnchantments ({@code EnchantmentBase}) and all 22
 * Better Survival enchantments do override it, so those nine are not. Removing any of the caller-side
 * mixins because "the flag is false now" would break the mod.
 *
 * <p>Collision-checked against a live {@code cleanmix.log}: {@code Enchantment} carries two other
 * mixins in the DEv 1.2 pack — BaublesEX ({@code canEnchantBaubles0/1}, i.e. {@code canApply} and
 * {@code canApplyAtEnchantingTable}) and UniversalTweaks ({@code getTranslatedName}). Neither touches
 * this method and neither declares an {@code @Overwrite}, so no priority juggling is needed. Note that
 * this mixin deliberately does <em>not</em> also cover {@code canApply} /
 * {@code canApplyAtEnchantingTable}: those are already closed caller-side at the anvil and in the
 * candidate pool, and injecting there would race BaublesEX over the same return value for no gain.
 *
 * <p>{@code isAllowedOnBooks} is a Forge addition, so it is not SRG-mapped and the plain name matches
 * in both dev and the obfuscated runtime.
 */
@Mixin(Enchantment.class)
public class MixinEnchantmentAllowedOnBooks {

    @Inject(method = "isAllowedOnBooks", at = @At("HEAD"), cancellable = true, remap = false)
    private void enchanteraser$refuseErasedOnBooks(CallbackInfoReturnable<Boolean> cir) {
        // The cast idiom rather than @Shadow: this mixin declares no members of its own, and
        // getRegistryName is safely null during registration, where isDisabled then returns false.
        if (EraserState.isDisabled((Enchantment) (Object) this)) {
            cir.setReturnValue(Boolean.FALSE);
        }
    }
}
