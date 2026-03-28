package com.spege.insanetweaks.skills;

import net.minecraft.util.ResourceLocation;

public class TraitAstralProspector extends TraitBase {

    public TraitAstralProspector() {
        super("astral_prospector", 3, 3, "reskillable:mining", 2, "reskillable:mining|32");
        this.setIcon(new ResourceLocation("minecraft", "textures/items/diamond_pickaxe.png"));
    }

}
