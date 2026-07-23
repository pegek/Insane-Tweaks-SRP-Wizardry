package com.spege.srpwizcore;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import net.minecraftforge.fml.common.Mod;

/**
 * SRP&WIZ Core — private pack-glue for the SRP&Wizardry pack (DEv 1.2). Mixin-only:
 * EntityTracker concurrency fix (early), OpenTerrainGenerator structure-gen null-biome
 * guards, FutureMC bamboo worldgen race guard. Each fix config-gated in
 * {@link com.spege.srpwizcore.config.SrpWizCoreConfig}.
 */
@Mod(modid = SrpWizCore.MODID,
        name = SrpWizCore.NAME,
        version = SrpWizCore.VERSION,
        dependencies = "after:openterraingenerator;after:futuremc",
        acceptableRemoteVersions = "*")
public class SrpWizCore {
    public static final String MODID = "srpwizcore";
    public static final String NAME = "SRP&WIZ Core";
    public static final String VERSION = "1.0.0";

    public static final Logger LOGGER = LogManager.getLogger(MODID);
}
