package com.spege.insanetweaks.client.renderer.entity.magic;

import com.dhanantry.scapeandrunparasites.client.renderer.entity.primitive.RenderHull;
import com.dhanantry.scapeandrunparasites.entity.monster.primitive.EntityHull;

import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/** SRP's manducater renderer, tinted, with the arcane glow on top. */
@SideOnly(Side.CLIENT)
public class RenderMagicManducater extends RenderHull implements ArcaneTextureSource<EntityHull> {

    public RenderMagicManducater(RenderManager manager) {
        super(manager);
        this.addLayer(new LayerArcaneGlow<EntityHull>(this));
    }

    @Override
    protected void renderModel(EntityHull entity, float limbSwing, float limbSwingAmount, float ageInTicks,
            float netHeadYaw, float headPitch, float scaleFactor) {
        ArcaneTint.begin();
        try {
            super.renderModel(entity, limbSwing, limbSwingAmount, ageInTicks, netHeadYaw, headPitch, scaleFactor);
        } finally {
            ArcaneTint.end();
        }
    }

    @Override
    public ResourceLocation arcaneTexture(EntityHull entity) {
        return this.getEntityTexture(entity);
    }
}
