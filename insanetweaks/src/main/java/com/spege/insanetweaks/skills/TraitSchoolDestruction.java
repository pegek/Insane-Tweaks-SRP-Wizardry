package com.spege.insanetweaks.skills;

import net.minecraft.util.ResourceLocation;

public class TraitSchoolDestruction extends TraitBase {

    public TraitSchoolDestruction() {
        super("school_of_destruction", 4, 1, com.spege.insanetweaks.config.ModConfig.traits.schoolOfDestruction, "reskillable:magic", 5, "reskillable:magic|22");
        this.setIcon(new ResourceLocation("minecraft", "textures/items/fireball.png"));
    }

}
