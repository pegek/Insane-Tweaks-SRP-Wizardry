package com.spege.insanetweaks.client.renderer.entity;

import javax.annotation.Nonnull;

import com.spege.insanetweaks.client.model.entity.ModelLightBomberMinion;
import com.spege.insanetweaks.entities.EntityLightBomberMinion;

import electroblob.wizardry.client.renderer.entity.layers.LayerSummonAnimation;
import net.minecraft.client.renderer.entity.RenderLiving;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

@SideOnly(Side.CLIENT)
@SuppressWarnings("null")
public class RenderLightBomberMinion extends RenderLiving<EntityLightBomberMinion> {

    public static final ResourceLocation TEXTURE = new ResourceLocation(
            "srparasites:textures/entity/monster/omboo.png");

    public RenderLightBomberMinion(RenderManager manager) {
        super(manager, new ModelLightBomberMinion(), 0.5F);
        this.addLayer(new LayerSummonAnimation<EntityLightBomberMinion>(this));
        // Reuses the SRP texture/model but keeps the summon-safe presentation
        // instead of pulling in SRP's renderer stack.
    }

    @Override
    protected ResourceLocation getEntityTexture(@Nonnull EntityLightBomberMinion entity) {
        return TEXTURE;
    }
}
