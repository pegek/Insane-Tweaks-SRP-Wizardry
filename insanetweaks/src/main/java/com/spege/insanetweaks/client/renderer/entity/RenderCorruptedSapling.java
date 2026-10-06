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
        super(manager, new ModelCorruptedSapling(), 0.5f);
    }

    /** Height of ModelCorruptedSapling in blocks at scale 1. */
    private static final float MODEL_HEIGHT = ModelCorruptedSapling.HEIGHT_PX / 16.0F;

    @Override
    protected void preRenderCallback(@Nonnull EntityCorruptedSapling entity, float partialTickTime) {
        // Scale the model around its feet so it is exactly as tall as the synced hitbox.
        float s = com.spege.insanetweaks.entities.sapling.SaplingStats.height(entity.getProgress()) / MODEL_HEIGHT;
        GlStateManager.scale(s, s, s);
    }

    @Override
    @Nonnull
    protected ResourceLocation getEntityTexture(@Nonnull EntityCorruptedSapling entity) {
        return TEXTURE;
    }
}
