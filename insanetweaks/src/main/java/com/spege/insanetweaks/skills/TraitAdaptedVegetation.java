package com.spege.insanetweaks.skills;

import net.minecraft.util.ResourceLocation;

public class TraitAdaptedVegetation extends TraitBase {

    public TraitAdaptedVegetation() {
        super("adapted_vegetation", 2, 0, com.spege.insanetweaks.config.ModConfig.traits.adaptedVegetation, "reskillable:farming", 5, "reskillable:farming|18");
        this.setIcon(new ResourceLocation("minecraft", "textures/items/seeds_wheat.png"));
    }

}
