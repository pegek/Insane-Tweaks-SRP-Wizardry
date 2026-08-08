package com.spege.insanetweaks.mixins;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import com.spege.insanetweaks.util.NativeElements;

import electroblob.wizardry.constants.Element;
import electroblob.wizardry.entity.living.EntityRemnant;

/** Same reasoning as {@code MixinEntityWizardElements}, for remnants (added in EBW 4.3). */
@Mixin(value = EntityRemnant.class, remap = false)
public abstract class MixinEntityRemnantElements {

    @Redirect(method = { "func_180482_a", "onInitialSpawn" },
            at = @At(value = "INVOKE",
                     target = "Lelectroblob/wizardry/constants/Element;values()[Lelectroblob/wizardry/constants/Element;"))
    private Element[] insanetweaks$narrowSpawnElements() {
        return NativeElements.values();
    }
}
