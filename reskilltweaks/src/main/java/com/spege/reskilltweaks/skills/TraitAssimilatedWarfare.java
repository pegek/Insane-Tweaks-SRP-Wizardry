package com.spege.reskilltweaks.skills;

import net.minecraft.util.ResourceLocation;

public class TraitAssimilatedWarfare extends TraitBase {

    public TraitAssimilatedWarfare() {
        super("assimilated_warfare", 3, 0, com.spege.reskilltweaks.config.ReskillTweaksConfig.traits.assimilatedWarfare, "reskillable:attack", 6, "reskillable:attack|18");
        this.setIcon(new ResourceLocation("minecraft", "textures/items/skull_skeleton.png"));
    }

}
