package com.spege.ebreduxaddon.compat.spore;

import com.binaris.wizardry.client.model.WizardModel;
import com.mojang.blaze3d.vertex.PoseStack;
import com.spege.ebreduxaddon.EbreduxAddon;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Mob;
import net.minecraftforge.client.event.EntityRenderersEvent;
import org.jetbrains.annotations.NotNull;

import java.util.function.ToIntFunction;

/**
 * Klient zarazonego maga i Mykomanty. Wolane z ClientSetup tylko za SporeCompat.present().
 *
 * <p>WizardModel z Redux jest przypiety do AbstractWizard, wiec bierzemy tylko jego siatke
 * (createBodyLayer - glowa z broda) pod wlasna warstwa i zwykly HumanoidModel. Oba moby dziela
 * siatke; Mykomanta jest powiekszona i ma wlasne tekstury.
 */
public final class SporeClient {

    public static final ModelLayerLocation LAYER = new ModelLayerLocation(EbreduxAddon.id("infected_wizard"), "main");

    private SporeClient() {
    }

    public static void layers(EntityRenderersEvent.RegisterLayerDefinitions event) {
        event.registerLayerDefinition(LAYER, WizardModel::createBodyLayer);
    }

    public static void renderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(SporeContent.INFECTED_WIZARD.get(), context -> new Renderer<>(context,
                textures("infected_wizard", InfectedWizardEntity.TEXTURE_COUNT), InfectedWizardEntity::getTextureIndex, 1.0f));
        event.registerEntityRenderer(SporeContent.MYCOMANCER.get(), context -> new Renderer<>(context,
                textures("mycomancer", MycomancerEntity.TEXTURE_COUNT), MycomancerEntity::getTextureIndex, MycomancerEntity.SCALE));
    }

    private static ResourceLocation[] textures(String name, int count) {
        ResourceLocation[] out = new ResourceLocation[count];
        for (int i = 0; i < count; i++) {
            out[i] = EbreduxAddon.id("textures/entity/" + name + "/" + name + "_" + i + ".png");
        }
        return out;
    }

    static final class Renderer<T extends Mob> extends HumanoidMobRenderer<T, HumanoidModel<T>> {

        private final ResourceLocation[] textures;
        private final ToIntFunction<T> index;
        private final float scale;

        Renderer(EntityRendererProvider.Context context, ResourceLocation[] textures, ToIntFunction<T> index, float scale) {
            super(context, new HumanoidModel<>(context.bakeLayer(LAYER)), 0.5f * scale);
            this.textures = textures;
            this.index = index;
            this.scale = scale;
        }

        @Override
        protected void scale(@NotNull T entity, @NotNull PoseStack pose, float partialTick) {
            pose.scale(scale, scale, scale);
        }

        @Override
        public @NotNull ResourceLocation getTextureLocation(@NotNull T entity) {
            return textures[Math.floorMod(index.applyAsInt(entity), textures.length)];
        }
    }
}
