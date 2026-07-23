package com.spege.insanetweaks;

import javax.annotation.Nonnull;

import com.spege.insanetweaks.entities.EntitySentinel;
import com.windanesz.ancientspellcraft.client.model.ModelClassWizard;

import net.minecraft.client.renderer.entity.RenderBiped;
import net.minecraft.client.renderer.entity.RenderLivingBase;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.client.renderer.entity.layers.LayerBipedArmor;
import net.minecraft.client.renderer.entity.layers.LayerRenderer;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

@SideOnly(Side.CLIENT)
public class RenderSentinel extends RenderBiped<EntitySentinel> {

    private static final ResourceLocation[] TEXTURES = new ResourceLocation[EntitySentinel.TEXTURE_VARIANT_COUNT];

    public RenderSentinel(RenderManager renderManager) {
        super(renderManager, new ModelClassWizard(), 0.5F);

        for (int i = 0; i < TEXTURES.length; i++) {
            if (TEXTURES[i] == null) {
                TEXTURES[i] = new ResourceLocation("ancientspellcraft",
                        "textures/entity/class_wizard/battlemage_" + i + ".png");
            }
        }

        this.addLayer((LayerRenderer) new LayerBipedArmor((RenderLivingBase) this));
    }

    @Override
    @Nonnull
    protected ResourceLocation getEntityTexture(@Nonnull EntitySentinel entity) {
        int index = entity.getTextureIndex();
        if (index < 0 || index >= TEXTURES.length) {
            index = 0;
        }

        return TEXTURES[index];
    }
}
