package com.spege.insanetweaks.mixins;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import com.spege.insanetweaks.util.NativeElements;

import electroblob.wizardry.constants.Element;
import electroblob.wizardry.item.ItemSpectralDust;

/**
 * Same reasoning as {@code MixinItemCrystalElements}, for spectral dust.
 */
@Mixin(value = ItemSpectralDust.class, remap = false)
public abstract class MixinItemSpectralDustElements {

    @Redirect(method = { "func_150895_a", "getSubItems" },
            at = @At(value = "INVOKE",
                     target = "Lelectroblob/wizardry/constants/Element;values()[Lelectroblob/wizardry/constants/Element;"))
    private Element[] insanetweaks$narrowDustSubtypes() {
        return NativeElements.values();
    }
}
