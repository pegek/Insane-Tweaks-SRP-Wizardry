package com.spege.insanetweaks.skills;

import net.minecraft.util.ResourceLocation;

public class TraitSchoolConjuration extends TraitBase {

    public TraitSchoolConjuration() {
        super("school_of_conjuration", 4, 3, com.spege.insanetweaks.config.ModConfig.traits.schoolOfConjuration, "reskillable:magic", 5, "reskillable:magic|22");
        this.setIcon(new ResourceLocation("minecraft", "textures/items/bone.png"));
    }

}
