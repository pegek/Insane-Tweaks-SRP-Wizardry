package com.spege.insanetweaks.skills;

import net.minecraft.util.ResourceLocation;

public class TraitCoiledSpring extends TraitBase {

    public TraitCoiledSpring() {
        super("coiled_spring", 3, 2, com.spege.insanetweaks.config.ModConfig.traits.coiledSpring, "reskillable:agility", 8,
                "reskillable:agility|40");
        this.setIcon(new ResourceLocation("minecraft", "textures/items/feather.png"));
    }

}
