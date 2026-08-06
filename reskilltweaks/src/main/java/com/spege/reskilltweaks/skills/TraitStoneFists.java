package com.spege.reskilltweaks.skills;

import net.minecraft.util.ResourceLocation;

public class TraitStoneFists extends TraitBase {

    public TraitStoneFists() {
        super("stone_fists", 1, 3, com.spege.reskilltweaks.config.ReskillTweaksConfig.traits.stoneFists, "reskillable:mining", 14,
                "reskillable:mining|30", "reskillable:gathering|20", "reskillable:building|20");
        this.setIcon(new ResourceLocation("minecraft", "textures/items/stone_pickaxe.png"));
    }

}
