package com.spege.srpwizcore.util;

import com.pg85.otg.configuration.world.WorldConfig;
import com.pg85.otg.forge.world.ForgeWorld;

/**
 * Maps the vanilla/CQR structure names to the matching OpenTerrainGenerator
 * {@code WorldConfig} enable flags.
 *
 * <p>OTG's {@code ForgeWorld.getNearestStructurePos} dispatches on these names without ever
 * consulting the flags, so a structure disabled for the dimension still costs a full spiral
 * scan that can never succeed. Callers use this to skip the scan entirely.
 *
 * <p><b>Hard-links OTG types.</b> Call this ONLY after an {@code instanceof ForgeWorldAccessor}
 * test has proved OTG is present and the generator is OTG's — the JVM resolves the
 * {@code invokestatic} lazily, so a pack without OTG never loads this class.
 */
public final class OtgStructureFlags {

    private OtgStructureFlags() {
    }

    /**
     * Returns whether the named structure can exist at all in this OTG world. Unknown names
     * return {@code true} so an unrecognised structure keeps its original (scanning) behaviour.
     */
    public static boolean isEnabled(Object forgeWorldObj, String structureName) {
        if (!(forgeWorldObj instanceof ForgeWorld) || structureName == null) {
            return true;
        }
        WorldConfig cfg = ((ForgeWorld) forgeWorldObj).getConfigs().getWorldConfig();
        if (cfg == null) {
            return true;
        }
        if ("Stronghold".equals(structureName)) {
            return cfg.strongholdsEnabled;
        }
        if ("Mansion".equals(structureName)) {
            return cfg.woodLandMansionsEnabled;
        }
        if ("Monument".equals(structureName)) {
            return cfg.oceanMonumentsEnabled;
        }
        if ("Village".equals(structureName) || "OTGVillage".equals(structureName)) {
            return cfg.villagesEnabled;
        }
        if ("Mineshaft".equals(structureName)) {
            return cfg.mineshaftsEnabled;
        }
        if ("Temple".equals(structureName) || "OTGTemple".equals(structureName)) {
            return cfg.rareBuildingsEnabled;
        }
        if ("Fortress".equals(structureName)) {
            return cfg.netherFortressesEnabled;
        }
        return true;
    }
}
