package com.spege.insanetweaks.skills;

import net.minecraft.util.ResourceLocation;

public class TraitMeditation extends TraitBase {

    public TraitMeditation() {
        super("meditation", 2, 1, com.spege.insanetweaks.config.ModConfig.traits.meditation, "reskillable:agility", 6, "reskillable:agility|18", "reskillable:magic|10");
        this.setIcon(new ResourceLocation("minecraft", "textures/blocks/flower_houstonia.png"));
    }

}
