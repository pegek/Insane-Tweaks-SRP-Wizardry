package com.spege.reskilltweaks.skills;

import net.minecraft.util.ResourceLocation;

public class TraitEnchantFishing extends TraitBase {

    public TraitEnchantFishing() {
        super("enchant_fishing", 4, 2, com.spege.reskilltweaks.config.ReskillTweaksConfig.traits.enchantFishing, "reskillable:gathering", 7, "reskillable:gathering|32");
        this.setIcon(new ResourceLocation("minecraft", "textures/items/book_enchanted.png"));
    }

}
