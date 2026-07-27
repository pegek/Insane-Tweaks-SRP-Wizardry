package com.spege.insanetweaks.client.renderer.entity.layers;

import javax.annotation.Nonnull;

import com.spege.insanetweaks.entities.EntitySimWizard;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.ModelBase;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.entity.layers.LayerRenderer;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/**
 * v3.0: emissive "arcane possession" overlay. Re-renders the wizard's own model as a
 * slightly inflated, fullbright, additive-blended violet shell. Subtle while idle,
 * flaring up with cast intensity - the player reads "magic-charged parasite" at a glance
 * even in darkness.
 *
 * Uses the SAME model instance as the main render pass, so all bone angles are already
 * set for this frame - no re-animation logic, no drift.
 */
@SideOnly(Side.CLIENT)
public class LayerSimWizardGlow implements LayerRenderer<EntitySimWizard> {

    /**
     * Glow tint per tier ordinal (NOVICE, ADEPT, MASTER): dim indigo, the established violet, hot
     * magenta. Recolouring this shell is the cheapest possible tier tell - no new texture, no new
     * geometry, and therefore none of the UV-garbage that every previous attempt at visual
     * distinction produced on the SRP 64x55 atlas (the v3.0 "stop fighting the SRP model" lesson).
     */
    private static final float[][] GLOW_RGB = {
            { 0.42F, 0.22F, 0.72F },
            { 0.58F, 0.18F, 0.90F },
            { 0.85F, 0.10F, 0.55F }
    };
    private static final float[] ALPHA_IDLE_BY_TIER = { 0.06F, 0.10F, 0.16F };
    /** Fallback tint when tier visuals are switched off - the pre-tier appearance. */
    private static final float[] GLOW_DEFAULT = { 0.58F, 0.18F, 0.90F };
    private static final float ALPHA_IDLE_DEFAULT = 0.10F;
    private static final float ALPHA_CAST_BONUS = 0.30F;
    /** Shell inflation so the overlay does not z-fight the base model. */
    private static final float SHELL_SCALE = 1.02F;

    private final ModelBase model;
    private final ResourceLocation texture;

    public LayerSimWizardGlow(ModelBase sharedMainModel, ResourceLocation texture) {
        this.model = sharedMainModel;
        this.texture = texture;
    }

    @Override
    public void doRenderLayer(@Nonnull EntitySimWizard entity, float limbSwing, float limbSwingAmount,
            float partialTicks, float ageInTicks, float netHeadYaw, float headPitch, float scale) {

        // Index defensively: a future fourth tier would otherwise crash the render thread here.
        boolean tiered = com.spege.insanetweaks.config.ModConfig.entities.assimilatedWizard.tiers.enableTierVisuals;
        int tier = Math.max(0, Math.min(entity.getTier().ordinal(), GLOW_RGB.length - 1));
        float[] rgb = tiered ? GLOW_RGB[tier] : GLOW_DEFAULT;
        float idleAlpha = tiered ? ALPHA_IDLE_BY_TIER[tier] : ALPHA_IDLE_DEFAULT;

        float alpha = idleAlpha + ALPHA_CAST_BONUS * entity.getCastFlashIntensity(partialTicks);

        Minecraft.getMinecraft().getTextureManager().bindTexture(this.texture);

        GlStateManager.pushMatrix();
        try {
            GlStateManager.enableBlend();
            // Additive blend = light-emitting look.
            GlStateManager.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);
            GlStateManager.disableLighting();
            GlStateManager.depthMask(false);
            // Fullbright lightmap so the glow ignores world darkness.
            OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, 240.0F, 240.0F);
            GlStateManager.color(rgb[0], rgb[1], rgb[2], alpha);

            GlStateManager.scale(SHELL_SCALE, SHELL_SCALE, SHELL_SCALE);
            this.model.render(entity, limbSwing, limbSwingAmount, ageInTicks, netHeadYaw, headPitch, scale);
        } finally {
            // Restore render state.
            GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
            GlStateManager.depthMask(true);
            GlStateManager.disableBlend();
            GlStateManager.enableLighting();
            int packed = entity.getBrightnessForRender();
            OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit,
                    (float) (packed % 65536), (float) (packed / 65536));
            GlStateManager.popMatrix();
        }
    }

    @Override
    public boolean shouldCombineTextures() {
        return false;
    }
}
