package com.spege.reskilltweaks.skills;

import net.minecraft.util.ResourceLocation;

public class TraitArchmage extends TraitBase {

    public TraitArchmage() {
        super("archmage", 3, 1, com.spege.reskilltweaks.config.ReskillTweaksConfig.traits.archmage, "reskillable:magic", 8, "reskillable:magic|45");
        this.setIcon(new ResourceLocation("minecraft", "textures/items/nether_star.png"));
    }

}
