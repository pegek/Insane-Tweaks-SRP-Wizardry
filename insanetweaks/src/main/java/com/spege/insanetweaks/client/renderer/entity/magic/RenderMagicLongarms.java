package com.spege.insanetweaks.client.renderer.entity.magic;

import com.dhanantry.scapeandrunparasites.client.renderer.entity.primitive.RenderShyco;
import com.dhanantry.scapeandrunparasites.entity.monster.primitive.EntityShyco;

import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/** SRP's longarms renderer, tinted, with the arcane glow on top. */
@SideOnly(Side.CLIENT)
public class RenderMagicLongarms extends RenderShyco implements ArcaneTextureSource<EntityShyco> {

    public RenderMagicLongarms(RenderManager manager) {
        super(manager);
        this.addLayer(new LayerArcaneGlow<EntityShyco>(this));
    }

    @Override
    protected void renderModel(EntityShyco entity, float limbSwing, float limbSwingAmount, float ageInTicks,
            float netHeadYaw, float headPitch, float scaleFactor) {
        ArcaneTint.begin();
        try {
            super.renderModel(entity, limbSwing, limbSwingAmount, ageInTicks, netHeadYaw, headPitch, scaleFactor);
        } finally {
            ArcaneTint.end();
        }
    }

    @Override
    public ResourceLocation arcaneTexture(EntityShyco entity) {
        return this.getEntityTexture(entity);
    }
}
