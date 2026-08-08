package com.spege.insanetweaks.mixins;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import com.spege.insanetweaks.util.NativeElements;

import electroblob.wizardry.block.BlockRunestone;
import electroblob.wizardry.constants.Element;

/**
 * Same reasoning as {@code MixinBlockCrystalElements}, for runestones.
 *
 * <p>The {@code <clinit>} redirect narrows the {@code ELEMENT} {@code PropertyEnum} so the
 * runestone's blockstate set stays <b>identical</b> to its pre-Abomination content - the seven
 * non-MAGIC native elements. Without it the block gains an Abomination variant that no blockstate
 * JSON or model covers, which logs resource errors on every launch. Unlike the pedestal this block
 * does not overflow metadata (one property, {@code meta = ordinal()}), so this is correctness of the
 * variant set rather than a crash fix.
 *
 * <p>{@code getStateFromMeta} stays untouched: it indexes the full {@code Element.values()} array
 * with the raw ordinal, matching the encoder.
 */
@Mixin(value = BlockRunestone.class, remap = false)
public abstract class MixinBlockRunestoneElements {

    @Redirect(method = { "func_149666_a", "getSubBlocks" },
            at = @At(value = "INVOKE",
                     target = "Lelectroblob/wizardry/constants/Element;values()[Lelectroblob/wizardry/constants/Element;"))
    private Element[] insanetweaks$narrowRunestoneSubtypes() {
        return NativeElements.values();
    }

    @Redirect(method = "<clinit>",
            at = @At(value = "INVOKE",
                     target = "Lelectroblob/wizardry/constants/Element;values()[Lelectroblob/wizardry/constants/Element;"))
    private static Element[] insanetweaks$narrowRunestoneProperty() {
        return NativeElements.values();
    }
}
