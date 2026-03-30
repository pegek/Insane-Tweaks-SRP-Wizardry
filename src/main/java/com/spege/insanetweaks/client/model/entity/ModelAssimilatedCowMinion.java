package com.spege.insanetweaks.client.model.entity;

import com.dhanantry.scapeandrunparasites.client.model.entity.infected.ModelInfCow;
import com.dhanantry.scapeandrunparasites.entity.monster.infected.EntityInfCow;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.MathHelper;

public class ModelAssimilatedCowMinion extends ModelInfCow {

    @Override
    public void setRotationAngles(float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw,
            float headPitch, float scaleFactor, Entity entityIn) {

        if (entityIn instanceof EntityInfCow) {
            super.setRotationAngles(limbSwing, limbSwingAmount, ageInTicks, netHeadYaw, headPitch, scaleFactor,
                    entityIn);
            return;
        }

        float headYaw = netHeadYaw * 0.017453292F;
        float headPitchRadians = headPitch * 0.017453292F;
        float legSwing = MathHelper.cos(limbSwing * 0.6662F) * 0.8F * limbSwingAmount;

        this.jointH.rotateAngleY = headYaw;
        this.jointH.rotateAngleX = headPitchRadians;
        this.jointFLL.rotateAngleX = legSwing;
        this.jointFRL.rotateAngleX = -legSwing;
        this.jointBLL.rotateAngleX = -legSwing;
        this.jointBRL.rotateAngleX = legSwing;
        this.mainbody.rotateAngleX = 0.0F;
    }
}
