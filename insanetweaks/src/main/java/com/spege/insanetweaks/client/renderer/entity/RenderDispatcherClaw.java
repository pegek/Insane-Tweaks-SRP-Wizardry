package com.spege.insanetweaks.client.renderer.entity;

import javax.annotation.Nonnull;

import com.spege.insanetweaks.client.model.entity.ModelDispatcherClaw;
import com.spege.insanetweaks.entities.EntityDispatcherClaw;

import net.minecraft.client.renderer.entity.RenderLiving;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/**
 * Renders the Dispatcher Grasp claw with SRP's Nak model/texture.
 *
 * <p>No {@code LayerSummonAnimation}: that layer's summon-fade is keyed off
 * {@code ISummonedCreature} lifetime data, and the claw deliberately is not a
 * summoned creature (it must not count against the caster's minion budget). No
 * shadow either — the claw is anchored on the victim, and a shadow blob under a
 * mid-air grab reads as a bug.
 */
@SideOnly(Side.CLIENT)
@SuppressWarnings("null")
public class RenderDispatcherClaw extends RenderLiving<EntityDispatcherClaw> {

    public static final ResourceLocation TEXTURE = new ResourceLocation(
            "srparasites:textures/entity/monster/nak.png");

    public RenderDispatcherClaw(RenderManager manager) {
        super(manager, new ModelDispatcherClaw(), 0.0F);
    }

    @Override
    protected ResourceLocation getEntityTexture(@Nonnull EntityDispatcherClaw entity) {
        return TEXTURE;
    }
}
