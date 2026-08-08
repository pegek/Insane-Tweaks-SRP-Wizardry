package com.spege.insanetweaks.mixins;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import com.spege.insanetweaks.util.NativeElements;

import electroblob.wizardry.constants.Element;
import electroblob.wizardry.item.ItemCrystal;

/**
 * Stops a ninth magic crystal subtype appearing in creative and JEI.
 *
 * <p>{@code getSubItems} emits one stack per element with {@code meta = element.ordinal()}, and the
 * model name is built in the {@code ebwizardry} namespace, which this mod does not ship into.
 * {@code getModelName} is deliberately left alone: it is a metadata lookup, not a random pick.
 */
@Mixin(value = ItemCrystal.class, remap = false)
public abstract class MixinItemCrystalElements {

    @Redirect(method = { "func_150895_a", "getSubItems" },
            at = @At(value = "INVOKE",
                     target = "Lelectroblob/wizardry/constants/Element;values()[Lelectroblob/wizardry/constants/Element;"))
    private Element[] insanetweaks$narrowCrystalSubtypes() {
        return NativeElements.values();
    }
}
