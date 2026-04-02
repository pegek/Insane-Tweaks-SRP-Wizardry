package com.spege.insanetweaks;

import com.dhanantry.scapeandrunparasites.client.model.entity.misc.ModelNade;
import com.spege.insanetweaks.entities.projectile.EntityYelloweyeNade;

import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.entity.Render;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.MathHelper;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

@SideOnly(Side.CLIENT)
public class RenderYelloweyeNade extends Render<EntityYelloweyeNade> {

    private static final ResourceLocation TEXTURE = new ResourceLocation("srparasites:textures/entity/monster/nade.png");
    private static final float MODEL_SCALE = 0.03125F;

    private final ModelNade model = new ModelNade();

    public RenderYelloweyeNade(RenderManager manager) {
        super(manager);
        this.shadowSize = 0.0F;
    }

    @Override
    public void doRender(EntityYelloweyeNade entity, double x, double y, double z, float entityYaw, float partialTicks) {
        GlStateManager.pushMatrix();
        GlStateManager.translate((float) x, (float) y, (float) z);
        GlStateManager.enableRescaleNormal();
        GlStateManager.scale(-1.0F, -1.0F, 1.0F);
        GlStateManager.rotate(180.0F - entityYaw, 0.0F, 1.0F, 0.0F);

        float flash = entity.getSelfeFlashIntensity(partialTicks);
        float pulse = 1.0F + MathHelper.sin(flash * 100.0F) * flash * 0.01F;
        flash = MathHelper.clamp(flash, 0.0F, 1.1F);
        flash *= flash;
        flash *= flash;

        float horizontalBoost = (1.0F + flash * 0.4F) * pulse;
        float verticalBoost = (1.0F + flash * 0.1F) / pulse;
        float scaleX = entity.width * 1.4F + horizontalBoost;
        float scaleY = entity.height * 1.25F + verticalBoost;

        GlStateManager.scale(scaleX, scaleY, scaleX);
        GlStateManager.translate(0.0F, -1.501F, 0.0F);
        this.bindEntityTexture(entity);
        this.model.render(entity, 0.0F, 0.0F, entity.ticksExisted + partialTicks, 0.0F, 0.0F, MODEL_SCALE);
        GlStateManager.disableRescaleNormal();
        GlStateManager.popMatrix();

        super.doRender(entity, x, y, z, entityYaw, partialTicks);
    }

    @Override
    protected ResourceLocation getEntityTexture(EntityYelloweyeNade entity) {
        return TEXTURE;
    }
}
