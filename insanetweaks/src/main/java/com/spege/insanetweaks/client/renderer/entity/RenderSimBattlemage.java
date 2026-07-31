package com.spege.insanetweaks.client.renderer.entity;

import javax.annotation.Nonnull;

import com.spege.insanetweaks.client.renderer.entity.layers.LayerSimWizardGlow;
import com.spege.insanetweaks.entities.EntitySimBattlemage;

import net.minecraft.client.model.ModelBiped;
import net.minecraft.client.model.ModelPlayer;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.entity.RenderBiped;
import net.minecraft.client.renderer.entity.RenderLivingBase;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.client.renderer.entity.layers.LayerBipedArmor;
import net.minecraft.client.renderer.entity.layers.LayerRenderer;
import net.minecraft.item.EnumAction;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumHand;
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

    /**
     * Pose the arms around whatever the battlemage is holding, every frame.
     *
     * <p>{@code ModelBiped.ArmPose} defaults to {@code EMPTY}, and nothing in {@code RenderBiped}
     * ever changes it - vanilla mobs set it in their OWN renderers ({@code RenderSkeleton} and
     * friends all do this). Left at EMPTY, both arms simply hang and swing with the walk cycle, so
     * a battlemage carrying a spellblade and a shield reads as stiff: the blade dangles at its hip
     * and the shield never comes up, no matter what the AI is doing.
     *
     * <p>{@code BLOCK} additionally gives the shield AI a visible tell - the raised guard is how a
     * player can see the block happen at all.
     */
    @Override
    public void doRender(@Nonnull EntitySimBattlemage entity, double x, double y, double z,
            float entityYaw, float partialTicks) {
        ModelPlayer model = (ModelPlayer) this.mainModel;
        model.rightArmPose = armPose(entity, EnumHand.MAIN_HAND);
        model.leftArmPose = armPose(entity, EnumHand.OFF_HAND);
        super.doRender(entity, x, y, z, entityYaw, partialTicks);
    }

    private static ModelBiped.ArmPose armPose(EntitySimBattlemage entity, EnumHand hand) {
        ItemStack stack = entity.getHeldItem(hand);
        if (stack.isEmpty()) {
            return ModelBiped.ArmPose.EMPTY;
        }
        if (entity.isHandActive() && entity.getActiveHand() == hand
                && stack.getItemUseAction() == EnumAction.BLOCK) {
            return ModelBiped.ArmPose.BLOCK;
        }
        return ModelBiped.ArmPose.ITEM;
    }

    // ------------------------------------------------------------------------
    // Death
    // ------------------------------------------------------------------------
    //
    // The SERVER-side death sequence needs no code at all: EntityInfHuman's constructor sets
    // canModRender = 1, so EntityParasiteBase.onDeathUpdate takes the parasite branch
    // (setSelfeState -> setParasiteStatus(6) -> dyingBurst), whose fuse eventually calls
    // selfExplode() -> spawnGore(), which places the gore block and spawns the EntityRemain keyed
    // by EntityList.getKey(this). The battlemage inherits all of it through EntitySimWizard, so it
    // already leaves remains exactly like any other SRP parasite.
    //
    // What it does NOT inherit is the VISUAL: SRP's melt is implemented in ModelSRP, which reads
    // the entity's selfeState, and we deliberately render with vanilla ModelPlayer so a plain
    // Minecraft skin maps correctly. The two below reproduce the read - collapse rather than
    // topple - without depending on SRP's model internals.

    /**
     * SRP parasites sink and collapse; they do not fall over like a vanilla mob. Returning 0
     * suppresses {@code RenderLivingBase}'s 90-degree death topple.
     */
    @Override
    protected float getDeathMaxRotation(@Nonnull EntitySimBattlemage entity) {
        return 0.0F;
    }

    /**
     * Flatten vertically while spreading slightly, which reads as melting into the ground.
     *
     * <p>Only scaling is used, no translation: {@code preRenderCallback} runs in the space where
     * the origin still sits at the entity's feet, so shrinking Y alone keeps the body planted and
     * collapses it downward. A translate here would have to account for the inverted Y axis that
     * {@code RenderLivingBase} has already applied, and would sink the model through the floor if
     * it got the sign wrong.
     */
    @Override
    protected void preRenderCallback(@Nonnull EntitySimBattlemage entity, float partialTicks) {
        super.preRenderCallback(entity, partialTicks);
        if (entity.deathTime <= 0) {
            return;
        }
        float progress = Math.min(1.0F, (entity.deathTime + partialTicks) / 20.0F);
        float spread = 1.0F + progress * 0.2F;
        float squash = Math.max(0.05F, 1.0F - progress * 0.9F);
        GlStateManager.scale(spread, squash, spread);
    }
}
