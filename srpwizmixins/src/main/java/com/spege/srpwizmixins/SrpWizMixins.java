package com.spege.srpwizmixins;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import net.minecraftforge.fml.common.Mod;

/**
 * SRP&Wiz mixins — standalone public mod bundling the native SRParasites 1.10.7
 * performance/correctness fixes. Mixin-only (no registry objects); each fix is
 * config-gated in {@link com.spege.srpwizmixins.config.SrpWizMixinsConfig}.
 */
@Mod(modid = SrpWizMixins.MODID,
        name = SrpWizMixins.NAME,
        version = SrpWizMixins.VERSION,
        dependencies = "after:srparasites",
        acceptableRemoteVersions = "*")
public class SrpWizMixins {
    public static final String MODID = "srpwizmixins";
    public static final String NAME = "SRP&Wiz mixins";
    public static final String VERSION = "1.0.0";

    public static final Logger LOGGER = LogManager.getLogger(MODID);
}
