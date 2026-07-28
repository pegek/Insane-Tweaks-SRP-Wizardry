package com.spege.srpwizcore;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import net.minecraftforge.fml.common.Mod;

/**
 * SRP&WIZ Core — private pack-glue for the SRP&Wizardry pack (DEv 1.2). Mixins:
 * EntityTracker, MapStorage and IntCache concurrency fixes (early), OpenTerrainGenerator
 * structure-gen null-biome guards, FutureMC bamboo worldgen race guard, per-dimension
 * Ice&amp;Fire worldgen control, plus the perf-glue guards for Doomlike Dungeons (null dungeon
 * map), CQR (disabled-structure scans in OTG dims) and Raids-Backport (per-world storage).
 * Also owns a small native registry system: the configurable dormant-waystone travel
 * system (block/worldgen/teleport, see {@code com.spege.srpwizcore.dormant}). Each fix
 * config-gated in {@link com.spege.srpwizcore.config.SrpWizCoreConfig}.
 */
@Mod(modid = SrpWizCore.MODID,
        name = SrpWizCore.NAME,
        version = SrpWizCore.VERSION,
        dependencies = "after:openterraingenerator;after:futuremc;after:iceandfire;"
                + "after:dldungeonsjbg;after:cqrepoured;after:raids",
        acceptableRemoteVersions = "*")
public class SrpWizCore {
    public static final String MODID = "srpwizcore";
    public static final String NAME = "SRP&WIZ Core";
    public static final String VERSION = "1.7.2";

    public static final Logger LOGGER = LogManager.getLogger(MODID);

    /**
     * Parses the Ice&amp;Fire per-dimension worldgen lists and the SpawnEngine budget tables once
     * at startup. Later edits are picked up by the {@code OnConfigChangedEvent} handler in
     * {@link com.spege.srpwizcore.config.SrpWizCoreConfig}.
     */
    @Mod.EventHandler
    public void preInit(net.minecraftforge.fml.common.event.FMLPreInitializationEvent event) {
        com.spege.srpwizcore.util.IandfWorldgenOverrides.rebuild();
        com.spege.srpwizcore.spawnengine.SpawnEngine.reload();
    }

    @Mod.EventHandler
    public void init(net.minecraftforge.fml.common.event.FMLInitializationEvent event) {
        // Registered unconditionally: the srparasites strip and the F0 diagnostics must work
        // with the engine master switch off, and every SpawnEngine flag is read live inside the
        // handlers anyway.
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.register(
                new com.spege.srpwizcore.spawnengine.SpawnEngineTickHandler());
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.register(
                new com.spege.srpwizcore.spawnengine.SpawnListFilterHandler());

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
