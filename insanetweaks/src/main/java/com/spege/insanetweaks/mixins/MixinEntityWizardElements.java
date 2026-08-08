package com.spege.insanetweaks.mixins;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import com.spege.insanetweaks.util.NativeElements;

import electroblob.wizardry.constants.Element;
import electroblob.wizardry.entity.living.EntityWizard;

/**
 * Keeps Abomination out of everything a naturally spawned wizard rolls.
 *
 * <p>Without this, {@code onInitialSpawn} can pick Abomination and then ask
 * {@code WizardryItems.getWand} for {@code <tier>_abomination_wand} and
 * {@code ItemWizardArmour.getArmour} for {@code <class>_abomination_<piece>} - registry names this
 * mod deliberately does not provide, because the element is not meant to generate in the world yet.
 *
 * <p>{@code getRandomItemOfTier} is the wizard's trade stock and contains <b>eight</b>
 * {@code values()} calls forming four random-pick expressions - three feeding {@code getWand} and
 * the fourth {@code getArmour}. A {@code @Redirect} with no {@code ordinal} binds to all eight,
 * which is what keeps each expression self-consistent: the array and its {@code .length} must come
 * from the same source.
 */
@Mixin(value = EntityWizard.class, remap = false)
public abstract class MixinEntityWizardElements {

    @Redirect(method = { "func_180482_a", "onInitialSpawn" },
            at = @At(value = "INVOKE",
                     target = "Lelectroblob/wizardry/constants/Element;values()[Lelectroblob/wizardry/constants/Element;"))
    private Element[] insanetweaks$narrowSpawnElements() {
        return NativeElements.values();
    }

    @Redirect(method = "getRandomItemOfTier",
            at = @At(value = "INVOKE",
                     target = "Lelectroblob/wizardry/constants/Element;values()[Lelectroblob/wizardry/constants/Element;"))
    private Element[] insanetweaks$narrowTradeItemElements() {
        return NativeElements.values();
    }

    @Redirect(method = "populateSpells",
            at = @At(value = "INVOKE",
                     target = "Lelectroblob/wizardry/constants/Element;values()[Lelectroblob/wizardry/constants/Element;"))
    private static Element[] insanetweaks$narrowPopulatedSpellElements() {
        return NativeElements.values();
    }
}
