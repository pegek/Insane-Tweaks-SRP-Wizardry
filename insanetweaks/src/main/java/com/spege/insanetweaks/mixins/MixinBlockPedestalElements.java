package com.spege.insanetweaks.mixins;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import com.spege.insanetweaks.util.NativeElements;

import electroblob.wizardry.block.BlockPedestal;
import electroblob.wizardry.constants.Element;

/**
 * Same reasoning as {@code MixinBlockCrystalElements}, for imbuement pedestals. {@code <clinit>} and
 * {@code getStateFromMeta} are deliberately untouched, for the same reason as the runestone.
 */
@Mixin(value = BlockPedestal.class, remap = false)
public abstract class MixinBlockPedestalElements {

    @Redirect(method = { "func_149666_a", "getSubBlocks" },
            at = @At(value = "INVOKE",
                     target = "Lelectroblob/wizardry/constants/Element;values()[Lelectroblob/wizardry/constants/Element;"))
    private Element[] insanetweaks$narrowPedestalSubtypes() {
        return NativeElements.values();
    }
}
