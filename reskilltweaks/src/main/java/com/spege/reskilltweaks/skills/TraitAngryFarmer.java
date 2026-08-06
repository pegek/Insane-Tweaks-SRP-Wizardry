package com.spege.reskilltweaks.skills;

import net.minecraft.util.ResourceLocation;

public class TraitAngryFarmer extends TraitBase {

    public TraitAngryFarmer() {
        super("angry_farmer", 1, 1, com.spege.reskilltweaks.config.ReskillTweaksConfig.traits.angryFarmer, "reskillable:farming", 10, "reskillable:farming|45");
        this.setIcon(new ResourceLocation("minecraft", "textures/items/iron_hoe.png"));
    }

}
