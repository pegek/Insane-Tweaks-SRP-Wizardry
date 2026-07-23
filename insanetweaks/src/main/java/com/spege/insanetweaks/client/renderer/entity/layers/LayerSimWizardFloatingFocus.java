package com.spege.insanetweaks.client.renderer.entity.layers;

import javax.annotation.Nonnull;

import com.spege.insanetweaks.entities.EntitySimWizard;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.block.model.ItemCameraTransforms;
import net.minecraft.client.renderer.entity.layers.LayerRenderer;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.MathHelper;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/**
 * v3.1: ORBITING levitating focus. The v3.0 version parked the wand at a fixed offset
 * beside the shoulder with only a self-spin - in game it read as "hanging in one spot".
 * Now the focus genuinely circles the wizard's torso:
 *
 *   translate(orbit centre) -> rotate(orbitAngle) -> translate(radius) -> item transforms
 *
 * Both angular speeds are CONSTANT (angle = ageInTicks * speed) so the motion is smooth -
 * cast intensity deliberately does not scale the angular speed (angle = t * variableSpeed
 * would jump every time intensity changes). Instead, casting pulls the orbit radius
 * inward and lifts the focus, which reads as the wand "snapping to attention".
 *
 * Coordinate space: doRenderLayer runs inside the entity render transform, i.e. after
 * {@code GlStateManager.scale(-1, -1, 1)} and {@code translate(0, -1.501, 0)} - origin is
 * ~1.5 blocks above the feet with +Y pointing DOWN and X mirrored. The
 * {@code rotate(180, 0, 0, 1)} below cancels that flip for the item so it renders upright.
 */
@SideOnly(Side.CLIENT)
public class LayerSimWizardFloatingFocus implements LayerRenderer<EntitySimWizard> {

    /** Orbit centre height: model-space Y (down-positive). 0.40 ~= chest (1.1 blocks above feet). */
    private static final float CENTER_Y = 0.40F;
    /** Orbit radius in blocks while idle. */
    private static final float RADIUS_IDLE = 0.85F;
    /** How far the radius shrinks at full cast intensity (wand pulled in close). */
    private static final float RADIUS_CAST_PULL = 0.30F;
    /** How far the focus rises (blocks) at full cast intensity. */
    private static final float CAST_LIFT = 0.25F;

    private static final float BOB_AMPLITUDE = 0.06F;
    private static final float BOB_SPEED = 0.07F;
    /** Orbit angular speed, degrees per tick (full circle ~= 7 s). */
    private static final float ORBIT_SPEED = 2.5F;
    /** Item self-spin angular speed, degrees per tick. */
    private static final float SELF_SPIN_SPEED = 9.0F;

    @Override
    public void doRenderLayer(@Nonnull EntitySimWizard entity, float limbSwing, float limbSwingAmount,
            float partialTicks, float ageInTicks, float netHeadYaw, float headPitch, float scale) {

        ItemStack stack = entity.getHeldItemMainhand();
        if (stack.isEmpty()) {
            return;
        }

        float intensity = entity.getCastFlashIntensity(partialTicks);
        float bob = MathHelper.sin(ageInTicks * BOB_SPEED) * BOB_AMPLITUDE;
        float orbitAngle = (ageInTicks * ORBIT_SPEED) % 360.0F;
        float selfSpin = (ageInTicks * SELF_SPIN_SPEED) % 360.0F;
        float radius = RADIUS_IDLE - intensity * RADIUS_CAST_PULL;

        GlStateManager.pushMatrix();
        try {
            // Orbit centre at the torso (+Y is down in this space).
            GlStateManager.translate(0.0F, CENTER_Y + bob - intensity * CAST_LIFT, 0.0F);
            // Swing around the vertical axis, then step out to the orbit ring.
            GlStateManager.rotate(orbitAngle, 0.0F, 1.0F, 0.0F);
            GlStateManager.translate(radius, 0.0F, 0.0F);
            // Undo the renderer's scale(-1,-1,1) flip so the item renders upright.
            GlStateManager.rotate(180.0F, 0.0F, 0.0F, 1.0F);
            // Levitation self-spin plus a slight tilt so it reads as "hovering".
            GlStateManager.rotate(selfSpin, 0.0F, 1.0F, 0.0F);
            GlStateManager.rotate(20.0F, 1.0F, 0.0F, 0.0F);
            GlStateManager.scale(1.1F, 1.1F, 1.1F);

            Minecraft.getMinecraft().getItemRenderer().renderItem(entity, stack,
                    ItemCameraTransforms.TransformType.GROUND);
        } finally {
            GlStateManager.popMatrix();
        }
    }

    @Override
    public boolean shouldCombineTextures() {
        return false;
    }
}
