package com.spege.insanetweaks.skills;

import net.minecraft.util.ResourceLocation;

public class TraitAstralProspector extends TraitBase {

    public TraitAstralProspector() {
        super("astral_prospector", 3, 3, com.spege.insanetweaks.config.ModConfig.traits.astralProspector, "reskillable:mining", 7, "reskillable:mining|30");
        this.setIcon(new ResourceLocation("minecraft", "textures/items/diamond_pickaxe.png"));
    }

}
