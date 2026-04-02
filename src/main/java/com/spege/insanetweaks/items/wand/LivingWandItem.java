package com.spege.insanetweaks.items.wand;

import net.minecraft.creativetab.CreativeTabs;

import electroblob.wizardry.constants.Tier;

public class LivingWandItem extends BaseCustomWandItem {
    @SuppressWarnings("null")
    public LivingWandItem() {
        super(Tier.MASTER, null, 0.40f, 1);
        setMaxDamage(4000);
        this.setRegistryName("insanetweaks", "living_wand");
        this.setUnlocalizedName("living_wand");
        CreativeTabs tab = CreativeTabs.COMBAT;
        if (tab != null)
            this.setCreativeTab(tab);
    }
}
