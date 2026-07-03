package com.spege.insanetweaks.mixins;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.spege.insanetweaks.util.SpellDisplayUtils;

import electroblob.wizardry.spell.Spell;
import electroblob.wizardry.util.SpellProperties;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.Style;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextFormatting;

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

    @Inject(method = "getDisplayNameWithFormatting", at = @At("HEAD"), cancellable = true)
    private void insanetweaks$makeOwnMagicSpellsAbominationColored(CallbackInfoReturnable<String> cir) {
        Spell self = (Spell) (Object) this;
        if (!SpellDisplayUtils.usesAbominationStyling(self)) {
            return;
        }

        cir.setReturnValue(SpellDisplayUtils.getFormattedSpellDisplayName(self));
    }

    @Inject(method = "getNameForTranslationFormatted", at = @At("HEAD"), cancellable = true)
    private void insanetweaks$makeOwnMagicSpellComponentAbominationColored(CallbackInfoReturnable<ITextComponent> cir) {
        Spell self = (Spell) (Object) this;
        if (!SpellDisplayUtils.usesAbominationStyling(self)) {
            return;
        }

        TextComponentString text = new TextComponentString(self.getDisplayName());
        text.setStyle(new Style().setColor(TextFormatting.RED));
        cir.setReturnValue(text);
    }
}
