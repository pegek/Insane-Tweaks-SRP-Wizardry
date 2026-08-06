package com.spege.reskilltweaks.skills;

import net.minecraft.util.ResourceLocation;

public class TraitIronStomach extends TraitBase {

    public TraitIronStomach() {
        super("iron_stomach", 2, 2, com.spege.reskilltweaks.config.ReskillTweaksConfig.traits.ironStomach, "reskillable:defense", 5, "reskillable:defense|15");
        this.setIcon(new ResourceLocation("minecraft", "textures/items/apple.png"));
    }

}
