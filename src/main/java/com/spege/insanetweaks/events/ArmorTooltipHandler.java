package com.spege.insanetweaks.events;

import java.util.ArrayList;
import java.util.List;

import com.spege.insanetweaks.config.ModConfig;
import com.spege.insanetweaks.items.armor.BattleMageArmorItem;
import com.spege.insanetweaks.items.armor.ParasiteWizardArmorItem;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.text.TextFormatting;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

public class ArmorTooltipHandler {

    @SubscribeEvent
    public void onArmorTooltip(ItemTooltipEvent event) {
        if (!ModConfig.enableSrpEbWizardryBridge)
            return;

        ItemStack stack = event.getItemStack();
        if (stack.isEmpty())
            return;

        Item item = stack.getItem();
        if (item instanceof ParasiteWizardArmorItem || item instanceof BattleMageArmorItem) {
            List<String> tooltip = event.getToolTip();
            int insertIdx = com.spege.insanetweaks.util.TooltipUtils.getInsertIdx(tooltip);
            List<String> myLines = new ArrayList<>();

            if (item instanceof ParasiteWizardArmorItem) {
                net.minecraft.nbt.NBTTagCompound nbt = stack.getTagCompound();
                float blocked = (nbt != null) ? nbt.getFloat("ArmorDamageBlocked") : 0.0f;
                int reduction = 1 + (int) (blocked / 150.0f); // Scales 1% to 11% at 1500 DMG

                myLines.add(TextFormatting.GRAY + "Per piece bonus for all spells:");
                myLines.add(TextFormatting.BLUE + "  -" + reduction + "% Mana Cost");
                myLines.add(TextFormatting.BLUE + "  -" + reduction + "% Cooldown");
                myLines.add(TextFormatting.BLUE + "  -" + reduction + "% Charge-up");

                tooltip.add(2, TextFormatting.GRAY + "A living, breathing arcane carapace, infused with "
                        + TextFormatting.DARK_GREEN + "Parasitic" + TextFormatting.GRAY + " biomass.");
                myLines.add(
                        "\u00a76Full set bonus: \u00a77Once in a while, saves you from lethal damage and cleanse negative effects.");
                myLines.add(
                        "\u00a78Evolution Process: \u00a77Adapts when absorbing damage, granting stronger stats.");

                if (blocked >= 10000.0f) {
                    String guardian = TextFormatting.AQUA + "G " + TextFormatting.DARK_AQUA + "U " +
                            TextFormatting.BLUE + "A " + TextFormatting.DARK_BLUE + "R " +
                            TextFormatting.DARK_GREEN + "D " + TextFormatting.GREEN + "I " +
                            TextFormatting.YELLOW + "A " + TextFormatting.GOLD + "N";
                    tooltip.add(1, "\u00a79---> " + guardian);
                } else {
                    String blockedStr = String.format("%.1f", blocked);
                    tooltip.add(1, "\u00a79---> \u00a79Absorbed: \u00a7e" + blockedStr + " / 1500.0");
                }
            } else {
                myLines.add(TextFormatting.GRAY + "Per piece bonus for all spells:");
                myLines.add(TextFormatting.BLUE + "  -10% Mana Cost");
                myLines.add(TextFormatting.BLUE + "  -10% Cooldown");
                myLines.add(TextFormatting.BLUE + "  -10% Charge-up");

                tooltip.add(2, TextFormatting.GRAY + "The ulterior evolution of arcane carapace. It has achieved "
                        + TextFormatting.AQUA + "Ethereal Stasis" + TextFormatting.GRAY + ".");
                myLines.add(
                        "\u00a76Full set bonus: \u00a77Once in a while, saves you from lethal damage and cleanse negative effects.");
                myLines.add("\u00a78Ethereal Shell: \u00a77Provides a flat -1.0% damage resistance from all sources.");

                net.minecraft.nbt.NBTTagCompound nbt = stack.getTagCompound();
                float absorbed = (nbt != null) ? nbt.getFloat("ArmorDamageBlocked") : 0.0f;

                if (absorbed >= 10000.0f) {
                    String guardian = TextFormatting.AQUA + "G " + TextFormatting.DARK_AQUA + "U " +
                            TextFormatting.BLUE + "A " + TextFormatting.DARK_BLUE + "R " +
                            TextFormatting.DARK_GREEN + "D " + TextFormatting.GREEN + "I " +
                            TextFormatting.YELLOW + "A " + TextFormatting.GOLD + "N";
                    tooltip.add(1, "\u00a79---> " + guardian);
                } else {
                    String absorbedStr = String.format("%.1f", absorbed);
                    tooltip.add(1, "\u00a79---> \u00a79Absorbed: \u00a7e" + absorbedStr);
                }
            }

            tooltip.addAll(insertIdx, myLines);
        }
    }
}
