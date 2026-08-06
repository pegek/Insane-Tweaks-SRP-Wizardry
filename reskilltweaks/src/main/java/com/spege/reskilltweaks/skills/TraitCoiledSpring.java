package com.spege.reskilltweaks.skills;

import net.minecraft.util.ResourceLocation;

public class TraitCoiledSpring extends TraitBase {

    public TraitCoiledSpring() {
        super("coiled_spring", 3, 2, com.spege.reskilltweaks.config.ReskillTweaksConfig.traits.coiledSpring, "reskillable:agility", 8,
                "reskillable:agility|40");
        this.setIcon(new ResourceLocation("minecraft", "textures/items/feather.png"));
    }

}
