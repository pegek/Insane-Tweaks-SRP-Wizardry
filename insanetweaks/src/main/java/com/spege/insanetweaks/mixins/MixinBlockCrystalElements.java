package com.spege.insanetweaks.mixins;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import com.spege.insanetweaks.util.NativeElements;

import electroblob.wizardry.block.BlockCrystal;
import electroblob.wizardry.constants.Element;

/**
 * Stops a ninth crystal block variant appearing in creative and JEI. Only {@code getSubBlocks} is
 * redirected; {@code getStateFromMeta} must keep seeing the full enum or a blockstate round trip
 * would resolve to the wrong element.
 */
@Mixin(value = BlockCrystal.class, remap = false)
public abstract class MixinBlockCrystalElements {

    @Redirect(method = { "func_149666_a", "getSubBlocks" },
            at = @At(value = "INVOKE",
                     target = "Lelectroblob/wizardry/constants/Element;values()[Lelectroblob/wizardry/constants/Element;"))
    private Element[] insanetweaks$narrowCrystalBlockSubtypes() {
        return NativeElements.values();
    }
}
