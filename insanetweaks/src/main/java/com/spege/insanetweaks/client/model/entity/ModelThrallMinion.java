package com.spege.insanetweaks.client.model.entity;

import net.minecraft.client.model.ModelBiped;
import net.minecraft.client.model.ModelRenderer;
import net.minecraft.entity.Entity;

/**
 * Port of ModelMinion from AtomicStryker's Minions mod.
 * Uses deobfuscated field names (bipedHead, bipedBody, etc.) from MCP 1.12.2 mappings.
 * Added a backpack renderer on the back (iconic Minion visual).
 */
@SuppressWarnings("null")
public class ModelThrallMinion extends ModelBiped {

    public ModelRenderer backPack;
    /** True when thrall has items in its inventory — raises arms like it's carrying something. */
    public boolean carryAnimation = false;

    public ModelThrallMinion() {
        this(0.0F);
    }

    public ModelThrallMinion(float scale) {
        // isChild = false
        this.isChild = false;

        // Left arm (mirrored)
        this.bipedLeftArm = new ModelRenderer(this, 24, 0);
        this.bipedLeftArm.mirror = false;

        // Right arm (mirrored)
        this.bipedRightArm = new ModelRenderer(this, 32, 0);
        this.bipedRightArm.mirror = false;

        // Head — slightly smaller than vanilla
        this.bipedHead = new ModelRenderer(this, 0, 0);
        this.bipedHead.addBox(-3.0F, -6.0F, -3.0F, 6, 5, 4);
        this.bipedHead.setRotationPoint(0.0F, 12.0F, 0.0F);

        // Body — squat body, shifted down
        this.bipedBody = new ModelRenderer(this, 22, 0);
        this.bipedBody.addBox(-4.0F, -3.0F, -2.0F, 8, 8, 4);
        this.bipedBody.setRotationPoint(0.0F, 14.0F, 2.0F);

        // Left leg
        this.bipedLeftLeg = new ModelRenderer(this, 0, 10);
        this.bipedLeftLeg.addBox(-2.0F, 7.0F, 0.0F, 2, 5, 3);
        this.bipedLeftLeg.setRotationPoint(-1.0F, 22.0F, 2.0F);

        // Right leg (mirrored)
        this.bipedRightLeg = new ModelRenderer(this, 0, 10);
        this.bipedRightLeg.addBox(0.0F, 7.0F, 0.0F, 2, 5, 3);
        this.bipedRightLeg.setRotationPoint(1.0F, 22.0F, 2.0F);
        this.bipedRightLeg.mirror = true;

        // Right arm
        this.bipedRightArm = new ModelRenderer(this, 0, 19);
        this.bipedRightArm.addBox(-1.0F, 0.0F, -1.0F, 2, 7, 3);
        this.bipedRightArm.setRotationPoint(-4.0F, 11.0F, 1.0F);

        // Left arm
        this.bipedLeftArm = new ModelRenderer(this, 0, 19);
        this.bipedLeftArm.addBox(-1.0F, 0.0F, -1.0F, 2, 7, 3);
        this.bipedLeftArm.setRotationPoint(4.0F, 11.0F, 1.0F);
        this.bipedLeftArm.mirror = true;

        // Backpack — signature Minion element, tilted slightly on the back
        this.backPack = new ModelRenderer(this, 11, 13);
        this.backPack.addBox(-3.0F, -2.0F, -2.0F, 6, 7, 5);
        this.backPack.setRotationPoint(0.0F, 13.0F, 4.0F);
        // Tilt forward slightly (pi/4 ≈ 0.785)
        this.backPack.rotateAngleX = 0.7853982F;

        // Hide the default ModelBiped hat overlay — our custom Minion texture
        // is not a standard player skin layout, so bipedHeadwear (texture offset 32,0)
        // would render garbage pixels as a floating artifact above the head.
        this.bipedHeadwear.showModel = false;
    }

    @Override
    public void render(Entity entity, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch, float scale) {
        super.render(entity, limbSwing, limbSwingAmount, ageInTicks, netHeadYaw, headPitch, scale);
        this.backPack.render(scale);
    }

    @Override
    public void setRotationAngles(float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch, float scaleFactor, Entity entity) {
        super.setRotationAngles(limbSwing, limbSwingAmount, ageInTicks, netHeadYaw, headPitch, scaleFactor, entity);

        // Keep head in correct position
        this.bipedHead.rotationPointY = 12.0F;

        // Carry animation: raise both arms straight up when thrall has items
        if (this.carryAnimation) {
            this.bipedLeftArm.rotateAngleX = (float) Math.PI;
            this.bipedRightArm.rotateAngleX = (float) Math.PI;
        }
    }
}
