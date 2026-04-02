package com.spege.insanetweaks.events;

import java.util.ArrayList;
import java.util.List;

import com.spege.insanetweaks.init.ModItems;
import com.spege.insanetweaks.items.wand.BaseCustomWandItem;
import com.spege.insanetweaks.util.PropertyDescriptions;
import com.spege.insanetweaks.util.TooltipUtils;

import net.minecraft.client.gui.GuiScreen;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.text.TextFormatting;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

@SideOnly(Side.CLIENT)
public class WandTooltipHandler {

    @SubscribeEvent
    public void onTooltip(ItemTooltipEvent event) {
        ItemStack stack = event.getItemStack();
        if (stack.isEmpty()) return;

        Item item = stack.getItem();
        boolean isLiving = item == ModItems.LIVING_WAND;
        boolean isSentient = item == ModItems.SENTIENT_WAND;
        if (!isLiving && !isSentient) return;

        List<String> tooltip = event.getToolTip();
        if (tooltip.isEmpty()) return;

        NBTTagCompound wandNbt = stack.getTagCompound();
        int evoPoints = (wandNbt != null) ? wandNbt.getInteger("WandEvolutionPoints") : 0;
        String suffix = getEvolutionSuffix(isLiving, isSentient, evoPoints);
        tooltip.add(1, "\u00a79---> \u00a79" + evoPoints + suffix);

        for (int i = tooltip.size() - 1; i >= 1; i--) {
            String line = tooltip.get(i).toLowerCase();
            if (line.contains("current spell")
                    || line.contains("mana:")
                    || line.contains("omnipotency")
                    || line.contains("progression")
                    || line.contains("%.")
                    || line.contains("item.ebwizardry")) {
                tooltip.remove(i);
            }
        }

        int insertIdx = tooltip.size();
        for (int i = 1; i < tooltip.size(); i++) {
            String cleanLine = TextFormatting.getTextWithoutFormattingCodes(tooltip.get(i));
            if (cleanLine == null) continue;

            String lower = cleanLine.toLowerCase();
            if (lower.startsWith("quality:") || lower.startsWith("when in ") || lower.startsWith("attribute modifiers")) {
                insertIdx = i;
                if (i > 1 && tooltip.get(i - 1).trim().isEmpty()) {
                    insertIdx = i - 1;
                }
                break;
            }
        }

        if (insertIdx == tooltip.size()) {
            insertIdx = TooltipUtils.getInsertIdx(tooltip);
        }

        if (!(item instanceof BaseCustomWandItem)) return;
        BaseCustomWandItem customWand = (BaseCustomWandItem) item;
        boolean isShiftPressed = GuiScreen.isShiftKeyDown();

        List<String> myLines = new ArrayList<String>();
        myLines.add("");
        if (isSentient) {
            myLines.add(TextFormatting.WHITE
                    + "A symbiotic catalyst. It doesn't just breathe-it observes, learns, and "
                    + TextFormatting.LIGHT_PURPLE + "assimilates" + TextFormatting.WHITE + ".");
        } else {
            myLines.add(TextFormatting.WHITE
                    + "The grip is disturbingly warm, woven with pulsating veins. It "
                    + TextFormatting.RED + "feeds" + TextFormatting.WHITE + " on your mana.");
        }
        myLines.add("");

        int currentMana = customWand.getMana(stack);
        int maxMana = customWand.getManaCapacity(stack);
        int potencyPercent = (int) (customWand.getPotencyBonus() * 100);

        myLines.add(TextFormatting.BLUE + "Mana: " + currentMana + " / " + maxMana);
        myLines.add("");

        String shiftHint = isShiftPressed
                ? TextFormatting.DARK_GRAY + "[Showing details]"
                : TextFormatting.DARK_GRAY + "[Press " + TextFormatting.AQUA + "SHIFT"
                        + TextFormatting.DARK_GRAY + " to show details]";
        myLines.add(TextFormatting.GOLD + "Properties: " + shiftHint);

        int adaptationLevel = customWand.getArcaneAdaptationLevel();
        if (adaptationLevel > 0) {
            myLines.add(TextFormatting.DARK_RED + "- Arcane Adaptation " + toRoman(adaptationLevel)
                    + TextFormatting.RED + " (x" + customWand.getArcaneAdaptationManaMultiplier()
                    + " Mana Cost)");
            if (isShiftPressed) {
                String desc = PropertyDescriptions.getDescription("arcane_adaptation");
                if (desc != null) {
                    myLines.add(TextFormatting.RED + "" + TextFormatting.ITALIC + "  " + desc);
                }
            }
        }
        myLines.add(TextFormatting.GOLD + "- Ashen Legacy");
        if (isShiftPressed) {
            String desc = PropertyDescriptions.getDescription("ashen_legacy");
            if (desc != null) {
                myLines.add(TextFormatting.GRAY + "" + TextFormatting.ITALIC + "  " + desc);
            }
        }
        if (net.minecraftforge.fml.common.Loader.isModLoaded("potioncore")) {
            int magicDamageBonus = customWand.getMagicDamageBonusPercent(stack);
            myLines.add(TextFormatting.GREEN + "- Magically Adapted "
                    + TextFormatting.DARK_AQUA + "(+" + magicDamageBonus + "% Magic Damage)");
            if (isShiftPressed) {
                String desc = PropertyDescriptions.getDescription("magically_adapted");
                if (desc != null) {
                    myLines.add(TextFormatting.GRAY + "" + TextFormatting.ITALIC + "  " + desc);
                }
            }
        }

        myLines.add(TextFormatting.RED + "+" + potencyPercent + "% " + TextFormatting.RED + "Omnipotency");

        float progress = Math.min(1.0f, Math.max(0.0f, evoPoints / 5000.0f));
        int durationBonus = isLiving ? (int)(5 + 20 * progress) : 25;
        int minionHealthBonus = isLiving ? (int)(5 + 20 * progress) : 25;
        int standardReduction = isLiving ? (int)(5 + 15 * progress) : 20;

        if (isSentient && evoPoints >= 10000) {
            myLines.add(TextFormatting.DARK_GRAY + "+1 " + TextFormatting.YELLOW + "Minion Count " + TextFormatting.LIGHT_PURPLE + "(Awakened)");
        }

        myLines.add(TextFormatting.DARK_GRAY + "+" + minionHealthBonus + "% " + TextFormatting.DARK_GREEN + "Minion Health");
        myLines.add(TextFormatting.DARK_GRAY + "+" + durationBonus + "% " + TextFormatting.GREEN + "Summon Duration");
        myLines.add(TextFormatting.DARK_GRAY + "-" + standardReduction + "% " + TextFormatting.BLUE + "Mana Cost");
        myLines.add(TextFormatting.DARK_GRAY + "-" + standardReduction + "% " + TextFormatting.LIGHT_PURPLE + "Charge-up Time");
        myLines.add(TextFormatting.DARK_GRAY + "-" + standardReduction + "% " + TextFormatting.DARK_PURPLE + "Cooldown");

        tooltip.addAll(insertIdx, myLines);
    }

    private static String toRoman(int value) {
        switch (value) {
            case 1:
                return "I";
            case 2:
                return "II";
            case 3:
                return "III";
            default:
                return String.valueOf(value);
        }
    }

    private static String getEvolutionSuffix(boolean isLiving, boolean isSentient, int evoPoints) {
        if (isLiving) {
            return " / 5000 harvested";
        }

        if (isSentient && evoPoints >= 10000) {
            return " " + getCondemnedLabel();
        }

        return " learned";
    }

    private static String getCondemnedLabel() {
        return TextFormatting.DARK_RED + "C "
                + TextFormatting.RED + "O "
                + TextFormatting.GOLD + "N "
                + TextFormatting.YELLOW + "D "
                + TextFormatting.DARK_GREEN + "E "
                + TextFormatting.GREEN + "M "
                + TextFormatting.DARK_AQUA + "E "
                + TextFormatting.AQUA + "D";
    }
}
