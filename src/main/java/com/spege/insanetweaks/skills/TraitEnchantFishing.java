package com.spege.insanetweaks.skills;

import net.minecraft.util.ResourceLocation;

public class TraitEnchantFishing extends TraitBase {

    public TraitEnchantFishing() {
        super("enchant_fishing", 4, 2, com.spege.insanetweaks.config.ModConfig.traits.enchantFishing, "reskillable:gathering", 7, "reskillable:gathering|32");
        this.setIcon(new ResourceLocation("minecraft", "textures/items/book_enchanted.png"));
    }

}
