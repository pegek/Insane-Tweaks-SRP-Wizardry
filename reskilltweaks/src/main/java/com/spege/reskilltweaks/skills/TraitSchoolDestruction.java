package com.spege.reskilltweaks.skills;

import net.minecraft.util.ResourceLocation;

public class TraitSchoolDestruction extends TraitBase {

    public TraitSchoolDestruction() {
        super("school_of_destruction", 4, 1, com.spege.reskilltweaks.config.ReskillTweaksConfig.traits.schoolOfDestruction, "reskillable:magic", 5, "reskillable:magic|22");
        this.setIcon(new ResourceLocation("minecraft", "textures/items/fireball.png"));
    }

}
