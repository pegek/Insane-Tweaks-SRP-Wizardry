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
                int adapt = (nbt != null) ? nbt.getInteger("adaptation_points") : 0;
                int reduction = 1 + (adapt / 10);

                myLines.add(TextFormatting.GRAY + "Per piece bonus for all spells:");
                myLines.add(TextFormatting.BLUE + "  -" + reduction + "% Mana Cost");
                myLines.add(TextFormatting.BLUE + "  -" + reduction + "% Cooldown");
                myLines.add(TextFormatting.BLUE + "  -" + reduction + "% Charge-up");

                myLines.add("\u00a75A living, breathing arcane carapace.");
                myLines.add(
                        "\u00a76Full set bonus: \u00a77Occasionally caps incoming damage. Dispels ailments on trigger.");
                myLines.add(
                        "\u00a78Evolution Process: \u00a77Adapts when taking heavy damage (>4 DMG), granting stronger stats.");

                if (adapt > 0) {
                    myLines.add("\u00a78Evolution Progress: \u00a7e" + adapt + "% / 100%");
                }
            } else {
                myLines.add(TextFormatting.GRAY + "Per piece bonus for all spells:");
                myLines.add(TextFormatting.BLUE + "  -10% Mana Cost");
                myLines.add(TextFormatting.BLUE + "  -10% Cooldown");
                myLines.add(TextFormatting.BLUE + "  -10% Charge-up");

                myLines.add("\u00a7eThe fully Evolved arcane carapace.");
                myLines.add("\u00a76Passive: \u00a77Caps incoming max damage per hit. Dispels ailments on trigger.");
                myLines.add("\u00a78Ethereal Shell: \u00a77Provides a flat -1.5% damage resistance from all sources.");
            }

            tooltip.addAll(insertIdx, myLines);
        }
    }
}
