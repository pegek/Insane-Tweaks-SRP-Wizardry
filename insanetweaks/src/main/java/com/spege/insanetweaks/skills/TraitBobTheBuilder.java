package com.spege.insanetweaks.skills;

import net.minecraft.util.ResourceLocation;

public class TraitBobTheBuilder extends TraitBase {

    public TraitBobTheBuilder() {
        super("bob_the_builder", 1, 2, com.spege.insanetweaks.config.ModConfig.traits.bobTheBuilder, "reskillable:building", 5, "reskillable:building|18");
        this.setIcon(new ResourceLocation("minecraft", "textures/items/brick.png"));
    }

}
