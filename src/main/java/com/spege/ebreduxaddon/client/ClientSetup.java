package com.spege.ebreduxaddon.client;

import com.binaris.wizardry.client.renderer.entity.MagicArrowRenderer;
import com.spege.ebreduxaddon.EbreduxAddon;
import com.spege.ebreduxaddon.feature.ModEntities;
import net.minecraft.client.renderer.entity.NoopRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Rejestracje tylko po stronie klienta. Serwer tej klasy nie laduje (value = Dist.CLIENT). */
@Mod.EventBusSubscriber(modid = EbreduxAddon.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class ClientSetup {

    private ClientSetup() {
    }

    @SubscribeEvent
    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        // Placeholder: tekstura darta z Redux. Docelowa w Tasku 8.
        event.registerEntityRenderer(ModEntities.SPINE.get(),
                ctx -> new MagicArrowRenderer<>(ctx, new ResourceLocation("ebwizardry", "textures/entity/dart.png")));
        // Fala nie ma modelu, rysuje tylko czasteczki - ale renderer musi byc, inaczej klient pada.
        event.registerEntityRenderer(ModEntities.PURIFYING_WAVE.get(), NoopRenderer::new);
    }
}
