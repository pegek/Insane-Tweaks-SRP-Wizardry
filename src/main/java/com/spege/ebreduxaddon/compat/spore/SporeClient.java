package com.spege.ebreduxaddon.compat.spore;

import com.binaris.wizardry.client.model.WizardModel;
import com.spege.ebreduxaddon.EbreduxAddon;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.client.event.EntityRenderersEvent;
import org.jetbrains.annotations.NotNull;

/**
 * Klient zarazonego maga. Wolane z ClientSetup tylko za SporeCompat.present().
 *
 * <p>WizardModel z Redux jest przypiety do AbstractWizard, wiec bierzemy tylko jego siatke
 * (createBodyLayer - glowa z broda) pod wlasna warstwa i zwykly HumanoidModel.
 */
public final class SporeClient {

    public static final ModelLayerLocation LAYER = new ModelLayerLocation(EbreduxAddon.id("infected_wizard"), "main");

    private SporeClient() {
    }

    public static void layers(EntityRenderersEvent.RegisterLayerDefinitions event) {
        event.registerLayerDefinition(LAYER, WizardModel::createBodyLayer);
    }

    public static void renderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(SporeContent.INFECTED_WIZARD.get(), Renderer::new);
    }

    static final class Renderer extends HumanoidMobRenderer<InfectedWizardEntity, HumanoidModel<InfectedWizardEntity>> {

        private static final ResourceLocation[] TEXTURES = new ResourceLocation[InfectedWizardEntity.TEXTURE_COUNT];

        static {
            for (int i = 0; i < TEXTURES.length; i++) {
                TEXTURES[i] = EbreduxAddon.id("textures/entity/infected_wizard/infected_wizard_" + i + ".png");
            }
        }

        Renderer(EntityRendererProvider.Context context) {
            super(context, new HumanoidModel<>(context.bakeLayer(LAYER)), 0.5f);
        }

        @Override
        public @NotNull ResourceLocation getTextureLocation(InfectedWizardEntity entity) {
            return TEXTURES[entity.getTextureIndex()];
        }
    }
}
