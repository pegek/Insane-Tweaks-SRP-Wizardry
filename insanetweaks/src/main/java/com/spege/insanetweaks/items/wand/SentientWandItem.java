package com.spege.insanetweaks.items.wand;

import net.minecraft.creativetab.CreativeTabs;

import electroblob.wizardry.constants.Tier;

public class SentientWandItem extends BaseCustomWandItem {
    @SuppressWarnings("null")
    public SentientWandItem() {
        super(Tier.MASTER, null, 0.80f, 1);
        // Mana capacity comes from gear.wands (BaseCustomWandItem#getMaxDamage), not from here.
        this.setRegistryName("insanetweaks", "sentient_wand");
        this.setUnlocalizedName("sentient_wand");
        CreativeTabs tab = CreativeTabs.COMBAT;
        if (tab != null)
            this.setCreativeTab(tab);
    }
}
