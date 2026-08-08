package com.spege.insanetweaks.mixins;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import com.spege.insanetweaks.util.NativeElements;

import electroblob.wizardry.constants.Element;
import electroblob.wizardry.integration.jei.ArcaneWorkbenchRecipe;

/**
 * Keeps Abomination out of the Arcane Workbench's JEI charging-recipe inputs, for the same reason as
 * {@code MixinJeiImbuementAltarElements}.
 */
@Mixin(value = ArcaneWorkbenchRecipe.class, remap = false)
public abstract class MixinJeiArcaneWorkbenchElements {

    @Redirect(method = "generateCrystalStacks",
            at = @At(value = "INVOKE",
                     target = "Lelectroblob/wizardry/constants/Element;values()[Lelectroblob/wizardry/constants/Element;"))
    private static Element[] insanetweaks$narrowJeiChargingCrystals() {
        return NativeElements.values();
    }
}
