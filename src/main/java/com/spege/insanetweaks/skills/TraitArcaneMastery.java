package com.spege.insanetweaks.skills;

import net.minecraft.util.ResourceLocation;

public class TraitArcaneMastery extends TraitBase {

    public TraitArcaneMastery() {
        super("arcane_mastery", 3, 3, com.spege.insanetweaks.config.ModConfig.traits.arcaneMastery, "reskillable:magic", 5, "reskillable:magic|32");
        this.setIcon(new ResourceLocation("minecraft", "textures/items/experience_bottle.png"));
    }

}
