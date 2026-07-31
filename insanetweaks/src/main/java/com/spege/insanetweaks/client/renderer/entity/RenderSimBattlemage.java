package com.spege.insanetweaks.client.renderer.entity;

import javax.annotation.Nonnull;

import com.spege.insanetweaks.client.renderer.entity.layers.LayerSimWizardGlow;
import com.spege.insanetweaks.entities.EntitySimBattlemage;
import com.windanesz.ancientspellcraft.client.model.ModelClassWizard;

import net.minecraft.client.renderer.entity.RenderBiped;
import net.minecraft.client.renderer.entity.RenderLivingBase;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.client.renderer.entity.layers.LayerBipedArmor;
import net.minecraft.client.renderer.entity.layers.LayerRenderer;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/**
 * Renderer for the Assimilated Battlemage. Uses ASC's biped {@code ModelClassWizard} - the same
 * proven stack the Sentinel renders with - rather than SRP's non-biped {@code ModelInfHuman}, which
 * is precisely why the battlemage is its own entity class.
 *
 * <p>The texture is a 64x64 player-layout skin, which is exactly what this biped model expects -
 * no invented geometry, no custom atlas regions (the v3.0 "stop fighting the model" lesson). Tier
 * identity is carried by the shared glow layer instead.
 *
 * <p>Unlike sim_wizard there is NO orbiting focus layer here: {@code RenderBiped} already renders
 * the held item in the entity's hand, so a levitating copy of the same wand just duplicated it.
 */
@SideOnly(Side.CLIENT)
public class RenderSimBattlemage extends RenderBiped<EntitySimBattlemage> {

    private static final ResourceLocation TEXTURE =
            new ResourceLocation(com.spege.insanetweaks.InsaneTweaksMod.MODID,
                    "textures/entity/sim_battlemage.png");

    @SuppressWarnings({ "unchecked", "rawtypes" })
    public RenderSimBattlemage(RenderManager renderManager) {
        super(renderManager, new ModelClassWizard(), 0.5F);
        this.addLayer((LayerRenderer) new LayerBipedArmor((RenderLivingBase) this));
        // The glow layer is typed on the parent entity, so it applies unchanged.
        this.addLayer((LayerRenderer) new LayerSimWizardGlow(this.mainModel, TEXTURE));
    }

    @Override
    @Nonnull
    protected ResourceLocation getEntityTexture(@Nonnull EntitySimBattlemage entity) {
        return TEXTURE;
    }
}
