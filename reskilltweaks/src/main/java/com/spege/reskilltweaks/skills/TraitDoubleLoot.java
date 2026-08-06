package com.spege.reskilltweaks.skills;

import net.minecraft.util.ResourceLocation;

public class TraitDoubleLoot extends TraitBase {

    public TraitDoubleLoot() {
        super("double_loot", 4, 1, com.spege.reskilltweaks.config.ReskillTweaksConfig.traits.doubleLoot, "reskillable:gathering", 5, "reskillable:gathering|10");
        this.setIcon(new ResourceLocation("minecraft", "textures/items/fish_cod_raw.png"));
    }

}
