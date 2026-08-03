package com.spege.insanetweaks.events;

import java.util.List;

import com.spege.insanetweaks.util.SpellDisplayUtils;

import electroblob.wizardry.item.ItemScroll;
import electroblob.wizardry.item.ItemSpellBook;
import electroblob.wizardry.spell.Spell;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.text.TextFormatting;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/**
 * Re-labels the element line on Abomination-styled scroll and spell-book tooltips.
 *
 * <p>Purely presentational, hence {@code @SideOnly(Side.CLIENT)} — which brings this class in line
 * with the eight other tooltip handlers in this package. The annotation is safe here only because
 * the single registration site in {@code InsaneTweaksMod.init()} already sits inside an
 * {@code event.getSide() == Side.CLIENT} block: a class-level {@code @SideOnly} makes
 * {@code new SpellItemTooltipHandler()} fatal on a dedicated server, so if that registration ever
 * moves out of the guard, this annotation has to go with it.
 */
@SideOnly(Side.CLIENT)
public class SpellItemTooltipHandler {

    @SubscribeEvent
    public void onItemTooltip(ItemTooltipEvent event) {
        ItemStack stack = event.getItemStack();
        if (stack.isEmpty() || stack.getMetadata() == Short.MAX_VALUE) {
            return;
        }

        Item item = stack.getItem();
        if (!(item instanceof ItemScroll) && !(item instanceof ItemSpellBook)) {
            return;
        }

        Spell spell = Spell.byMetadata(stack.getMetadata());
        if (!SpellDisplayUtils.usesAbominationStyling(spell)) {
            return;
        }

        String vanillaElementName = spell.getElement().getDisplayName();
        String replacementElementName = SpellDisplayUtils.getElementDisplayName(spell);
        List<String> tooltip = event.getToolTip();

        for (int i = 0; i < tooltip.size(); i++) {
            String line = tooltip.get(i);
            String stripped = TextFormatting.getTextWithoutFormattingCodes(line);
            if (stripped == null) {
                stripped = line;
            }

            if (vanillaElementName.equals(stripped)) {
                tooltip.set(i, replacementElementName);
                return;
            }

            String suffix = ": " + vanillaElementName;
            if (stripped.endsWith(suffix)) {
                tooltip.set(i, line.substring(0, line.length() - suffix.length()) + ": " + replacementElementName);
                return;
            }
        }
    }
}
