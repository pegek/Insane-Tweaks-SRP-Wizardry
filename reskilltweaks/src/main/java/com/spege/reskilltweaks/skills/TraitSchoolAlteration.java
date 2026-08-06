package com.spege.reskilltweaks.skills;

import net.minecraft.util.ResourceLocation;

public class TraitSchoolAlteration extends TraitBase {

    public TraitSchoolAlteration() {
        super("school_of_alteration", 2, 3, com.spege.reskilltweaks.config.ReskillTweaksConfig.traits.schoolOfAlteration, "reskillable:magic", 5, "reskillable:magic|28");
        this.setIcon(new ResourceLocation("minecraft", "textures/items/blaze_rod.png"));
    }

}
