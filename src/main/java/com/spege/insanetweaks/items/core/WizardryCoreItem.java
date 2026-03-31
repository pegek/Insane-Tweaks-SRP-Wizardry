package com.spege.insanetweaks.items.core;

import java.util.List;

import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.item.Item;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.text.TextFormatting;

public class WizardryCoreItem extends Item {

    private final String upgradeNbtKey;
    private final float increment;
    private final TextFormatting titleColor;
    private final String upgradeLabel;
    private final String effectLabel;
    private final boolean percentageDisplay;
    private final boolean reductionDisplay;
    private final String experimentalNote;

    public WizardryCoreItem(String registryName, String upgradeNbtKey, float increment, TextFormatting titleColor,
            String upgradeLabel, String effectLabel, boolean percentageDisplay, boolean reductionDisplay,
            String experimentalNote) {
        this.upgradeNbtKey = upgradeNbtKey;
        this.increment = increment;
        this.titleColor = titleColor;
        this.upgradeLabel = upgradeLabel;
        this.effectLabel = effectLabel;
        this.percentageDisplay = percentageDisplay;
        this.reductionDisplay = reductionDisplay;
        this.experimentalNote = experimentalNote;

        this.setRegistryName("insanetweaks", registryName);
        this.setUnlocalizedName(registryName);
        this.setCreativeTab(CreativeTabs.MISC);
        this.setMaxStackSize(16);
    }

    public String getUpgradeNbtKey() {
        return upgradeNbtKey;
    }

    public float getIncrement() {
        return increment;
    }

    public void addCoreTooltip(List<String> tooltip) {
        tooltip.add("");
        tooltip.add(TextFormatting.GOLD + "Combine in anvil with any armor piece,");
        tooltip.add(TextFormatting.GOLD + "maximum x2 times per core type!");

        if (experimentalNote != null && !experimentalNote.isEmpty()) {
            tooltip.add(TextFormatting.DARK_GRAY + experimentalNote);
        }
    }

    public void addAppliedUpgradeTooltip(List<String> tooltip, NBTTagCompound nbt, int maxUpgrades) {
        if (nbt == null || !nbt.hasKey(upgradeNbtKey)) {
            return;
        }

        float bonus = nbt.getFloat(upgradeNbtKey);
        int level = Math.round(bonus / increment);

        if (level <= 0) {
            return;
        }

        tooltip.add(titleColor + upgradeLabel + ": " + level + " / " + maxUpgrades);
        tooltip.add(TextFormatting.GRAY + "  " + formatEffectLine(level));

        if (experimentalNote != null && !experimentalNote.isEmpty()) {
            tooltip.add(TextFormatting.DARK_GRAY + "  " + experimentalNote);
        }
    }

    private String formatEffectLine(int level) {
        String sign = reductionDisplay ? "-" : "+";

        if (percentageDisplay) {
            int amount = Math.round(level * increment * 100.0f);
            return sign + amount + "% " + effectLabel;
        }

        int amount = Math.round(level * increment);
        return sign + amount + " " + effectLabel;
    }
}
