package com.spege.insanetweaks.client;

import java.util.Objects;

import com.spege.insanetweaks.CommonProxy;
import com.spege.insanetweaks.RenderFerCowMinion;
import com.spege.insanetweaks.RenderPrimitiveSummonerMinion;
import com.spege.insanetweaks.RenderPrimitiveYelloweyeMinion;
import com.spege.insanetweaks.RenderRupterMinion;
import com.spege.insanetweaks.RenderSentinel;
import com.spege.insanetweaks.RenderWizardMinion;
import com.spege.insanetweaks.RenderYelloweyeGlandProjectile;
import com.spege.insanetweaks.RenderYelloweyeNade;
import com.spege.insanetweaks.RenderYelloweyeNadeProjectile;
import com.spege.insanetweaks.client.renderer.entity.RenderBeckonSivMinion;
import com.spege.insanetweaks.client.renderer.entity.RenderBomberBomb;
import com.spege.insanetweaks.client.renderer.entity.RenderLightBomberMinion;
import com.spege.insanetweaks.client.renderer.entity.RenderSimWizard;
import com.spege.insanetweaks.client.renderer.entity.RenderThrallMinion;
import com.spege.insanetweaks.entities.EntityBeckonSivMinion;
import com.spege.insanetweaks.entities.EntityFerCowMinion;
import com.spege.insanetweaks.entities.EntityLightBomberMinion;
import com.spege.insanetweaks.entities.EntityPrimitiveSummonerMinion;
import com.spege.insanetweaks.entities.EntityPrimitiveYelloweyeMinion;
import com.spege.insanetweaks.entities.EntityRupterMinion;
import com.spege.insanetweaks.entities.EntitySentinel;
import com.spege.insanetweaks.entities.EntitySimWizard;
import com.spege.insanetweaks.entities.EntityThrallMinion;
import com.spege.insanetweaks.entities.EntityWizardMinion;
import com.spege.insanetweaks.entities.projectile.EntityBomberBomb;
import com.spege.insanetweaks.entities.projectile.EntityYelloweyeGlandProjectile;
import com.spege.insanetweaks.entities.projectile.EntityYelloweyeNade;
import com.spege.insanetweaks.entities.projectile.EntityYelloweyeNadeProjectile;
import com.spege.insanetweaks.entities.projectile.EntityYelloweyeSpineball;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.Render;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.client.renderer.entity.RenderSnowball;
import net.minecraft.init.Items;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.client.registry.IRenderFactory;
import net.minecraftforge.fml.client.registry.RenderingRegistry;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;

/**
 * Client half of the sided proxy: every entity renderer, the Sanctuary Dome TESR and the
 * client-only Zhonya tint handler. Loaded by FML's proxy injector on the physical client only —
 * see {@link CommonProxy} for why none of this may live in the {@code @Mod} class.
 */
public class ClientProxy extends CommonProxy {

    @Override
    @SuppressWarnings("null")
    public void preInit(FMLPreInitializationEvent event) {
        // Main-menu notice about the Tombstone module moving to CTombstone-Tweaks. Registered
        // unconditionally: the verdict depends on the mod list, which is not settled here, and the
        // handler re-checks it when the menu actually opens. It is a client-only class, so this is
        // the only place it may be constructed - see CommonProxy.
        MinecraftForge.EVENT_BUS.register(new com.spege.insanetweaks.client.TombstoneSplitNoticeHandler());

        if (com.spege.insanetweaks.config.ModConfig.modules.enableSanctuary) {
            net.minecraftforge.fml.client.registry.ClientRegistry.bindTileEntitySpecialRenderer(
                    com.spege.insanetweaks.sanctuary.TileEntitySanctuaryCore.class,
                    new com.spege.insanetweaks.client.renderer.tile.RenderSanctuaryDome());
        }
        RenderingRegistry.registerEntityRenderingHandler(EntitySentinel.class,
                new IRenderFactory<EntitySentinel>() {
                    @Override
                    public Render<? super EntitySentinel> createRenderFor(RenderManager manager) {
                        return new RenderSentinel(manager);
                    }
                });
        RenderingRegistry.registerEntityRenderingHandler(EntityWizardMinion.class,
                new IRenderFactory<EntityWizardMinion>() {
                    @Override
                    public Render<? super EntityWizardMinion> createRenderFor(RenderManager manager) {
                        return new RenderWizardMinion(manager);
                    }
                });
        RenderingRegistry.registerEntityRenderingHandler(EntitySimWizard.class,
                new IRenderFactory<EntitySimWizard>() {
                    @Override
                    public Render<? super EntitySimWizard> createRenderFor(RenderManager manager) {
                        return new RenderSimWizard(manager);
                    }
                });
        RenderingRegistry.registerEntityRenderingHandler(
                com.spege.insanetweaks.entities.EntitySimBattlemage.class,
                new IRenderFactory<com.spege.insanetweaks.entities.EntitySimBattlemage>() {
                    @Override
                    public Render<? super com.spege.insanetweaks.entities.EntitySimBattlemage> createRenderFor(
                            RenderManager manager) {
                        return new com.spege.insanetweaks.client.renderer.entity.RenderSimBattlemage(manager);
                    }
                });
        RenderingRegistry.registerEntityRenderingHandler(EntityFerCowMinion.class,
                new IRenderFactory<EntityFerCowMinion>() {
                    @Override
                    public Render<? super EntityFerCowMinion> createRenderFor(RenderManager manager) {
                        return new RenderFerCowMinion(manager);
                    }
                });
        RenderingRegistry.registerEntityRenderingHandler(EntityPrimitiveYelloweyeMinion.class,
                new IRenderFactory<EntityPrimitiveYelloweyeMinion>() {
                    @Override
                    public Render<? super EntityPrimitiveYelloweyeMinion> createRenderFor(RenderManager manager) {
                        return new RenderPrimitiveYelloweyeMinion(manager);
                    }
                });
        RenderingRegistry.registerEntityRenderingHandler(EntityPrimitiveSummonerMinion.class,
                new IRenderFactory<EntityPrimitiveSummonerMinion>() {
                    @Override
                    public Render<? super EntityPrimitiveSummonerMinion> createRenderFor(RenderManager manager) {
                        return new RenderPrimitiveSummonerMinion(manager);
                    }
                });
        RenderingRegistry.registerEntityRenderingHandler(EntityRupterMinion.class,
                new IRenderFactory<EntityRupterMinion>() {
                    @Override
                    public Render<? super EntityRupterMinion> createRenderFor(RenderManager manager) {
                        return new RenderRupterMinion(manager);
                    }
                });
        RenderingRegistry.registerEntityRenderingHandler(EntityYelloweyeSpineball.class,
                new IRenderFactory<EntityYelloweyeSpineball>() {
                    @Override
                    public Render<? super EntityYelloweyeSpineball> createRenderFor(RenderManager manager) {
                        return new RenderSnowball<EntityYelloweyeSpineball>(manager,
                                Objects.requireNonNull(Items.SLIME_BALL),
                                Minecraft.getMinecraft().getRenderItem());
                    }
                });
        RenderingRegistry.registerEntityRenderingHandler(EntityYelloweyeGlandProjectile.class,
                new IRenderFactory<EntityYelloweyeGlandProjectile>() {
                    @Override
                    public Render<? super EntityYelloweyeGlandProjectile> createRenderFor(RenderManager manager) {
                        return new RenderYelloweyeGlandProjectile(manager);
                    }
                });
        RenderingRegistry.registerEntityRenderingHandler(EntityYelloweyeNadeProjectile.class,
                new IRenderFactory<EntityYelloweyeNadeProjectile>() {
                    @Override
                    public Render<? super EntityYelloweyeNadeProjectile> createRenderFor(RenderManager manager) {
                        return new RenderYelloweyeNadeProjectile(manager);
                    }
                });
        RenderingRegistry.registerEntityRenderingHandler(EntityYelloweyeNade.class,
                new IRenderFactory<EntityYelloweyeNade>() {
                    @Override
                    public Render<? super EntityYelloweyeNade> createRenderFor(RenderManager manager) {
                        return new RenderYelloweyeNade(manager);
                    }
                });
        RenderingRegistry.registerEntityRenderingHandler(EntityBeckonSivMinion.class,
                new IRenderFactory<EntityBeckonSivMinion>() {
                    @Override
                    public Render<? super EntityBeckonSivMinion> createRenderFor(RenderManager manager) {
                        return new RenderBeckonSivMinion(manager);
                    }
                });
        RenderingRegistry.registerEntityRenderingHandler(EntityLightBomberMinion.class,
                new IRenderFactory<EntityLightBomberMinion>() {
                    @Override
                    public Render<? super EntityLightBomberMinion> createRenderFor(RenderManager manager) {
                        return new RenderLightBomberMinion(manager);
                    }
                });
        RenderingRegistry.registerEntityRenderingHandler(EntityBomberBomb.class,
                new IRenderFactory<EntityBomberBomb>() {
                    @Override
                    public Render<? super EntityBomberBomb> createRenderFor(RenderManager manager) {
                        return new RenderBomberBomb(manager);
                    }
                });
        RenderingRegistry.registerEntityRenderingHandler(
                com.spege.insanetweaks.entities.EntityDispatcherClaw.class,
                new IRenderFactory<com.spege.insanetweaks.entities.EntityDispatcherClaw>() {
                    @Override
                    public Render<? super com.spege.insanetweaks.entities.EntityDispatcherClaw> createRenderFor(
                            RenderManager manager) {
                        return new com.spege.insanetweaks.client.renderer.entity.RenderDispatcherClaw(manager);
                    }
                });
        RenderingRegistry.registerEntityRenderingHandler(EntityThrallMinion.class,
                new IRenderFactory<EntityThrallMinion>() {
                    @Override
                    public Render<? super EntityThrallMinion> createRenderFor(RenderManager manager) {
                        return new RenderThrallMinion(manager);
                    }
                });
        RenderingRegistry.registerEntityRenderingHandler(
                com.spege.insanetweaks.entities.EntityCorruptedSapling.class,
                new IRenderFactory<com.spege.insanetweaks.entities.EntityCorruptedSapling>() {
                    @Override
                    public Render<com.spege.insanetweaks.entities.EntityCorruptedSapling> createRenderFor(
                            RenderManager manager) {
                        return new com.spege.insanetweaks.client.renderer.entity.RenderCorruptedSapling(manager);
                    }
                });

        // Zhonya rework: golden player tint during Gilded Stasis.
        MinecraftForge.EVENT_BUS.register(new com.spege.insanetweaks.events.ZhonyaClientHandler());
    }
}
