package com.spege.insanetweaks;

import com.spege.insanetweaks.entities.projectile.EntityYelloweyeGlandProjectile;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.client.renderer.entity.RenderSnowball;
import net.minecraft.init.Items;

@SuppressWarnings("null")
public class RenderYelloweyeGlandProjectile extends RenderSnowball<EntityYelloweyeGlandProjectile> {

    private static final float VISUAL_SCALE = 1.7F;

    public RenderYelloweyeGlandProjectile(RenderManager renderManager) {
        super(renderManager, Items.SLIME_BALL, Minecraft.getMinecraft().getRenderItem());
    }

    @Override
    public void doRender(EntityYelloweyeGlandProjectile entity, double x, double y, double z, float entityYaw,
            float partialTicks) {
        GlStateManager.pushMatrix();
        GlStateManager.translate((float) x, (float) y, (float) z);
        GlStateManager.scale(VISUAL_SCALE, VISUAL_SCALE, VISUAL_SCALE);
        super.doRender(entity, 0.0D, 0.0D, 0.0D, entityYaw, partialTicks);
        GlStateManager.popMatrix();
    }
}
