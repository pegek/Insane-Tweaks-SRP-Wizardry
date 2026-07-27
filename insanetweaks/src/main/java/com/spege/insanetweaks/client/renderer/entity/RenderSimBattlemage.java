package com.spege.insanetweaks.client.renderer.entity;

import javax.annotation.Nonnull;

import com.spege.insanetweaks.client.renderer.entity.layers.LayerSimWizardFloatingFocus;
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
 * <p>The texture is an existing ASC battlemage skin. Deliberately NOT a new UV layout: every past
 * attempt to invent geometry or atlas regions for these entities ended in garbage pixels (the v3.0
 * "stop fighting the model" lesson). Tier identity is carried by the shared glow layer instead.
 */
@SideOnly(Side.CLIENT)
public class RenderSimBattlemage extends RenderBiped<EntitySimBattlemage> {

    private static final ResourceLocation TEXTURE =
            new ResourceLocation("ancientspellcraft", "textures/entity/class_wizard/battlemage_3.png");

    @SuppressWarnings({ "unchecked", "rawtypes" })
    public RenderSimBattlemage(RenderManager renderManager) {
        super(renderManager, new ModelClassWizard(), 0.5F);
        this.addLayer((LayerRenderer) new LayerBipedArmor((RenderLivingBase) this));
        // Both sim_wizard layers are typed on the parent entity, so they apply unchanged.
        this.addLayer((LayerRenderer) new LayerSimWizardGlow(this.mainModel, TEXTURE));
        this.addLayer((LayerRenderer) new LayerSimWizardFloatingFocus());
    }

    @Override
    @Nonnull
    protected ResourceLocation getEntityTexture(@Nonnull EntitySimBattlemage entity) {
        return TEXTURE;
    }
}
