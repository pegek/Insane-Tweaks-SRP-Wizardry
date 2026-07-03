package com.spege.insanetweaks.client.renderer.entity;

import javax.annotation.Nonnull;

import com.dhanantry.scapeandrunparasites.client.model.entity.infected.ModelInfHuman;
import com.spege.insanetweaks.client.renderer.entity.layers.LayerSimWizardFloatingFocus;
import com.spege.insanetweaks.client.renderer.entity.layers.LayerSimWizardGlow;
import com.spege.insanetweaks.entities.EntitySimWizard;

import net.minecraft.client.renderer.entity.RenderLiving;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/**
 * v3.0 visual rework - "stop fighting the SRP model":
 *
 *  - Model: plain {@link ModelInfHuman}. The old {@code ModelSimWizard} subclass added
 *    hood/mantle/spine boxes that sampled garbage UV regions of the 64x55 atlas (the
 *    blue slabs from playtests) and layered cast-pose deltas on top of ModelSRP's own
 *    animation logic (the "budging" during casts). Both are gone; native SRP animations
 *    now run untouched.
 *  - Texture: {@code insanetweaks:textures/entity/sim_wizard.png} - a violet/magenta
 *    recolor of the SRP human atlas with the IDENTICAL 64x55 layout, so every joint maps
 *    perfectly while the wizard is instantly distinguishable from a plain sim_human.
 *  - Identity layers: {@link LayerSimWizardGlow} (emissive violet shell, flares on cast)
 *    and {@link LayerSimWizardFloatingFocus} (levitating wand beside the shoulder -
 *    replaces the fragile joint-chain held-item attempt).
 *  - No preRenderCallback scale pulse - the old +18% inflate/deflate on every cast read
 *    as a glitch, not an effect. Cast feedback now comes from glow flare, focus spin-up,
 *    telegraph ring particles and the burst.
 */
@SideOnly(Side.CLIENT)
public class RenderSimWizard extends RenderLiving<EntitySimWizard> {

    private static final ResourceLocation TEXTURE = new ResourceLocation("insanetweaks",
            "textures/entity/sim_wizard.png");

    public RenderSimWizard(RenderManager renderManager) {
        super(renderManager, new ModelInfHuman(), 0.5F);
        this.addLayer(new LayerSimWizardGlow(this.getMainModel(), TEXTURE));
        this.addLayer(new LayerSimWizardFloatingFocus());
    }

    @Override
    @Nonnull
    protected ResourceLocation getEntityTexture(@Nonnull EntitySimWizard entity) {
        return TEXTURE;
    }
}
