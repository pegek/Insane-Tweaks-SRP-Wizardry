package com.spege.insanetweaks.client.model.entity;

import com.dhanantry.scapeandrunparasites.client.model.entity.deterrent.ModelNak;
import com.dhanantry.scapeandrunparasites.entity.ai.misc.EntityPStationary;
import com.dhanantry.scapeandrunparasites.entity.monster.deterrent.EntityNak;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.util.math.MathHelper;

/**
 * Reuses SRP's Nak claw model for {@code EntityDispatcherClaw}.
 *
 * <p><b>ModelNak casts in two different methods, to two different types</b>
 * (verified with {@code javap -p -c} against SRParasites 1.10.7):
 * <ul>
 * <li>{@code setRotationAngles} (SRG {@code func_78087_a}) casts the entity to
 * {@code EntityNak} and reads {@code getTargetedEntity()} to pick between an
 * idle and a "claws extended" arm pose;</li>
 * <li>{@code setLivingAnimations} (SRG {@code func_78086_a}) casts to
 * {@code EntityPStationary} and reads {@code getFloorTimer()} to slide the whole
 * body out of the ground.</li>
 * </ul>
 * Both are unconditional {@code checkcast}s, so both must be guarded — passing
 * our claw to either would throw {@code ClassCastException} on the render
 * thread. Neither fallback calls {@code super}.
 *
 * <p>The fallback pose is SRP's own "target acquired" branch: the same constant
 * offsets that Nak snaps to while it is holding something, driven by the slower
 * idle-branch sway frequencies so the claw breathes instead of twitching.
 */
public class ModelDispatcherClaw extends ModelNak {

    // Constant offsets copied from ModelNak's "getTargetedEntity() != null"
    // branch — this is literally the pose Nak holds a victim in.
    private static final float JOINT_LA_BASE_X = 0.5F;
    private static final float JOINT_A_BASE_X = 0.8F;
    private static final float JOINT_L_BASE_X = 0.5F;

    // Sway amplitudes/frequencies from ModelNak's idle branch (slower than the
    // attack branch, which is what makes this read as "held", not "flailing").
    private static final float SWAY_LA_FREQ = 0.11095986F;
    private static final float SWAY_LA_AMP = 0.16429871F;
    private static final float SWAY_A_FREQ = 0.13986F;
    private static final float SWAY_A_AMP = 0.17429872F;
    private static final float SWAY_L_FREQ = 0.0886F;
    private static final float SWAY_L_AMP = 0.1472F;

    @Override
    public void setRotationAngles(float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw,
            float headPitch, float scaleFactor, @javax.annotation.Nonnull Entity entityIn) {

        if (entityIn instanceof EntityNak) {
            super.setRotationAngles(limbSwing, limbSwingAmount, ageInTicks, netHeadYaw, headPitch, scaleFactor,
                    entityIn);
            return;
        }

        float swayLA = MathHelper.cos(ageInTicks * SWAY_LA_FREQ) * SWAY_LA_AMP;
        float swayA = -1.0F * MathHelper.cos(ageInTicks * SWAY_A_FREQ) * SWAY_A_AMP;
        float swayL = MathHelper.cos(ageInTicks * SWAY_L_FREQ) * SWAY_L_AMP;

        this.jointLA.rotateAngleX = JOINT_LA_BASE_X + swayLA;
        this.jointLA.rotateAngleY = swayLA * 0.3F;
        this.jointLA_1.rotateAngleZ = -0.3F + swayLA;
        this.jointLA_2.rotateAngleZ = -0.6F;
        this.jointLA_3.rotateAngleZ = -1.0F + swayLA;
        this.jointLA_5.rotateAngleZ = swayLA;
        this.jointLA_7.rotateAngleZ = swayLA;

        this.jointA.rotateAngleX = JOINT_A_BASE_X + swayA;
        this.jointA.rotateAngleY = swayA * -0.2333F;
        this.jointA_1.rotateAngleZ = -0.3F + swayA;
        this.jointA_2.rotateAngleZ = -0.6F;
        this.jointA_3.rotateAngleZ = swayA;
        this.jointA_5.rotateAngleZ = swayA;
        this.jointA_7.rotateAngleZ = swayA;

        this.jointL.rotateAngleX = JOINT_L_BASE_X + swayL;
        this.jointL.rotateAngleY = swayL * 0.5F;
        this.jointL_1.rotateAngleZ = -0.3F + swayL;
        this.jointL_2.rotateAngleZ = -0.4F;
        this.jointL_3.rotateAngleZ = -0.1F + swayL;
        this.jointL_5.rotateAngleZ = swayL;
        this.jointL_7.rotateAngleZ = swayL;
    }

    @Override
    public void setLivingAnimations(@javax.annotation.Nonnull EntityLivingBase entitylivingbaseIn, float limbSwing,
            float limbSwingAmount, float partialTickTime) {

        if (entitylivingbaseIn instanceof EntityPStationary) {
            super.setLivingAnimations(entitylivingbaseIn, limbSwing, limbSwingAmount, partialTickTime);
            return;
        }

        // Fully emerged, no burrow shake — the claw is already out of the ground.
        this.mainbody.offsetX = 0.0F;
        this.mainbody.offsetY = 0.0F;
        this.mainbody.offsetZ = 0.0F;
    }
}
