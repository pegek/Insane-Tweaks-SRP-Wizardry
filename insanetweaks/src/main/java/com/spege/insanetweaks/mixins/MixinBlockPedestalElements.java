package com.spege.insanetweaks.mixins;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import com.spege.insanetweaks.util.NativeElements;

import electroblob.wizardry.block.BlockPedestal;
import electroblob.wizardry.constants.Element;

/**
 * Same reasoning as {@code MixinBlockCrystalElements}, for imbuement pedestals.
 *
 * <p>The {@code <clinit>} redirect is <b>load-bearing, not cosmetic</b>. {@code getMetaFromState}
 * packs {@code element.ordinal() + (natural ? ELEMENT.getAllowedValues().size() : 0)} into four bits
 * and wastes index 0 by using {@code ordinal()} rather than {@code ordinal() - 1}, so eight allowed
 * elements produce a maximum metadata of 16 and Forge's registry throws
 * {@code ArrayIndexOutOfBoundsException} during block registration. Narrowing restores the property
 * to exactly its pre-Abomination content, and the decoder stays symmetric because it reads the same
 * {@code getAllowedValues().size()}.
 *
 * <p>{@code getStateFromMeta} stays untouched: it indexes the full {@code Element.values()} array
 * with the raw ordinal, matching the encoder.
 */
@Mixin(value = BlockPedestal.class, remap = false)
public abstract class MixinBlockPedestalElements {

    @Redirect(method = { "func_149666_a", "getSubBlocks" },
            at = @At(value = "INVOKE",
                     target = "Lelectroblob/wizardry/constants/Element;values()[Lelectroblob/wizardry/constants/Element;"))
    private Element[] insanetweaks$narrowPedestalSubtypes() {
        return NativeElements.values();
    }

    @Redirect(method = "<clinit>",
            at = @At(value = "INVOKE",
                     target = "Lelectroblob/wizardry/constants/Element;values()[Lelectroblob/wizardry/constants/Element;"))
    private static Element[] insanetweaks$narrowPedestalProperty() {
        return NativeElements.values();
    }
}
