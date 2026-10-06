package com.spege.insanetweaks.client.renderer.entity.magic;

import com.dhanantry.scapeandrunparasites.client.renderer.entity.primitive.RenderCanra;
import com.dhanantry.scapeandrunparasites.entity.monster.primitive.EntityCanra;

import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/** SRP's summoner renderer, tinted, with the arcane glow on top. */
@SideOnly(Side.CLIENT)
public class RenderMagicSummoner extends RenderCanra implements ArcaneTextureSource<EntityCanra> {

    public RenderMagicSummoner(RenderManager manager) {
        super(manager);
        this.addLayer(new LayerArcaneGlow<EntityCanra>(this));
    }

    @Override
    protected void renderModel(EntityCanra entity, float limbSwing, float limbSwingAmount, float ageInTicks,
            float netHeadYaw, float headPitch, float scaleFactor) {
        ArcaneTint.begin();
        try {
            super.renderModel(entity, limbSwing, limbSwingAmount, ageInTicks, netHeadYaw, headPitch, scaleFactor);
        } finally {
            ArcaneTint.end();
        }
    }

    @Override
    public ResourceLocation arcaneTexture(EntityCanra entity) {
        return this.getEntityTexture(entity);
    }
}
