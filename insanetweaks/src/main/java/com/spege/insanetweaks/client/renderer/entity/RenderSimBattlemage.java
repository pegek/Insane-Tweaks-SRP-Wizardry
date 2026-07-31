package com.spege.insanetweaks.client.renderer.entity;

import javax.annotation.Nonnull;

import com.spege.insanetweaks.client.renderer.entity.layers.LayerSimWizardGlow;
import com.spege.insanetweaks.entities.EntitySimBattlemage;

import net.minecraft.client.model.ModelPlayer;
import net.minecraft.client.renderer.entity.RenderBiped;
import net.minecraft.client.renderer.entity.RenderLivingBase;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.client.renderer.entity.layers.LayerBipedArmor;
import net.minecraft.client.renderer.entity.layers.LayerRenderer;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/**
 * Renderer for the Assimilated Battlemage. Uses vanilla {@link ModelPlayer} rather than SRP's
 * non-biped {@code ModelInfHuman}, which is precisely why the battlemage is its own entity class.
 *
 * <h3>Why NOT ASC's {@code ModelClassWizard}</h3>
 * It was the first choice, and it renders garbage for our texture. That model extends
 * {@code ModelBiped} through the <em>no-arg</em> constructor, so it is a <b>64x32</b> model, and it
 * never replaces the vanilla biped parts. Its per-part {@code setTextureSize(64, 64)} calls are
 * dead code - Techne emits them <em>after</em> {@code addBox}, which has already baked the UVs from
 * the ModelBase dimensions. ASC's own {@code class_wizard/battlemage_0.png} is 64x32, confirming it.
 * Worse, its robe/hood/beard geometry sits at texture offsets like {@code (0, 51)}, which wrap past
 * a 32px height and sample the leg rows, and it hides {@code bipedHeadwear} outright. A plain
 * Minecraft skin can therefore never fill that model correctly at any size.
 *
 * <p>{@link ModelPlayer} is the model a 64x64 player skin is actually authored for: it renders the
 * hat, jacket, sleeve and trouser overlay layers itself, so the supplied skin appears exactly as
 * drawn. Built with {@code smallArms = false} to match the classic 4px-arm layout. This keeps the
 * v3.0 "stop fighting the model" lesson intact - we pick the model that fits the texture rather
 * than bending a texture onto foreign UVs. Tier identity is carried by the shared glow layer.
 *
 * <p>Unlike sim_wizard there is NO orbiting focus layer here: {@code RenderBiped} already adds a
 * {@code LayerHeldItem}, so a levitating copy of the same wand just duplicated it.
 */
@SideOnly(Side.CLIENT)
public class RenderSimBattlemage extends RenderBiped<EntitySimBattlemage> {

    private static final ResourceLocation TEXTURE =
            new ResourceLocation(com.spege.insanetweaks.InsaneTweaksMod.MODID,
                    "textures/entity/sim_battlemage.png");

    @SuppressWarnings({ "unchecked", "rawtypes" })
    public RenderSimBattlemage(RenderManager renderManager) {
        super(renderManager, new ModelPlayer(0.0F, false), 0.5F);
        this.addLayer((LayerRenderer) new LayerBipedArmor((RenderLivingBase) this));
        // The glow layer is typed on the parent entity, so it applies unchanged.
        this.addLayer((LayerRenderer) new LayerSimWizardGlow(this.mainModel, TEXTURE));
    }

    @Override
    @Nonnull
    protected ResourceLocation getEntityTexture(@Nonnull EntitySimBattlemage entity) {
        return TEXTURE;
    }
}
