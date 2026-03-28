package com.spege.insanetweaks.skills;

import net.minecraft.util.ResourceLocation;

public class TraitIronStomach extends TraitBase {

    public TraitIronStomach() {
        super("iron_stomach", 2, 2, "reskillable:defense", 4, "reskillable:defense|20");
        this.setIcon(new ResourceLocation("minecraft", "textures/items/apple.png"));
    }

}
