package com.spege.insanetweaks.mixins;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import com.spege.insanetweaks.util.NativeElements;

import electroblob.wizardry.block.BlockRunestone;
import electroblob.wizardry.constants.Element;

/**
 * Same reasoning as {@code MixinBlockCrystalElements}, for runestones. {@code <clinit>} and
 * {@code getStateFromMeta} are deliberately untouched: the former builds the {@code PropertyEnum}
 * from the full enum and narrowing it would shrink the block's legal blockstate variants.
 */
@Mixin(value = BlockRunestone.class, remap = false)
public abstract class MixinBlockRunestoneElements {

    @Redirect(method = { "func_149666_a", "getSubBlocks" },
            at = @At(value = "INVOKE",
                     target = "Lelectroblob/wizardry/constants/Element;values()[Lelectroblob/wizardry/constants/Element;"))
    private Element[] insanetweaks$narrowRunestoneSubtypes() {
        return NativeElements.values();
    }
}
