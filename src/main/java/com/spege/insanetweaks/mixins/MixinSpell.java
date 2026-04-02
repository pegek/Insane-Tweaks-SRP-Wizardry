package com.spege.insanetweaks.mixins;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.spege.insanetweaks.util.SpellDisplayUtils;

import electroblob.wizardry.spell.Spell;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.Style;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextFormatting;

@Mixin(value = Spell.class, remap = false)
public abstract class MixinSpell {

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
