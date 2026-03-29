package com.spege.insanetweaks.skills;

import net.minecraft.util.ResourceLocation;

public class TraitSchoolAlteration extends TraitBase {

    public TraitSchoolAlteration() {
        super("school_of_alteration", 2, 3, com.spege.insanetweaks.config.ModConfig.traits.schoolOfAlteration, "reskillable:magic", 8, "reskillable:magic|44");
        this.setIcon(new ResourceLocation("minecraft", "textures/items/blaze_rod.png"));
    }

}
