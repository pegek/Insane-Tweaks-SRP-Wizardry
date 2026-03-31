package com.spege.insanetweaks;

import electroblob.wizardry.client.renderer.entity.layers.LayerSummonAnimation;
import com.spege.insanetweaks.client.model.entity.ModelFerCowMinion;
import com.spege.insanetweaks.entities.EntityFerCowMinion;
import net.minecraft.client.renderer.entity.RenderLiving;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import javax.annotation.Nonnull;
import java.util.Objects;

@SideOnly(Side.CLIENT)
public class RenderFerCowMinion extends RenderLiving<EntityFerCowMinion> {

    private static final ResourceLocation TEXTURE = new ResourceLocation("srparasites:textures/entity/monster/fercow.png");

    public RenderFerCowMinion(RenderManager manager) {
        super(manager, new ModelFerCowMinion(), 0.5F);
        this.addLayer(new LayerSummonAnimation<EntityFerCowMinion>(this));
    }

    @Override
    @Nonnull
    protected ResourceLocation getEntityTexture(@Nonnull EntityFerCowMinion entity) {
        return Objects.requireNonNull(TEXTURE);
    }
}
