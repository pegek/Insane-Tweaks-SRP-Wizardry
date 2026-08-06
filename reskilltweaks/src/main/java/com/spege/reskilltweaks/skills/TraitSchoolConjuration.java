package com.spege.reskilltweaks.skills;

import net.minecraft.util.ResourceLocation;

public class TraitSchoolConjuration extends TraitBase {

    public TraitSchoolConjuration() {
        super("school_of_conjuration", 4, 3, com.spege.reskilltweaks.config.ReskillTweaksConfig.traits.schoolOfConjuration, "reskillable:magic", 5, "reskillable:magic|22");
        this.setIcon(new ResourceLocation("minecraft", "textures/items/bone.png"));
    }

}
