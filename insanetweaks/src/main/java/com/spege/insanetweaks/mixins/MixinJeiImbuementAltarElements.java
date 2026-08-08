package com.spege.insanetweaks.mixins;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import com.spege.insanetweaks.util.NativeElements;

import electroblob.wizardry.constants.Element;
import electroblob.wizardry.integration.jei.ImbuementAltarRecipeCategory;

/**
 * Keeps Abomination out of the Imbuement Altar's JEI recipe list.
 *
 * <p>EBW's JEI integration builds its ingredient stacks straight from {@code Element.values()} and
 * never goes through {@code getSubItems}, so the redirects on the item and block classes do not
 * reach it. Without this, JEI shows a ninth crystal and crystal-block recipe pair with no model and
 * no lang key.
 */
@Mixin(value = ImbuementAltarRecipeCategory.class, remap = false)
public abstract class MixinJeiImbuementAltarElements {

    @Redirect(method = "generateCrystalRecipes",
            at = @At(value = "INVOKE",
                     target = "Lelectroblob/wizardry/constants/Element;values()[Lelectroblob/wizardry/constants/Element;"))
    private static Element[] insanetweaks$narrowJeiCrystalRecipes() {
        return NativeElements.values();
    }

    @Redirect(method = "generateCrystalBlockRecipes",
            at = @At(value = "INVOKE",
                     target = "Lelectroblob/wizardry/constants/Element;values()[Lelectroblob/wizardry/constants/Element;"))
    private static Element[] insanetweaks$narrowJeiCrystalBlockRecipes() {
        return NativeElements.values();
    }

    @Redirect(method = "generateArmourRecipes",
            at = @At(value = "INVOKE",
                     target = "Lelectroblob/wizardry/constants/Element;values()[Lelectroblob/wizardry/constants/Element;"))
    private static Element[] insanetweaks$narrowJeiArmourRecipes() {
        return NativeElements.values();
    }
}
