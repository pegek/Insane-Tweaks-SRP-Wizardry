package com.spege.srpwizcore;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import net.minecraftforge.fml.common.Mod;

/**
 * SRP&WIZ Core — private pack-glue for the SRP&Wizardry pack (DEv 1.2). Mixin-only:
 * EntityTracker concurrency fix (early), OpenTerrainGenerator structure-gen null-biome
 * guards, FutureMC bamboo worldgen race guard, per-dimension Ice&amp;Fire worldgen control.
 * Each fix config-gated in {@link com.spege.srpwizcore.config.SrpWizCoreConfig}.
 */
@Mod(modid = SrpWizCore.MODID,
        name = SrpWizCore.NAME,
        version = SrpWizCore.VERSION,
        dependencies = "after:openterraingenerator;after:futuremc;after:iceandfire",
        acceptableRemoteVersions = "*")
public class SrpWizCore {
    public static final String MODID = "srpwizcore";
    public static final String NAME = "SRP&WIZ Core";
    public static final String VERSION = "1.1.0";

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
}
