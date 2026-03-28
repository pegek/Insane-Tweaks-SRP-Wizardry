package com.spege.insanetweaks.skills;

import net.minecraft.util.ResourceLocation;

public class TraitSchoolDestruction extends TraitBase {

    public TraitSchoolDestruction() {
        super("school_of_destruction", 4, 1, "reskillable:magic", 7, "reskillable:magic|16");
        this.setIcon(new ResourceLocation("minecraft", "textures/items/fireball.png"));
    }

}
