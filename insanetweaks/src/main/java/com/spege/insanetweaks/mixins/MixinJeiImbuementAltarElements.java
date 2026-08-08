package com.spege.insanetweaks.mixins;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import com.spege.insanetweaks.util.NativeElements;

import electroblob.wizardry.constants.Element;
import electroblob.wizardry.integration.jei.ImbuementAltarRecipeCategory;

/**
 * Keeps the Abomination crystal <em>block</em> out of the Imbuement Altar's JEI recipe list.
 *
 * <p>Only the block. The crystal <em>item</em> recipe is now genuine - the item has a model and the
 * altar really does produce it - and the armour recipes need no help: EBW's own
 * {@code if (output.isEmpty()) continue;} drops them, because {@code getArmour} misses for
 * Abomination and yields an empty stack. That self-filtering is deliberate load-bearing behaviour:
 * the day {@code living_warlock_armour} is registered under the {@code ebwizardry} namespace, the
 * armour row appears by itself.
 *
 * <p>The block is different because {@code BlockCrystal} renders from a single blockstate file
 * listing every variant, which we cannot extend without replacing EBW's copy - so a ninth block
 * variant would show in JEI with no model behind it.
 */
@Mixin(value = ImbuementAltarRecipeCategory.class, remap = false)
public abstract class MixinJeiImbuementAltarElements {

    @Redirect(method = "generateCrystalBlockRecipes",
            at = @At(value = "INVOKE",
                     target = "Lelectroblob/wizardry/constants/Element;values()[Lelectroblob/wizardry/constants/Element;"))
    private static Element[] insanetweaks$narrowJeiCrystalBlockRecipes() {
        return NativeElements.values();
    }
}
