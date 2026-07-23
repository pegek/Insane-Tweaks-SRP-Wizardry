package com.spege.insanetweaks.client.renderer.entity;

import javax.annotation.Nonnull;

import com.spege.insanetweaks.entities.EntityCorruptedSapling;

import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.entity.RenderLiving;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

@SideOnly(Side.CLIENT)
public class RenderCorruptedSapling extends RenderLiving<EntityCorruptedSapling> {

    private static final ResourceLocation TEXTURE =
            new ResourceLocation("insanetweaks", "textures/entity/corrupted_sapling.png");

    public RenderCorruptedSapling(RenderManager manager) {
        super(manager, new ModelCorruptedSapling(), 0.3f);
    }

    @Override
    protected void preRenderCallback(@Nonnull EntityCorruptedSapling entity, float partialTickTime) {
        // Stage 0 = small sprout, MAX_STAGE = full size.
        float s = 0.35f + 0.65f * (entity.getStage() / (float) EntityCorruptedSapling.MAX_STAGE);
        GlStateManager.scale(s, s, s);
    }

    @Override
    @Nonnull
    protected ResourceLocation getEntityTexture(@Nonnull EntityCorruptedSapling entity) {
        return TEXTURE;
    }
}
