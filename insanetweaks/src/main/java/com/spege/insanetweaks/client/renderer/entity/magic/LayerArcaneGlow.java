package com.spege.insanetweaks.client.renderer.entity.magic;

import javax.annotation.Nonnull;

import com.spege.insanetweaks.entities.magic.IMagicParasite;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.ModelBase;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.entity.RenderLivingBase;
import net.minecraft.client.renderer.entity.layers.LayerRenderer;
import net.minecraft.entity.EntityLivingBase;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/**
 * Fullbright, additive, slightly inflated violet re-render of the entity's own model and skin (the
 * LayerSimWizardGlow technique). Faint while idle, pulsing during a telegraph, bright while
 * warded. Uses the renderer's main model, so the bones are already posed for this frame.
 */
@SideOnly(Side.CLIENT)
public class LayerArcaneGlow<T extends EntityLivingBase> implements LayerRenderer<T> {

    private static final float[] GLOW_RGB = { 0.58F, 0.18F, 0.90F };
    private static final float ALPHA_IDLE = 0.10F;
    private static final float ALPHA_WARD = 0.80F;
    private static final float SHELL_SCALE = 1.03F;

    private final RenderLivingBase<T> renderer;
    private final ArcaneTextureSource<T> textures;

    public <R extends RenderLivingBase<T> & ArcaneTextureSource<T>> LayerArcaneGlow(R renderer) {
        this.renderer = renderer;
        this.textures = renderer;
    }

    @Override
    public void doRenderLayer(@Nonnull T entity, float limbSwing, float limbSwingAmount, float partialTicks,
            float ageInTicks, float netHeadYaw, float headPitch, float scale) {
        if (!(entity instanceof IMagicParasite) || entity.isInvisible()) {
            return;
        }
        byte state = ((IMagicParasite) entity).getArcaneState();
        float alpha;
        if (state == IMagicParasite.STATE_WARD) {
            alpha = ALPHA_WARD;
        } else if (state == IMagicParasite.STATE_TELEGRAPH) {
            alpha = 0.35F + 0.25F * (float) Math.sin(ageInTicks * 0.6F);
        } else {
            alpha = ALPHA_IDLE;
        }
        ModelBase model = this.renderer.getMainModel();
        Minecraft.getMinecraft().getTextureManager().bindTexture(this.textures.arcaneTexture(entity));

        GlStateManager.pushMatrix();
        try {
            GlStateManager.enableBlend();
            GlStateManager.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);
            GlStateManager.disableLighting();
            GlStateManager.depthMask(false);
            OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, 240.0F, 240.0F);
            GlStateManager.color(GLOW_RGB[0], GLOW_RGB[1], GLOW_RGB[2], alpha);
            GlStateManager.scale(SHELL_SCALE, SHELL_SCALE, SHELL_SCALE);
            model.render(entity, limbSwing, limbSwingAmount, ageInTicks, netHeadYaw, headPitch, scale);
        } finally {
            GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
            GlStateManager.depthMask(true);
            GlStateManager.disableBlend();
            GlStateManager.enableLighting();
            int packed = entity.getBrightnessForRender();
            OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, (float) (packed % 65536),
                    (float) (packed / 65536));
            GlStateManager.popMatrix();
        }
    }

    @Override
    public boolean shouldCombineTextures() {
        return false;
    }
}
