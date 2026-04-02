package com.spege.insanetweaks;

import com.dhanantry.scapeandrunparasites.client.model.entity.ModelProjectileHomming;
import com.spege.insanetweaks.entities.projectile.EntityYelloweyeNadeProjectile;

import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.entity.Render;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.MathHelper;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

@SideOnly(Side.CLIENT)
public class RenderYelloweyeNadeProjectile extends Render<EntityYelloweyeNadeProjectile> {

    private static final ResourceLocation TEXTURE = new ResourceLocation("srparasites:textures/entity/projectile/nade.png");
    private static final float MODEL_SCALE = 0.03125F;

    private final ModelProjectileHomming model = new ModelProjectileHomming();

    public RenderYelloweyeNadeProjectile(RenderManager manager) {
        super(manager);
        this.shadowSize = 0.0F;
    }

    @Override
    public void doRender(EntityYelloweyeNadeProjectile entity, double x, double y, double z, float entityYaw,
            float partialTicks) {
        GlStateManager.pushMatrix();
        GlStateManager.translate((float) x, (float) y + 0.15F, (float) z);

        float age = entity.ticksExisted + partialTicks;
        float yaw = this.rotLerp(entity.prevRotationYaw, entity.rotationYaw, partialTicks);
        float pitch = entity.prevRotationPitch + (entity.rotationPitch - entity.prevRotationPitch) * partialTicks;
        GlStateManager.rotate(MathHelper.sin(age * 0.1F) * 180.0F, 0.0F, 1.0F, 0.0F);
        GlStateManager.rotate(MathHelper.cos(age * 0.1F) * 180.0F, 1.0F, 0.0F, 0.0F);
        GlStateManager.rotate(MathHelper.sin(age * 0.15F) * 360.0F, 0.0F, 0.0F, 1.0F);
        GlStateManager.enableRescaleNormal();
        GlStateManager.scale(-1.0F, -1.0F, 1.0F);
        this.bindEntityTexture(entity);
        this.model.render(entity, 0.0F, 0.0F, 0.0F, yaw, pitch, MODEL_SCALE);
        GlStateManager.enableBlend();
        GlStateManager.color(1.0F, 1.0F, 1.0F, 0.5F);
        GlStateManager.scale(1.5F, 1.5F, 1.5F);
        this.model.render(entity, 0.0F, 0.0F, 0.0F, yaw, pitch, MODEL_SCALE);
        GlStateManager.disableBlend();
        GlStateManager.disableRescaleNormal();
        GlStateManager.popMatrix();

        super.doRender(entity, x, y, z, entityYaw, partialTicks);
    }

    @Override
    protected ResourceLocation getEntityTexture(EntityYelloweyeNadeProjectile entity) {
        return TEXTURE;
    }

    private float rotLerp(float previous, float current, float partialTicks) {
        float delta;
        for (delta = current - previous; delta < -180.0F; delta += 360.0F) {
        }
        while (delta >= 180.0F) {
            delta -= 360.0F;
        }
        return previous + partialTicks * delta;
    }
}
