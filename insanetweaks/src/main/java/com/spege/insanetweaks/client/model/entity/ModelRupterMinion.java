package com.spege.insanetweaks.client.model.entity;

import com.dhanantry.scapeandrunparasites.client.model.entity.inborn.ModelMudo;
import com.dhanantry.scapeandrunparasites.entity.ai.misc.EntityParasiteBase;

import net.minecraft.entity.Entity;
import net.minecraft.util.math.MathHelper;

@SuppressWarnings("null")
public class ModelRupterMinion extends ModelMudo {

    @Override
    public void setRotationAngles(float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw,
            float headPitch, float scaleFactor, Entity entityIn) {

        if (entityIn instanceof EntityParasiteBase) {
            super.setRotationAngles(limbSwing, limbSwingAmount, ageInTicks, netHeadYaw, headPitch, scaleFactor,
                    entityIn);
            return;
        }

        float swing = MathHelper.cos(limbSwing * 0.6662F) * 0.35F * limbSwingAmount;
        this.mainbody.rotateAngleX = 0.0F;
        this.jointFLLX.rotateAngleY = -swing;
        this.jointFRLX.rotateAngleY = -swing;
        this.jointBLLX.rotateAngleY = swing;
        this.jointBRLX.rotateAngleY = swing;
        this.jointFLLY.rotateAngleZ = -0.25F + swing;
        this.jointFRLY.rotateAngleZ = -0.25F - swing;
        this.jointBLLY.rotateAngleZ = swing * 0.8F;
        this.jointBRLY.rotateAngleZ = -swing * 0.8F;
    }
}
