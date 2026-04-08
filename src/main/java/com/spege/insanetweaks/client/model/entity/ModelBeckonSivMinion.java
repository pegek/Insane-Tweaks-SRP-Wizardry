package com.spege.insanetweaks.client.model.entity;

import com.dhanantry.scapeandrunparasites.client.model.entity.deterrent.nexus.ModelVenkrolSIV;
import com.dhanantry.scapeandrunparasites.entity.monster.deterrent.nexus.EntityVenkrolSIV;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.MathHelper;

public class ModelBeckonSivMinion extends ModelVenkrolSIV {

    @Override
    public void setRotationAngles(float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch, float scaleFactor, @javax.annotation.Nonnull Entity entityIn) {
        if (entityIn instanceof EntityVenkrolSIV) {
            super.setRotationAngles(limbSwing, limbSwingAmount, ageInTicks, netHeadYaw, headPitch, scaleFactor, entityIn);
            return;
        }

        // Reset base bounds for static animation
        this.jointDBL1.rotateAngleX = 0.0f;
        this.jointDBL2.rotateAngleY = 0.0f;
        this.jointDBL3.rotateAngleY = 0.0f;
        this.jointDBL4.rotateAngleY = 0.0f;
        this.jointDBR1.rotateAngleX = 0.0f;
        this.jointDBR2.rotateAngleY = 0.0f;
        this.jointDBR3.rotateAngleY = 0.0f;
        this.jointDBR4.rotateAngleY = 0.0f;
        this.jointDFL1.rotateAngleX = 0.0f;
        this.jointDFL2.rotateAngleY = 0.0f;
        this.jointDFL3.rotateAngleY = 0.0f;
        this.jointDFL4.rotateAngleY = 0.0f;
        this.jointDFR1.rotateAngleX = 0.0f;
        this.jointDFR2.rotateAngleY = 0.0f;
        this.jointDFR3.rotateAngleY = 0.0f;
        this.jointDFR4.rotateAngleY = 0.0f;
        this.jointML1.rotateAngleX = 0.0f;
        this.jointML2.rotateAngleY = 0.0f;
        this.jointML3.rotateAngleY = 0.0f;
        this.jointML4.rotateAngleY = 0.0f;
        this.jointMR1.rotateAngleX = 0.0f;
        this.jointMR2.rotateAngleY = 0.0f;
        this.jointMR3.rotateAngleY = 0.0f;
        this.jointMR4.rotateAngleY = 0.0f;
        this.jointMF1.rotateAngleX = 0.0f;
        this.jointMF2.rotateAngleY = 0.0f;
        this.jointMF3.rotateAngleY = 0.0f;
        this.jointMF4.rotateAngleY = 0.0f;
        this.jointMB1.rotateAngleX = 0.0f;
        this.jointMB2.rotateAngleY = 0.0f;
        this.jointMB3.rotateAngleY = 0.0f;
        this.jointMB4.rotateAngleY = 0.0f;
        this.taclejointBL1.rotateAngleX = 0.0f;
        this.taclejointBL2.rotateAngleX = 0.0f;
        this.taclejointBL3.rotateAngleX = 0.0f;
        this.taclejointBL4.rotateAngleX = 0.0f;
        this.taclejointBL5.rotateAngleX = 0.0f;
        this.taclejointBL6.rotateAngleX = 0.0f;
        this.taclejointBL7.rotateAngleX = 0.0f;
        this.taclejointBL8.rotateAngleX = 0.0f;
        this.taclejointBR1.rotateAngleX = 0.0f;
        this.taclejointBR2.rotateAngleX = 0.0f;
        this.taclejointBR3.rotateAngleX = 0.0f;
        this.taclejointBR4.rotateAngleX = 0.0f;
        this.taclejointBR5.rotateAngleX = 0.0f;
        this.taclejointBR6.rotateAngleX = 0.0f;
        this.taclejointBR7.rotateAngleX = 0.0f;
        this.taclejointBR8.rotateAngleX = 0.0f;
        this.taclejointFL1.rotateAngleX = 0.0f;
        this.taclejointFL2.rotateAngleX = 0.0f;
        this.taclejointFL3.rotateAngleX = 0.0f;
        this.taclejointFL4.rotateAngleX = 0.0f;
        this.taclejointFL5.rotateAngleX = 0.0f;
        this.taclejointFL6.rotateAngleX = 0.0f;
        this.taclejointFL7.rotateAngleX = 0.0f;
        this.taclejointFL8.rotateAngleX = 0.0f;
        this.taclejointFR1.rotateAngleX = 0.0f;
        this.taclejointFR2.rotateAngleX = 0.0f;
        this.taclejointFR3.rotateAngleX = 0.0f;
        this.taclejointFR4.rotateAngleX = 0.0f;
        this.taclejointFR5.rotateAngleX = 0.0f;
        this.taclejointFR6.rotateAngleX = 0.0f;
        this.taclejointFR7.rotateAngleX = 0.0f;
        this.taclejointFR8.rotateAngleX = 0.0f;
        
        // Emulate stage 1 default animations
        float f1 = -0.3f * MathHelper.cos(ageInTicks * 0.051688f) * 0.011f;
        float f2 = -0.6f * MathHelper.cos(ageInTicks * 0.013515f) * 0.011f;
        float f3 = 0.3f * MathHelper.cos(ageInTicks * 0.1f) * 0.1f;
        float f4 = 0.3f * MathHelper.cos(ageInTicks * 0.1f) * 0.1f;
        
        this.body1.rotateAngleX = f1;
        this.body2.rotateAngleX = f1;
        this.body3.rotateAngleX = f1;
        this.body4.rotateAngleX = f1;
        this.body5.rotateAngleX = f1;
        
        this.body1.rotateAngleZ = f2;
        this.body2.rotateAngleZ = f2;
        this.body3.rotateAngleZ = f2;
        this.body4.rotateAngleZ = f2;
        this.body5.rotateAngleZ = f2;
        
        f1 = -0.3f * MathHelper.cos(ageInTicks * 0.11688f) * 0.31f;
        f2 = -0.6f * MathHelper.cos(ageInTicks * 0.093515f) * 0.273f;
        f3 = 0.3f * MathHelper.cos(ageInTicks * 0.1f) * 0.29f;
        f4 = 0.6f * MathHelper.cos(ageInTicks * 0.11f) * 0.28f;
        
        this.jointDBL1.rotateAngleX = -1.0f * f3;
        this.jointDBL2.rotateAngleY = f3;
        this.jointDBL3.rotateAngleY = f3;
        this.jointDBL4.rotateAngleY = 0.0f;
        this.jointDBR1.rotateAngleX = f3;
        this.jointDBR2.rotateAngleY = -1.0f * f3;
        this.jointDBR3.rotateAngleY = f3;
        this.jointDBR4.rotateAngleY = 0.0f;
        this.jointDFL1.rotateAngleX = -1.0f * f2;
        this.jointDFL2.rotateAngleY = f2;
        this.jointDFL3.rotateAngleY = f1;
        this.jointDFL4.rotateAngleY = 0.0f;
        this.jointDFR1.rotateAngleX = f1;
        this.jointDFR2.rotateAngleY = -1.0f * f1;
        this.jointDFR3.rotateAngleY = f2;
        this.jointDFR4.rotateAngleY = 0.0f;
        
        f1 = -0.3f * MathHelper.cos(ageInTicks * 0.11688f) * 0.41f;
        f2 = -0.6f * MathHelper.cos(ageInTicks * 0.093515f) * 0.373f;
        f3 = 0.3f * MathHelper.cos(ageInTicks * 0.1f) * 0.39f;
        f4 = 0.6f * MathHelper.cos(ageInTicks * 0.11f) * 0.38f;
        
        this.jointML1.rotateAngleX = -1.0f * f1;
        this.jointML2.rotateAngleY = 0.0f;
        this.jointML3.rotateAngleY = -1.0f * f2;
        this.jointML4.rotateAngleY = 0.0f;
        this.jointMR1.rotateAngleX = f1;
        this.jointMR2.rotateAngleY = 0.0f;
        this.jointMR3.rotateAngleY = f2;
        this.jointMR4.rotateAngleY = 0.0f;
        this.jointMF1.rotateAngleX = f3;
        this.jointMF2.rotateAngleY = 0.0f;
        this.jointMF3.rotateAngleY = f4;
        this.jointMF4.rotateAngleY = 0.0f;
        this.jointMB1.rotateAngleX = f1;
        this.jointMB2.rotateAngleY = 0.0f;
        this.jointMB3.rotateAngleY = f2;
        this.jointMB4.rotateAngleY = 0.0f;
        
        f1 = -0.3f * MathHelper.cos(ageInTicks * 0.0711688f) * 0.11f;
        f2 = -0.6f * MathHelper.cos(ageInTicks * 0.083515f) * 0.143f;
        f3 = 0.3f * MathHelper.cos(ageInTicks * 0.061f) * 0.139f;
        f4 = 0.6f * MathHelper.cos(ageInTicks * 0.0711f) * 0.128f;
        
        this.taclejointBL1.rotateAngleX = f1;
        this.taclejointBL2.rotateAngleX = f4;
        this.taclejointBL3.rotateAngleX = f2;
        this.taclejointBL4.rotateAngleX = f1;
        this.taclejointBL5.rotateAngleX = f2;
        this.taclejointBL6.rotateAngleX = f4;
        this.taclejointBL7.rotateAngleX = f2;
        this.taclejointBL8.rotateAngleX = f1;
        
        this.taclejointBR1.rotateAngleX = f2;
        this.taclejointBR2.rotateAngleX = f3;
        this.taclejointBR3.rotateAngleX = f2;
        this.taclejointBR4.rotateAngleX = f1;
        this.taclejointBR5.rotateAngleX = f2;
        this.taclejointBR6.rotateAngleX = f3;
        this.taclejointBR7.rotateAngleX = f2;
        this.taclejointBR8.rotateAngleX = f1;
        
        this.taclejointFL1.rotateAngleX = f3;
        this.taclejointFL2.rotateAngleX = f4;
        this.taclejointFL3.rotateAngleX = f3;
        this.taclejointFL4.rotateAngleX = f1;
        this.taclejointFL5.rotateAngleX = f3;
        this.taclejointFL6.rotateAngleX = f4;
        this.taclejointFL7.rotateAngleX = f3;
        this.taclejointFL8.rotateAngleX = f1;
        
        this.taclejointFR1.rotateAngleX = f4;
        this.taclejointFR2.rotateAngleX = f1;
        this.taclejointFR3.rotateAngleX = f3;
        this.taclejointFR4.rotateAngleX = f2;
        this.taclejointFR5.rotateAngleX = f4;
        this.taclejointFR6.rotateAngleX = f1;
        this.taclejointFR7.rotateAngleX = f3;
        this.taclejointFR8.rotateAngleX = f2;
    }
}
