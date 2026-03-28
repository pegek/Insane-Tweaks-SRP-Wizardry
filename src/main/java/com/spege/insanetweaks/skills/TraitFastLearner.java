package com.spege.insanetweaks.skills;

import net.minecraft.util.ResourceLocation;

public class TraitFastLearner extends TraitBase {

    public TraitFastLearner() {
        super("fast_learner", 2, 1, "reskillable:attack", 2, "reskillable:attack|5");
        // We set the icon here, same as changeIcon("contenttweaker:textures/traits/fast_learner.png")
        // But since we removed contenttweaker, let's use a standard vanilla icon or an insanetweaks icon!
        this.setIcon(new ResourceLocation("minecraft", "textures/items/experience_bottle.png"));
    }

}
