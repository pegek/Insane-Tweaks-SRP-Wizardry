package com.spege.insanetweaks.skills;

import net.minecraft.util.ResourceLocation;

public class TraitIronStomach extends TraitBase {

    public TraitIronStomach() {
        super("iron_stomach", 2, 2, com.spege.insanetweaks.config.ModConfig.traits.ironStomach, "reskillable:defense", 5, "reskillable:defense|15");
        this.setIcon(new ResourceLocation("minecraft", "textures/items/apple.png"));
    }

}
