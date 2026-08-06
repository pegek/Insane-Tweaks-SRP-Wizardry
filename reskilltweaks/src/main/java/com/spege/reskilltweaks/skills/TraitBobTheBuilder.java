package com.spege.reskilltweaks.skills;

import net.minecraft.util.ResourceLocation;

public class TraitBobTheBuilder extends TraitBase {

    public TraitBobTheBuilder() {
        super("bob_the_builder", 1, 2, com.spege.reskilltweaks.config.ReskillTweaksConfig.traits.bobTheBuilder, "reskillable:building", 5, "reskillable:building|18");
        this.setIcon(new ResourceLocation("minecraft", "textures/items/brick.png"));
    }

}
