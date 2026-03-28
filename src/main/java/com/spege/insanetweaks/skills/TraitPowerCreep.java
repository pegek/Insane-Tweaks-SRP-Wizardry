package com.spege.insanetweaks.skills;

import net.minecraft.util.ResourceLocation;

public class TraitPowerCreep extends TraitBase {

    public TraitPowerCreep() {
        super("power_creep", 3, 1, "reskillable:magic", 5, "reskillable:magic|50");
        this.setIcon(new ResourceLocation("minecraft", "textures/items/nether_star.png"));
    }

}
