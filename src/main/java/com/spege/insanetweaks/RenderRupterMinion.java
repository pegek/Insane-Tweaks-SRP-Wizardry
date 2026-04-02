package com.spege.insanetweaks;

import java.util.Objects;

import javax.annotation.Nonnull;

import com.spege.insanetweaks.client.model.entity.ModelRupterMinion;
import com.spege.insanetweaks.entities.EntityRupterMinion;

import electroblob.wizardry.client.renderer.entity.layers.LayerSummonAnimation;
import net.minecraft.client.renderer.entity.RenderLiving;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

@SideOnly(Side.CLIENT)
public class RenderRupterMinion extends RenderLiving<EntityRupterMinion> {

    private static final ResourceLocation TEXTURE = new ResourceLocation("srparasites:textures/entity/monster/mudo.png");

    public RenderRupterMinion(RenderManager manager) {
        super(manager, new ModelRupterMinion(), 0.5F);
        this.addLayer(new LayerSummonAnimation<EntityRupterMinion>(this));
    }

    @Override
    @Nonnull
    protected ResourceLocation getEntityTexture(@Nonnull EntityRupterMinion entity) {
        return Objects.requireNonNull(TEXTURE);
    }
}
