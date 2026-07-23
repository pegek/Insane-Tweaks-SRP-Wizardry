package com.spege.insanetweaks;

import com.spege.insanetweaks.client.model.entity.ModelPrimitiveYelloweyeMinion;
import com.spege.insanetweaks.entities.EntityPrimitiveYelloweyeMinion;

import electroblob.wizardry.client.renderer.entity.layers.LayerSummonAnimation;
import net.minecraft.client.renderer.entity.RenderLiving;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import javax.annotation.Nonnull;
import java.util.Objects;

@SideOnly(Side.CLIENT)
public class RenderPrimitiveYelloweyeMinion extends RenderLiving<EntityPrimitiveYelloweyeMinion> {

    private static final ResourceLocation TEXTURE = new ResourceLocation("srparasites:textures/entity/monster/emana.png");

    public RenderPrimitiveYelloweyeMinion(RenderManager manager) {
        super(manager, new ModelPrimitiveYelloweyeMinion(), 0.2F);
        this.addLayer(new LayerSummonAnimation<EntityPrimitiveYelloweyeMinion>(this));
    }

    @Override
    @Nonnull
    protected ResourceLocation getEntityTexture(@Nonnull EntityPrimitiveYelloweyeMinion entity) {
        return Objects.requireNonNull(TEXTURE);
    }
}
