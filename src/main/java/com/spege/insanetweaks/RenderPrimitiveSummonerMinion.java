package com.spege.insanetweaks;

import java.util.Objects;

import javax.annotation.Nonnull;

import com.spege.insanetweaks.client.model.entity.ModelPrimitiveSummonerMinion;
import com.spege.insanetweaks.entities.EntityPrimitiveSummonerMinion;

import electroblob.wizardry.client.renderer.entity.layers.LayerSummonAnimation;
import net.minecraft.client.renderer.entity.RenderLiving;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

@SideOnly(Side.CLIENT)
public class RenderPrimitiveSummonerMinion extends RenderLiving<EntityPrimitiveSummonerMinion> {

    private static final ResourceLocation TEXTURE = new ResourceLocation("srparasites:textures/entity/monster/canra.png");

    public RenderPrimitiveSummonerMinion(RenderManager manager) {
        super(manager, new ModelPrimitiveSummonerMinion(), 1.0F);
        this.addLayer(new LayerSummonAnimation<EntityPrimitiveSummonerMinion>(this));
    }

    @Override
    @Nonnull
    protected ResourceLocation getEntityTexture(@Nonnull EntityPrimitiveSummonerMinion entity) {
        return Objects.requireNonNull(TEXTURE);
    }
}
