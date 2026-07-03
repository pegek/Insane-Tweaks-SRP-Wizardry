package com.spege.insanetweaks.events;

import com.spege.insanetweaks.config.ModConfig;
import com.spege.insanetweaks.items.armor.LivingBattlemageArmorItem;
import com.spege.insanetweaks.items.armor.SentientBattlemageArmorItem;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.text.TextFormatting;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import java.util.ArrayList;
import java.util.List;

@SideOnly(Side.CLIENT)
public class BattlemageTooltipHandler {

    @SubscribeEvent
    public void onArmorTooltip(ItemTooltipEvent event) {
        if (!ModConfig.modules.enableSrpEbWizardryBridge) return;

        ItemStack stack = event.getItemStack();
        if (stack.isEmpty()) return;

        Item item = stack.getItem();
        if (item instanceof LivingBattlemageArmorItem || item instanceof SentientBattlemageArmorItem) {
            List<String> tooltip = event.getToolTip();
            boolean isShiftPressed = GuiScreen.isShiftKeyDown();

            // Find insertion point
            int insertIdx = tooltip.size();
            for (int i = 1; i < tooltip.size(); i++) {
                String cleanLine = TextFormatting.getTextWithoutFormattingCodes(tooltip.get(i));
                if (cleanLine == null) continue;
                String lower = cleanLine.toLowerCase();
                
                if (lower.startsWith("quality:") || lower.startsWith("when in ") || lower.startsWith("when on ") || lower.startsWith("attribute modifiers")) {
                    insertIdx = i;
                    if (i > 1 && tooltip.get(i - 1).trim().isEmpty()) {
                        insertIdx = i - 1;
                    }
                    break;
                }
            }
            if (insertIdx == tooltip.size()) {
                insertIdx = com.spege.insanetweaks.util.TooltipUtils.getInsertIdx(tooltip);
            }

            List<String> myLines = new ArrayList<>();
            // myLines.add(TextFormatting.GRAY + "" + TextFormatting.ITALIC + "A heavy armor shaped from parasitic bio-mass,");
            // myLines.add(TextFormatting.GRAY + "" + TextFormatting.ITALIC + "able to learn and resist damage types.");
            // myLines.add("");

            boolean isLiving = item instanceof LivingBattlemageArmorItem;
            NBTTagCompound nbt = stack.getTagCompound();
            float absorbed = nbt != null ? nbt.getFloat("itHitsAbsorbed") : 0f;

            int maxTypes = isLiving ? LivingBattlemageArmorItem.MAX_DAMAGE_TYPES : SentientBattlemageArmorItem.MAX_DAMAGE_TYPES;
            if (!isLiving && absorbed >= 10000.0f) {
                maxTypes += 1;
            }

            int maxPoints = isLiving ? LivingBattlemageArmorItem.MAX_POINTS_PER_TYPE : SentientBattlemageArmorItem.MAX_POINTS_PER_TYPE;
            float reductionPerPoint = isLiving ? LivingBattlemageArmorItem.POINT_REDUCTION : SentientBattlemageArmorItem.POINT_REDUCTION;
            int spellBonus = isLiving ? 1 : 2;

            myLines.add(TextFormatting.GOLD + "Bonuses & Resistances:");
            myLines.add(TextFormatting.BLUE + "  -" + spellBonus + "% Mana Cost per piece");
            myLines.add(TextFormatting.BLUE + "  -" + spellBonus + "% Cooldown per piece");
            myLines.add(TextFormatting.BLUE + "  -" + spellBonus + "% Charge-up per piece");
            
            
            myLines.add("");
            myLines.add(TextFormatting.DARK_GREEN + "Adaptation (" + maxTypes + " types max):");

            if (nbt != null && nbt.hasKey("itResistNames", 9)) {
                NBTTagList names = nbt.getTagList("itResistNames", 8);
                int[] points = nbt.getIntArray("itResistPoints");

                for (int i = 0; i < names.tagCount(); i++) {
                    String dmgType = names.getStringTagAt(i);
                    int pts = points.length > i ? points[i] : 0;
                    int effPts = Math.min(pts, maxPoints);
                    float resistPct = effPts * reductionPerPoint * 100f; // in percent
                    
                    String color = effPts >= maxPoints ? TextFormatting.GREEN.toString() : TextFormatting.DARK_AQUA.toString();
                    myLines.add(color + "  → " + dmgType + ": " + String.format("%.1f", resistPct) + "% (" + pts + " pts)");
                }
            } else {
                myLines.add(TextFormatting.DARK_GRAY + "  (No adaptations yet)");
            }

            tooltip.addAll(insertIdx, myLines);

            if (isLiving) {
                tooltip.add(1, "\u00a79Absorbed: \u00a7e" + String.format("%.0f", absorbed) + " / " + String.format("%.0f", LivingBattlemageArmorItem.EVOLUTION_THRESHOLD));
            } else {
                if (absorbed >= 10000.0f) {
                    String watchdog = TextFormatting.DARK_RED + "W " + TextFormatting.RED + "A " +
                            TextFormatting.GOLD + "T " + TextFormatting.YELLOW + "C " +
                            TextFormatting.GREEN + "H " + TextFormatting.DARK_GREEN + "D " +
                            TextFormatting.AQUA + "O " + TextFormatting.DARK_AQUA + "G";
                    tooltip.add(1, "\u00a79---> " + watchdog);
                } else {
                    tooltip.add(1, "\u00a79Absorbed: \u00a7e" + String.format("%.0f", absorbed));
                }
            }
        }
    }
}
