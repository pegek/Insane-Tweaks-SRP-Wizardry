package com.spege.insanetweaks.skills;

import net.minecraft.util.ResourceLocation;

public class TraitSpidersGrace extends TraitBase {

    public TraitSpidersGrace() {
        super("poison_immunity", 3, 3, "reskillable:defense", 5, "reskillable:defense|50");
        this.setIcon(new ResourceLocation("minecraft", "textures/items/string.png"));
    }

}
