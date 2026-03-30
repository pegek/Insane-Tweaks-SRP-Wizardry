package com.spege.insanetweaks;

import electroblob.wizardry.client.renderer.entity.layers.LayerSummonAnimation;
import com.spege.insanetweaks.client.model.entity.ModelAssimilatedCowMinion;
import com.spege.insanetweaks.entities.EntityAssimilatedCowMinion;
import net.minecraft.client.renderer.entity.RenderLiving;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

@SideOnly(Side.CLIENT)
public class RenderAssimilatedCowMinion extends RenderLiving<EntityAssimilatedCowMinion> {

    private static final ResourceLocation TEXTURE = new ResourceLocation("srparasites:textures/entity/monster/cow.png");

    public RenderAssimilatedCowMinion(RenderManager manager) {
        super(manager, new ModelAssimilatedCowMinion(), 0.5F);
        this.addLayer(new LayerSummonAnimation<EntityAssimilatedCowMinion>(this));
    }

    @Override
    protected ResourceLocation getEntityTexture(EntityAssimilatedCowMinion entity) {
        return TEXTURE;
    }
}
