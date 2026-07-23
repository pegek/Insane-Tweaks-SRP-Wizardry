package com.spege.insanetweaks.items.bridge;

import javax.annotation.Nonnull;

import electroblob.wizardry.item.ItemWandUpgrade;
import net.minecraft.item.EnumRarity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;

import com.spege.insanetweaks.InsaneTweaksMod;

public class AdaptationUpgradeItem extends ItemWandUpgrade {

    @SuppressWarnings("null")
    public AdaptationUpgradeItem() {
        this.setRegistryName(new ResourceLocation(InsaneTweaksMod.MODID, "adaptation_upgrade"));
        this.setUnlocalizedName("adaptation_upgrade");
    }

    @Override
    public net.minecraftforge.common.IRarity getForgeRarity(@Nonnull ItemStack stack) {
        return EnumRarity.RARE;
    }
}
