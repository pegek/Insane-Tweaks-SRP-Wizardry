package com.spege.insanetweaks.skills;

import net.minecraft.util.ResourceLocation;

public class TraitSpidersGrace extends TraitBase {

    public TraitSpidersGrace() {
        super("spiders_grace", 3, 3, com.spege.insanetweaks.config.ModConfig.traits.spidersGrace, "reskillable:defense", 7, "reskillable:defense|35");
        this.setIcon(new ResourceLocation("minecraft", "textures/items/string.png"));
    }

}
