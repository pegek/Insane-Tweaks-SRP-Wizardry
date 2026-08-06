package com.spege.reskilltweaks.skills;

import net.minecraft.util.ResourceLocation;

public class TraitScarredFlesh extends TraitBase {

    public TraitScarredFlesh() {
        super("scarred_flesh", 1, 3, com.spege.reskilltweaks.config.ReskillTweaksConfig.traits.scarredFlesh, "reskillable:defense", 15,
                "reskillable:defense|40");
        this.setIcon(new ResourceLocation("minecraft", "textures/items/fermented_spider_eye.png"));
    }

}
