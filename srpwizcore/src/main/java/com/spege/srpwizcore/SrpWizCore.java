package com.spege.srpwizcore;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import net.minecraftforge.fml.common.Mod;

/**
 * SRP&WIZ Core — private pack-glue for the SRP&Wizardry pack (DEv 1.2). Mixins:
 * EntityTracker, MapStorage and IntCache concurrency fixes (early), OpenTerrainGenerator
 * structure-gen null-biome guards, FutureMC bamboo worldgen race guard, per-dimension
 * Ice&amp;Fire worldgen control.
 * Also owns a small native registry system: the configurable dormant-waystone travel
 * system (block/worldgen/teleport, see {@code com.spege.srpwizcore.dormant}). Each fix
 * config-gated in {@link com.spege.srpwizcore.config.SrpWizCoreConfig}.
 */
@Mod(modid = SrpWizCore.MODID,
        name = SrpWizCore.NAME,
        version = SrpWizCore.VERSION,
        dependencies = "after:openterraingenerator;after:futuremc;after:iceandfire",
        acceptableRemoteVersions = "*")
public class SrpWizCore {
    public static final String MODID = "srpwizcore";
    public static final String NAME = "SRP&WIZ Core";
    public static final String VERSION = "1.4.0";

    public static final Logger LOGGER = LogManager.getLogger(MODID);

    /**
     * Parses the Ice&amp;Fire per-dimension worldgen lists once at startup. Later edits are picked
     * up by the {@code OnConfigChangedEvent} handler in
     * {@link com.spege.srpwizcore.config.SrpWizCoreConfig}.
     */
    @Mod.EventHandler
    public void preInit(net.minecraftforge.fml.common.event.FMLPreInitializationEvent event) {
        com.spege.srpwizcore.util.IandfWorldgenOverrides.rebuild();
    }

    @Mod.EventHandler
    public void init(net.minecraftforge.fml.common.event.FMLInitializationEvent event) {
        if (com.spege.srpwizcore.config.SrpWizCoreConfig.dormantWaystones.enabled) {
            if (com.spege.srpwizcore.config.SrpWizCoreConfig.dormantWaystones.worldgenEnabled) {
                net.minecraftforge.fml.common.registry.GameRegistry.registerWorldGenerator(
                        new com.spege.srpwizcore.dormant.DormantWaystoneWorldGen(), 0);
            }
            net.minecraftforge.common.MinecraftForge.EVENT_BUS.register(
                    new com.spege.srpwizcore.dormant.DormantWaystoneEventHandler());
            net.minecraftforge.common.MinecraftForge.EVENT_BUS.register(
                    new com.spege.srpwizcore.dormant.DormantTeleportHandler());
            net.minecraftforge.common.MinecraftForge.EVENT_BUS.register(
                    new com.spege.srpwizcore.dormant.DormantEyeHandler());
            LOGGER.info("[srpwizcore] dormant-waystone system armed (keyItem={}, dims {}<->{})",
                    com.spege.srpwizcore.config.SrpWizCoreConfig.dormantWaystones.keyItem,
                    com.spege.srpwizcore.config.SrpWizCoreConfig.dormantWaystones.dimSurface,
                    com.spege.srpwizcore.config.SrpWizCoreConfig.dormantWaystones.dimTarget);
        }
    }
}
