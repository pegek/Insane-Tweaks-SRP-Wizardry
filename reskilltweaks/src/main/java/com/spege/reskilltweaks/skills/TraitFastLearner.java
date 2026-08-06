package com.spege.reskilltweaks.skills;

import net.minecraft.util.ResourceLocation;

public class TraitFastLearner extends TraitBase {

    public TraitFastLearner() {
        super("fast_learner", 2, 1, com.spege.reskilltweaks.config.ReskillTweaksConfig.traits.fastLearner, "reskillable:attack", 6, "reskillable:attack|8");
        // We set the icon here, same as changeIcon("contenttweaker:textures/traits/fast_learner.png")
        // But since we removed contenttweaker, let's use a standard vanilla icon or an insanetweaks icon!
        this.setIcon(new ResourceLocation("minecraft", "textures/items/experience_bottle.png"));
    }

}
