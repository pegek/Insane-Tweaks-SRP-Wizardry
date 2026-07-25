package com.spege.insanetweaks.client.renderer.entity;

import javax.annotation.Nonnull;

import com.dhanantry.scapeandrunparasites.client.model.entity.misc.ModelBombOmboo;
import com.spege.insanetweaks.entities.projectile.EntityBomberBomb;

import net.minecraft.client.model.ModelBase;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.entity.Render;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/**
 * Renders the summoned bomber's bomb with SRP's stock Omboo bomb model.
 * {@code ModelBombOmboo} performs no entity casts, so it can be reused as-is.
 */
@SideOnly(Side.CLIENT)
@SuppressWarnings("null")
public class RenderBomberBomb extends Render<EntityBomberBomb> {

    public static final ResourceLocation TEXTURE = new ResourceLocation(
            "srparasites:textures/entity/monster/bombo.png");

    private final ModelBase model = new ModelBombOmboo();

    public RenderBomberBomb(RenderManager manager) {
        super(manager);
        this.shadowSize = 0.3F;
    }

    @Override
    public void doRender(@Nonnull EntityBomberBomb entity, double x, double y, double z, float entityYaw,
            float partialTicks) {
        GlStateManager.pushMatrix();
        GlStateManager.translate((float) x, (float) y + 1.5F, (float) z);
        this.bindEntityTexture(entity);
        GlStateManager.rotate(180.0F, 0.0F, 0.0F, 1.0F);
        this.model.render(entity, 0.0F, 0.0F, (float) entity.ticksExisted, entity.rotationYaw, entity.rotationPitch,
                0.0625F);
        GlStateManager.popMatrix();

        super.doRender(entity, x, y, z, entityYaw, partialTicks);
    }

    @Override
    protected ResourceLocation getEntityTexture(@Nonnull EntityBomberBomb entity) {
        return TEXTURE;
    }
}
