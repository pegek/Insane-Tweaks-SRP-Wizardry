package com.spege.insanetweaks.mixins;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import com.spege.insanetweaks.util.NativeElements;

import electroblob.wizardry.constants.Element;
import electroblob.wizardry.entity.living.EntityEvilWizard;

/** Same reasoning as {@code MixinEntityWizardElements}, for the hostile variant. */
@Mixin(value = EntityEvilWizard.class, remap = false)
public abstract class MixinEntityEvilWizardElements {

    @Redirect(method = { "func_180482_a", "onInitialSpawn" },
            at = @At(value = "INVOKE",
                     target = "Lelectroblob/wizardry/constants/Element;values()[Lelectroblob/wizardry/constants/Element;"))
    private Element[] insanetweaks$narrowSpawnElements() {
        return NativeElements.values();
    }
}
