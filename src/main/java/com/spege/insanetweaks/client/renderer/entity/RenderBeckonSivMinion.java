package com.spege.insanetweaks.client.renderer.entity;

import com.spege.insanetweaks.client.model.entity.ModelBeckonSivMinion;
import com.spege.insanetweaks.entities.EntityBeckonSivMinion;
import electroblob.wizardry.client.renderer.entity.layers.LayerSummonAnimation;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.entity.RenderLiving;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import javax.annotation.Nonnull;

@SideOnly(Side.CLIENT)
@SuppressWarnings("null")
public class RenderBeckonSivMinion extends RenderLiving<EntityBeckonSivMinion> {

    public static final ResourceLocation TEXTURE = new ResourceLocation("srparasites:textures/entity/monster/venkrolsiv.png");
    private static final float SUMMON_SCALE = 1.02F;

    public RenderBeckonSivMinion(RenderManager manager) {
        super(manager, new ModelBeckonSivMinion(), 0.4F);
        this.addLayer(new LayerSummonAnimation<EntityBeckonSivMinion>(this));
        // We intentionally reuse the SRP texture/model, but keep the presentation
        // lightweight and summon-safe instead of forcing SRP's renderer stack.
    }

    @Override
    protected void preRenderCallback(EntityBeckonSivMinion entitylivingbaseIn, float partialTickTime) {
        GlStateManager.scale(SUMMON_SCALE, SUMMON_SCALE, SUMMON_SCALE);
    }

    @Override
    protected ResourceLocation getEntityTexture(@Nonnull EntityBeckonSivMinion entity) {
        return TEXTURE;
    }
}
