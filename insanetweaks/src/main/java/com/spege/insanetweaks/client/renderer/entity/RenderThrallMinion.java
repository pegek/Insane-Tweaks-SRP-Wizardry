package com.spege.insanetweaks.client.renderer.entity;

import com.spege.insanetweaks.client.model.entity.ModelThrallMinion;
import com.spege.insanetweaks.entities.EntityThrallMinion;

import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.entity.RenderLiving;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.client.renderer.entity.layers.LayerHeldItem;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

@SideOnly(Side.CLIENT)
@SuppressWarnings("null")
public class RenderThrallMinion extends RenderLiving<EntityThrallMinion> {

    private static final ResourceLocation TEXTURE =
            new ResourceLocation("insanetweaks", "textures/entity/thrall_minion.png");

    private final ModelThrallMinion thrallModel;

    public RenderThrallMinion(RenderManager manager) {
        super(manager, new ModelThrallMinion(), 0.5F);
        this.thrallModel = (ModelThrallMinion) this.mainModel;
        this.addLayer(new LayerHeldItem(this));
    }

    @Override
    protected void preRenderCallback(EntityThrallMinion thrall, float partialTicks) {
        GlStateManager.scale(1.0F, 1.0F, 1.0F);
        // Update carry animation: raise arms when inventory is not empty
        this.thrallModel.carryAnimation = thrall.getThrallInventory().containsItems();
    }

    @Override
    protected ResourceLocation getEntityTexture(EntityThrallMinion entity) {
        return TEXTURE;
    }
}
