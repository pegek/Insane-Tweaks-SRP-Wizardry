package com.spege.insanetweaks.skills;

import net.minecraft.util.ResourceLocation;

public class TraitDoubleLoot extends TraitBase {

    public TraitDoubleLoot() {
        super("double_loot", 4, 1, "reskillable:gathering", 1, "reskillable:gathering|5");
        this.setIcon(new ResourceLocation("minecraft", "textures/items/fish_cod_raw.png"));
    }

}
