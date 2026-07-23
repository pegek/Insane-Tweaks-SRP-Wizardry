package com.spege.insanetweaks.client.renderer.entity;

import net.minecraft.client.model.ModelBase;
import net.minecraft.client.model.ModelRenderer;
import net.minecraft.entity.Entity;

/** Minimal stem+bulb model for the Corrupted Sapling. Stage growth = render scale. */
public class ModelCorruptedSapling extends ModelBase {

    private final ModelRenderer stem;
    private final ModelRenderer bulb;

    public ModelCorruptedSapling() {
        this.textureWidth = 64;
        this.textureHeight = 64;

        this.stem = new ModelRenderer(this, 0, 0);
        this.stem.addBox(-1.5f, 0.0f, -1.5f, 3, 14, 3);
        this.stem.setRotationPoint(0.0f, 10.0f, 0.0f);

        this.bulb = new ModelRenderer(this, 16, 0);
        this.bulb.addBox(-4.0f, -8.0f, -4.0f, 8, 8, 8);
        this.bulb.setRotationPoint(0.0f, 10.0f, 0.0f);
    }

    @Override
    public void render(Entity entity, float limbSwing, float limbSwingAmount, float ageInTicks,
            float netHeadYaw, float headPitch, float scale) {
        this.stem.render(scale);
        this.bulb.render(scale);
    }
}
