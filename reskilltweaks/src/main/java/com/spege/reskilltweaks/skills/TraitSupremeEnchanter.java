package com.spege.reskilltweaks.skills;

import net.minecraft.util.ResourceLocation;

public class TraitSupremeEnchanter extends TraitBase {

    public TraitSupremeEnchanter() {
        super("supreme_enchanter", 4, 2, com.spege.reskilltweaks.config.ReskillTweaksConfig.traits.supremeEnchanter, "reskillable:building", 8, "reskillable:building|30");
        this.setIcon(new ResourceLocation("minecraft", "textures/items/book_enchanted.png"));
    }

}
