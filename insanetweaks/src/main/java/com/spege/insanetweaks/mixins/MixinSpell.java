package com.spege.insanetweaks.mixins;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import electroblob.wizardry.spell.Spell;
import electroblob.wizardry.util.SpellProperties;

@Mixin(value = Spell.class, remap = false)
@SuppressWarnings("null")
public abstract class MixinSpell {

    /** Shadow of the private {@code properties} field in {@link Spell}. */
    @Shadow
    private SpellProperties properties;

    /**
     * Null-safe guard for {@code Spell.isEnabled()}.
     *
     * EBWizardry 4.x calls {@code isEnabled()} inside {@code EntityWizard.populateSpells()} via
     * {@code Spell.getSpells(filter)}.  For custom spells registered by external mods,
     * {@code SpellProperties.load()} may not have run yet at that point, leaving
     * {@code this.properties == null} → NPE crash on the server thread.
     *
     * Fix: cancel early and return {@code true} (spell considered enabled) when properties
     * are not yet loaded.  The JSON {@code "npcs": false} entry will still prevent wizards
     * from actually casting this spell once properties ARE loaded on the next tick.
     */
    @Inject(method = "isEnabled", at = @At("HEAD"), cancellable = true)
    private void insanetweaks$nullSafeIsEnabled(SpellProperties.Context[] contexts,
                                                CallbackInfoReturnable<Boolean> cir) {
        if (this.properties == null) {
            cir.setReturnValue(true);
        }
    }
}
