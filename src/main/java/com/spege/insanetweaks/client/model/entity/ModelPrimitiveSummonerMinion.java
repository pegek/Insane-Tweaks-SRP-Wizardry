package com.spege.insanetweaks.client.model.entity;

import com.dhanantry.scapeandrunparasites.client.model.entity.primitive.ModelCanra;
import com.dhanantry.scapeandrunparasites.entity.ai.misc.EntityParasiteBase;

import net.minecraft.entity.Entity;
import net.minecraft.util.math.MathHelper;

public class ModelPrimitiveSummonerMinion extends ModelCanra {

    @Override
    public void setRotationAngles(float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw,
            float headPitch, float scaleFactor, Entity entityIn) {

        if (entityIn instanceof EntityParasiteBase) {
            super.setRotationAngles(limbSwing, limbSwingAmount, ageInTicks, netHeadYaw, headPitch, scaleFactor,
                    entityIn);
            return;
        }

        this.jointFLLX.rotateAngleY = 0.0F;
        this.jointFLLY.rotateAngleZ = 0.0F;
        this.jointFRLX.rotateAngleY = 0.0F;
        this.jointFRLY.rotateAngleZ = 0.0F;
        this.jointBLLX.rotateAngleY = 0.0F;
        this.jointBLLY.rotateAngleZ = 0.0F;
        this.jointBRLX.rotateAngleY = 0.0F;
        this.jointBRLY.rotateAngleZ = 0.0F;
        this.jointLM.rotateAngleY = 0.0F;
        this.jointRM.rotateAngleY = 0.0F;
        this.mainbody.offsetY = 0.0F;
        this.jointH.rotateAngleX = 0.0F;
        this.jointH.rotateAngleY = 0.0F;

        float swing = MathHelper.cos(limbSwing * 0.6662F) * 0.2F * limbSwingAmount;
        this.jointFLLX.rotateAngleY = -swing;
        this.jointFRLX.rotateAngleY = -swing;
        this.jointBLLX.rotateAngleY = swing;
        this.jointBRLX.rotateAngleY = swing;
        this.jointFLLY.rotateAngleZ = swing * 1.2F;
        this.jointFRLY.rotateAngleZ = -swing * 1.2F;
        this.jointBLLY.rotateAngleZ = -swing;
        this.jointBRLY.rotateAngleZ = swing;
        this.jointLM.rotateAngleY = MathHelper.cos(ageInTicks * 0.2F) * 0.1F;
        this.jointRM.rotateAngleY = -this.jointLM.rotateAngleY;
    }
}
