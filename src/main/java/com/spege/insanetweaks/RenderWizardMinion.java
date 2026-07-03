package com.spege.insanetweaks;

import java.util.Objects;

import javax.annotation.Nonnull;

import com.spege.insanetweaks.entities.EntityWizardMinion;

import electroblob.wizardry.client.model.ModelWizard;
import electroblob.wizardry.client.renderer.entity.layers.LayerSummonAnimation;
import net.minecraft.client.renderer.entity.RenderBiped;
import net.minecraft.client.renderer.entity.RenderLivingBase;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.client.renderer.entity.layers.LayerBipedArmor;
import net.minecraft.client.renderer.entity.layers.LayerRenderer;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

@SideOnly(Side.CLIENT)
public class RenderWizardMinion extends RenderBiped<EntityWizardMinion> {

    private static final ResourceLocation[] TEXTURES = new ResourceLocation[EntityWizardMinion.TEXTURE_VARIANT_COUNT];

    public RenderWizardMinion(RenderManager renderManager) {
        super(renderManager, new ModelWizard(), 0.5F);

        for (int i = 0; i < TEXTURES.length; i++) {
            if (TEXTURES[i] == null) {
                TEXTURES[i] = new ResourceLocation("ebwizardry", "textures/entity/wizard/wizard_" + i + ".png");
            }
        }

        this.addLayer((LayerRenderer) new LayerBipedArmor((RenderLivingBase) this));
        this.addLayer(new LayerSummonAnimation<EntityWizardMinion>(this));
    }

    @Override
    @Nonnull
    protected ResourceLocation getEntityTexture(@Nonnull EntityWizardMinion entity) {
        int index = entity.getTextureIndex();
        if (index < 0 || index >= TEXTURES.length) {
            index = 0;
        }

        return Objects.requireNonNull(TEXTURES[index]);
    }
}
