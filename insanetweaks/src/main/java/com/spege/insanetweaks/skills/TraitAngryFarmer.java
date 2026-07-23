package com.spege.insanetweaks.skills;

import net.minecraft.util.ResourceLocation;

public class TraitAngryFarmer extends TraitBase {

    public TraitAngryFarmer() {
        super("angry_farmer", 1, 1, com.spege.insanetweaks.config.ModConfig.traits.angryFarmer, "reskillable:farming", 10, "reskillable:farming|45");
        this.setIcon(new ResourceLocation("minecraft", "textures/items/iron_hoe.png"));
    }

}
