package com.spege.insanetweaks.mixins;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import com.spege.insanetweaks.util.SpellDisplayUtils;

import electroblob.wizardry.client.gui.GuiSpellInfo;
import electroblob.wizardry.constants.Element;
import electroblob.wizardry.spell.Spell;

@Mixin(value = GuiSpellInfo.class, remap = false)
public abstract class MixinGuiSpellInfo {

    @Shadow
    public abstract Spell getSpell();

    @Redirect(method = "drawForegroundLayer", at = @At(value = "INVOKE", target = "Lelectroblob/wizardry/spell/Spell;getDisplayName()Ljava/lang/String;"), require = 0)
    private String insanetweaks$formatSpellTitleInGui(Spell spell) {
        return SpellDisplayUtils.getFormattedSpellDisplayName(spell);
    }

    @Redirect(method = "drawForegroundLayer", at = @At(value = "INVOKE", target = "Lelectroblob/wizardry/constants/Element;getFormattingCode()Ljava/lang/String;"), require = 0)
    private String insanetweaks$replaceElementFormatting(Element element) {
        Spell spell = this.getSpell();
        return SpellDisplayUtils.usesAbominationStyling(spell)
                ? SpellDisplayUtils.getAbominationFormattingCode()
                : element.getFormattingCode();
    }

    @Redirect(method = "drawForegroundLayer", at = @At(value = "INVOKE", target = "Lelectroblob/wizardry/constants/Element;getDisplayName()Ljava/lang/String;"), require = 0)
    private String insanetweaks$replaceElementName(Element element) {
        Spell spell = this.getSpell();
        return SpellDisplayUtils.usesAbominationStyling(spell)
                ? SpellDisplayUtils.getElementDisplayName(spell)
                : element.getDisplayName();
    }
}
