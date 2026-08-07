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
 * system (block/worldgen/teleport, see {@code com.spege.srpwizcore.dormant}), and the
 * elemental dragon ranged weapon system that passes fire/ice/lightning to any ammunition
 * (see {@code com.spege.srpwizcore.dragonranged}). Each fix config-gated in
 * {@link com.spege.srpwizcore.config.SrpWizCoreConfig}.
 */
@Mod(modid = SrpWizCore.MODID,
        name = SrpWizCore.NAME,
        version = SrpWizCore.VERSION,
        dependencies = "after:openterraingenerator;after:futuremc;after:iceandfire;"
                + "after:dldungeonsjbg;after:cqrepoured;after:raids;"
                + "after:spartanfire;after:spartandragonsteel;"
                + "after:betterhurttimer;after:bountifulbaubles;after:firstaid",
        acceptableRemoteVersions = "*")
public class SrpWizCore {
    public static final String MODID = "srpwizcore";
    public static final String NAME = "SRP&WIZ Core";
    public static final String VERSION = "1.15.1";

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

        // WorseHurtTimer i-frame layer. Registered unconditionally, like the SpawnEngine
        // handlers above: every whtCompat flag is read live inside the provider and inside the
        // mixins, so gating registration on `enabled` would only mean that switching the module
        // on at runtime did nothing until a restart. Harmless when WHT is absent — nothing reads
        // WhtIFrames then.
        com.spege.srpwizcore.whtcompat.CrossNecklaceProvider.registerIfPossible();

        // Bountiful Baubles trinket repairs. Resolves the item now that item registration is
        // over. The mixin behind it is early and vanilla-targeted, so it always applies and
        // self-gates on whether this arming found the item and the mods it needs.
        com.spege.srpwizcore.bbcompat.BrokenHeartProvider.arm();

        com.spege.srpwizcore.whtcompat.WhtDiag.syncFromConfig(
                com.spege.srpwizcore.config.SrpWizCoreConfig.whtCompat.diagEnabled,
                com.spege.srpwizcore.config.SrpWizCoreConfig.whtCompat.diagVerbose,
                com.spege.srpwizcore.config.SrpWizCoreConfig.whtCompat.diagPlayersOnly);
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.register(
                new com.spege.srpwizcore.whtcompat.WhtDiagHandler());
        if (net.minecraftforge.fml.common.Loader.isModLoaded("betterhurttimer")) {
            // Separate class on purpose: its listener takes a WorseHurtTimer event type, and Forge
            // reflects over parameter types at registration time.
            net.minecraftforge.common.MinecraftForge.EVENT_BUS.register(
                    new com.spege.srpwizcore.whtcompat.WhtPreAttackDiagHandler());
        }

        if (net.minecraftforge.fml.common.Loader.isModLoaded("cqrepoured")) {
            // Boss-room chest lock + boss-death bonus loot. All flags read live inside the
            // handlers; the class touches no CQR types (class-name checks only).
            net.minecraftforge.common.MinecraftForge.EVENT_BUS.register(
                    new com.spege.srpwizcore.util.CqrBossRoomHandler());
            // Experimental combat rules (2026-08-01): siege digging of player-placed
            // blocks inside protected dungeons + per-player attacker cap. Flags read live.
            net.minecraftforge.common.MinecraftForge.EVENT_BUS.register(
                    new com.spege.srpwizcore.util.CqrSiegeDigHandler());
            net.minecraftforge.common.MinecraftForge.EVENT_BUS.register(
                    new com.spege.srpwizcore.util.CqrAttackerCapHandler());
        }

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

        if (com.spege.srpwizcore.config.SrpWizCoreConfig.dragonRanged.enabled
                && net.minecraftforge.fml.common.Loader.isModLoaded("iceandfire")) {
            // The element table calls Ice and Fire's capability and chain-lightning API, so the
            // handler only makes sense with that mod present. The weapon map itself is built in
            // postInit, after every mod has registered its items.
            net.minecraftforge.common.MinecraftForge.EVENT_BUS.register(
                    new com.spege.srpwizcore.dragonranged.DragonRangedHandler());
            LOGGER.info("[srpwizcore] dragon ranged elements armed");
        }
    }

    /**
     * Resolves the elemental dragon ranged weapons once every mod has registered its items.
     */
    @Mod.EventHandler
    public void postInit(net.minecraftforge.fml.common.event.FMLPostInitializationEvent event) {
        if (com.spege.srpwizcore.config.SrpWizCoreConfig.dragonRanged.enabled) {
            com.spege.srpwizcore.dragonranged.DragonWeaponRegistry.build();
        }
    }

    @Mod.EventHandler
    public void serverStarting(net.minecraftforge.fml.common.event.FMLServerStartingEvent event) {
        event.registerServerCommand(new com.spege.srpwizcore.whtcompat.CommandWhtDiag());
    }
}
