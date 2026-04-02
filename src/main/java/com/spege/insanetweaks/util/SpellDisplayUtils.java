package com.spege.insanetweaks.util;

import com.spege.insanetweaks.InsaneTweaksMod;

import electroblob.wizardry.constants.Element;
import electroblob.wizardry.spell.Spell;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.util.text.translation.I18n;

@SuppressWarnings("deprecation")
public final class SpellDisplayUtils {

    private static final String ABOMINATION_KEY = "insanetweaks.element.abomination";
    private static final String ABOMINATION_FALLBACK = "Abomination";

    private SpellDisplayUtils() {}

    public static boolean usesAbominationStyling(Spell spell) {
        if (spell == null || spell.getElement() != Element.MAGIC) {
            return false;
        }

        ResourceLocation registryName = spell.getRegistryName();
        return registryName != null && InsaneTweaksMod.MODID.equals(registryName.getResourceDomain());
    }

    public static String getAbominationFormattingCode() {
        return TextFormatting.RED.toString();
    }

    public static String getFormattedSpellDisplayName(Spell spell) {
        if (spell == null) {
            return "";
        }
        return usesAbominationStyling(spell)
                ? getAbominationFormattingCode() + spell.getDisplayName()
                : spell.getDisplayName();
    }

    public static String getElementDisplayName(Spell spell) {
        if (!usesAbominationStyling(spell)) {
            return spell != null ? spell.getElement().getDisplayName() : ABOMINATION_FALLBACK;
        }

        String translated = I18n.translateToLocal(ABOMINATION_KEY);
        return ABOMINATION_KEY.equals(translated) ? ABOMINATION_FALLBACK : translated;
    }

    public static String getFormattedElementDisplayName(Spell spell) {
        return usesAbominationStyling(spell)
                ? getAbominationFormattingCode() + getElementDisplayName(spell)
                : getElementDisplayName(spell);
    }
}
