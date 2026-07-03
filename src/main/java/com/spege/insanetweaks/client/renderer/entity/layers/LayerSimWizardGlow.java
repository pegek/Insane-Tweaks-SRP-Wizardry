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

    /** Glow tint (violet/magenta, matches the recolored texture + particle palette). */
    private static final float GLOW_R = 0.58F;
    private static final float GLOW_G = 0.18F;
    private static final float GLOW_B = 0.90F;
    private static final float ALPHA_IDLE = 0.10F;
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

        float alpha = ALPHA_IDLE + ALPHA_CAST_BONUS * entity.getCastFlashIntensity(partialTicks);

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
            GlStateManager.color(GLOW_R, GLOW_G, GLOW_B, alpha);

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
