package com.spege.insanetweaks.skills;

import net.minecraft.util.ResourceLocation;

public class TraitArchmage extends TraitBase {

    public TraitArchmage() {
        super("archmage", 3, 1, com.spege.insanetweaks.config.ModConfig.traits.archmage, "reskillable:magic", 5, "reskillable:magic|50");
        this.setIcon(new ResourceLocation("minecraft", "textures/items/nether_star.png"));
    }

}
