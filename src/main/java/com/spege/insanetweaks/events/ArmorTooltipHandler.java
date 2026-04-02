package com.spege.insanetweaks.events;

import java.util.ArrayList;
import java.util.List;

import com.spege.insanetweaks.config.ModConfig;
import com.spege.insanetweaks.items.armor.BattleMageArmorItem;
import com.spege.insanetweaks.items.armor.ParasiteWizardArmorItem;
import com.spege.insanetweaks.util.PropertyDescriptions;

import net.minecraft.client.gui.GuiScreen;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.text.TextFormatting;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

@SideOnly(Side.CLIENT)
public class ArmorTooltipHandler {

    @SubscribeEvent
    public void onArmorTooltip(ItemTooltipEvent event) {
        if (!ModConfig.modules.enableSrpEbWizardryBridge)
            return;

        ItemStack stack = event.getItemStack();
        if (stack.isEmpty())
            return;

        Item item = stack.getItem();
        if (item instanceof ParasiteWizardArmorItem || item instanceof BattleMageArmorItem) {
            List<String> tooltip = event.getToolTip();
            boolean isShiftPressed = GuiScreen.isShiftKeyDown();
            
            // Find the best insertion index (before QualityTools and Vanilla attributes)
            int insertIdx = tooltip.size();
            for (int i = 1; i < tooltip.size(); i++) {
                String cleanLine = net.minecraft.util.text.TextFormatting.getTextWithoutFormattingCodes(tooltip.get(i));
                if (cleanLine == null) continue;
                String lower = cleanLine.toLowerCase();
                
                // Look for QualityTools or Vanilla modifiers (When on body, etc.)
                if (lower.startsWith("quality:") || lower.startsWith("when in ") || lower.startsWith("when on ") || lower.startsWith("attribute modifiers")) {
                    insertIdx = i;
                    // Capture any empty line before the Quality text
                    if (i > 1 && tooltip.get(i - 1).trim().isEmpty()) {
                        insertIdx = i - 1;
                    }
                    break;
                }
            }
            
            // Fallback to TooltipUtils if nothing was found
            if (insertIdx == tooltip.size()) {
                insertIdx = com.spege.insanetweaks.util.TooltipUtils.getInsertIdx(tooltip);
            }
            
            List<String> myLines = new ArrayList<>();

            if (item instanceof ParasiteWizardArmorItem) {
                float blocked = ParasiteWizardArmorItem.getBlockedDamage(stack);
                int reduction = ParasiteWizardArmorItem.getSpellReductionPercent(stack);

                addDescriptionLine(myLines, TextFormatting.GRAY, "living_armor_lore");
                myLines.add("");
                addArmorPropertyLines(myLines, isShiftPressed, false, false);
                myLines.add("");
                myLines.add(TextFormatting.GRAY + "Per piece bonus for all spells:");
                myLines.add(TextFormatting.BLUE + "  -" + reduction + "% Mana Cost");
                myLines.add(TextFormatting.BLUE + "  -" + reduction + "% Cooldown");
                myLines.add(TextFormatting.BLUE + "  -" + reduction + "% Charge-up");
                myLines.add(TextFormatting.DARK_GREEN + "Adaptive milestones:");
                myLines.add(TextFormatting.GREEN + "  0 / 500 / 1000 DMG -> -1% / -2% / -3%");
                myLines.add(TextFormatting.DARK_GRAY + "  " + PropertyDescriptions.getDescription("living_armor_adaptation"));
                myLines.add(TextFormatting.DARK_GRAY + "  Evolves at 1500 absorbed damage.");

                if (blocked >= 10000.0f) {
                    String guardian = TextFormatting.AQUA + "G " + TextFormatting.DARK_AQUA + "U " +
                            TextFormatting.BLUE + "A " + TextFormatting.DARK_BLUE + "R " +
                            TextFormatting.DARK_GREEN + "D " + TextFormatting.GREEN + "I " +
                            TextFormatting.YELLOW + "A " + TextFormatting.GOLD + "N";
                    tooltip.add(1, "\u00a79---> " + guardian);
                } else {
                    String blockedStr = String.format("%.1f", blocked);
                    tooltip.add(1, "\u00a79---> \u00a79Absorbed: \u00a7e" + blockedStr + " / "
                            + String.format("%.1f", ParasiteWizardArmorItem.EVOLUTION_THRESHOLD));
                }
            } else {
                net.minecraft.nbt.NBTTagCompound nbt = stack.getTagCompound();
                float absorbed = (nbt != null) ? nbt.getFloat("ArmorDamageBlocked") : 0.0f;
                boolean awakenedShell = absorbed >= 10000.0f;

                addDescriptionLine(myLines, TextFormatting.GRAY, "sentient_armor_lore");
                myLines.add("");
                addArmorPropertyLines(myLines, isShiftPressed, true, awakenedShell);
                myLines.add("");
                myLines.add(TextFormatting.GRAY + "Per piece bonus for all spells:");
                myLines.add(TextFormatting.BLUE + "  -3% Mana Cost");
                myLines.add(TextFormatting.BLUE + "  -3% Cooldown");
                myLines.add(TextFormatting.BLUE + "  -3% Charge-up");

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

    private static void addArmorPropertyLines(List<String> tooltip, boolean isShiftPressed, boolean sentient,
            boolean awakenedShell) {
        String shiftHint = isShiftPressed
                ? TextFormatting.DARK_GRAY + "[Showing details]"
                : TextFormatting.DARK_GRAY + "[Press " + TextFormatting.AQUA + "SHIFT"
                        + TextFormatting.DARK_GRAY + " to show details]";
        tooltip.add(TextFormatting.GOLD + "Properties: " + shiftHint);
        addPropertyLine(tooltip, TextFormatting.GOLD, "Ashen Legacy", "ashen_legacy", isShiftPressed);
        addPropertyLine(tooltip, TextFormatting.GOLD, "Grave Defiance", "armor_last_stand", isShiftPressed);

        if (sentient) {
            String shellName = awakenedShell ? "Awakened Veil of Stasis" : "Veil of Stasis";
            String shellKey = awakenedShell ? "ethereal_shell_awakened" : "ethereal_shell";
            TextFormatting shellColor = awakenedShell ? TextFormatting.LIGHT_PURPLE : TextFormatting.DARK_GRAY;
            addPropertyLine(tooltip, shellColor, shellName, shellKey, isShiftPressed);
        }
    }

    private static void addDescriptionLine(List<String> tooltip, TextFormatting color, String key) {
        String desc = PropertyDescriptions.getDescription(key);
        if (desc != null) {
            tooltip.add(color + desc);
        }
    }

    private static void addPropertyLine(List<String> tooltip, TextFormatting color, String name, String key,
            boolean isShiftPressed) {
        tooltip.add(color + "- " + name);

        if (isShiftPressed) {
            String desc = PropertyDescriptions.getDescription(key);
            if (desc != null) {
                tooltip.add(TextFormatting.GRAY + "" + TextFormatting.ITALIC + "  " + desc);
            }
        }
    }
}
