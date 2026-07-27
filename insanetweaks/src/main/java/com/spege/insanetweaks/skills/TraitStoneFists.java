package com.spege.insanetweaks.skills;

import net.minecraft.util.ResourceLocation;

public class TraitStoneFists extends TraitBase {

    public TraitStoneFists() {
        super("stone_fists", 1, 3, com.spege.insanetweaks.config.ModConfig.traits.stoneFists, "reskillable:mining", 14,
                "reskillable:mining|30", "reskillable:gathering|20", "reskillable:building|20");
        this.setIcon(new ResourceLocation("minecraft", "textures/items/stone_pickaxe.png"));
    }

}
