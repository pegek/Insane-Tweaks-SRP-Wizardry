package com.spege.insanetweaks.mixins;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.spege.insanetweaks.util.SpellDisplayUtils;

import electroblob.wizardry.client.gui.GuiSpellDisplay;
import electroblob.wizardry.data.SpellGlyphData;
import electroblob.wizardry.data.WizardData;
import electroblob.wizardry.registry.WizardryPotions;
import electroblob.wizardry.spell.Spell;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.text.TextFormatting;

@Mixin(value = GuiSpellDisplay.class, remap = false)
public abstract class MixinGuiSpellDisplay {

    @Inject(method = "getFormattedSpellName", at = @At("HEAD"), cancellable = true)
    private static void insanetweaks$formatOwnSpellNamesInHud(Spell spell, EntityPlayer player, int switchTime,
            CallbackInfoReturnable<String> cir) {

        if (!SpellDisplayUtils.usesAbominationStyling(spell)) {
            return;
        }

        boolean discovered = true;
        if (!player.isCreative() && WizardData.get(player) != null) {
            discovered = WizardData.get(player).hasSpellBeenDiscovered(spell);
        }

        String formatting = switchTime > 0
                ? TextFormatting.DARK_GRAY.toString()
                : SpellDisplayUtils.getAbominationFormattingCode();

        if (!discovered) {
            formatting = TextFormatting.BLUE.toString();
        }

        if (player.isPotionActive(WizardryPotions.arcane_jammer)) {
            formatting += TextFormatting.OBFUSCATED.toString();
        }

        String name = discovered ? spell.getDisplayName() : SpellGlyphData.getGlyphName(spell, player.world);
        String formattedName = formatting + name;

        if (!discovered) {
            formattedName = "#" + formattedName + "#";
        }

        cir.setReturnValue(formattedName);
    }
}
