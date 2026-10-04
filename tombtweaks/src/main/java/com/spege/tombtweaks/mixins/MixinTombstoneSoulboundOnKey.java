package com.spege.tombtweaks.mixins;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.spege.tombtweaks.util.GraveKeySoulboundPolicy;

import net.minecraft.item.ItemStack;

/**
 * Grave Keys Survive Death = false: the anvil refuses to put Soulbound on a grave key.
 *
 * <p>Once keys are immune to Soulbound the enchantment does nothing on them, so letting the anvil
 * apply it would only eat a book. Soulbound's item category accepts anything with a max stack size
 * of 1, which includes the key; the enchanting table never offers it there anyway (the key's
 * enchantability is 0), so the anvil is the one path that matters.
 *
 * <p>Targets {@code canApply} on {@code TombstoneEnchantment}: {@code EnchantmentSoulBound} does not
 * override it in 4.8.0, so the base class body is the one that runs. Dual selector because this
 * repo has no refmap - {@code canApply} in dev, {@code func_92089_a} in the obfuscated runtime.
 * Injecting into the method body rather than at the anvil's call site composes with
 * enchanteraser's {@code @Redirect} on that same {@code ContainerRepair} invoke.
 */
@Mixin(targets = "ovh.corail.tombstone.enchantment.TombstoneEnchantment", remap = false)
public abstract class MixinTombstoneSoulboundOnKey {

    @Inject(method = {"canApply", "func_92089_a"}, at = @At("HEAD"), cancellable = true, remap = false)
    private void tombtweaks$noSoulboundOnKey(ItemStack stack, CallbackInfoReturnable<Boolean> cir) {
        if (GraveKeySoulboundPolicy.refusesEnchantment(this, stack)) {
            cir.setReturnValue(Boolean.FALSE);
        }
    }
}
