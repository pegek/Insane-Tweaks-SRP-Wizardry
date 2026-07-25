package com.spege.insanetweaks.client.model.entity;

import com.dhanantry.scapeandrunparasites.client.model.entity.pure.ModelOmboo;
import com.dhanantry.scapeandrunparasites.entity.ai.misc.EntityParasiteBase;

import net.minecraft.entity.Entity;
import net.minecraft.util.math.MathHelper;

/**
 * ModelOmboo reused for the summoned bomber.
 *
 * <p>SRP's {@code setRotationAngles} casts the entity to
 * {@code EntityParasiteBase} on its first instruction, so a non-parasite must
 * never reach {@code super} — it would be an instant ClassCastException every
 * render frame.
 */
@SuppressWarnings("null")
public class ModelLightBomberMinion extends ModelOmboo {

    @Override
    public void setRotationAngles(float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw,
            float headPitch, float scaleFactor, Entity entityIn) {

        if (entityIn instanceof EntityParasiteBase) {
            super.setRotationAngles(limbSwing, limbSwingAmount, ageInTicks, netHeadYaw, headPitch, scaleFactor,
                    entityIn);
            return;
        }

        float sway = MathHelper.cos(ageInTicks * 0.2F) * 0.15F;

        this.mainbody.rotateAngleX = 0.0F;
        this.jointLW.rotateAngleZ = sway * 0.5F;
        this.jointRW.rotateAngleZ = -sway * 0.5F;
        this.taclejointFL1.rotateAngleX = sway;
        this.taclejointFR1.rotateAngleX = sway;
        this.taclejointBL1.rotateAngleX = -sway;
        this.taclejointBR1.rotateAngleX = -sway;
        this.taclejointFL2.rotateAngleX = sway * 0.6F;
        this.taclejointFR2.rotateAngleX = sway * 0.6F;
        this.taclejointBL2.rotateAngleX = -sway * 0.6F;
        this.taclejointBR2.rotateAngleX = -sway * 0.6F;
    }
}
