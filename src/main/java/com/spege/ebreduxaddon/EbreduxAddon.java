package com.spege.ebreduxaddon;

import com.spege.ebreduxaddon.platform.Config;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.common.Mod;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Mod(EbreduxAddon.MODID)
public final class EbreduxAddon {

    public static final String MODID = "ebreduxaddon";
    public static final Logger LOGGER = LogManager.getLogger(MODID);

    public EbreduxAddon() {
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, Config.SPEC, "ebreduxaddon-common.toml");
        // Wersja z kontenera moda (manifest jara), nie ze stalej: stala rozjezdza sie z build.gradle.
        LOGGER.info("[EbreduxAddon] {} loading",
                ModLoadingContext.get().getActiveContainer().getModInfo().getVersion());
    }

    public static ResourceLocation id(String path) {
        return new ResourceLocation(MODID, path);
    }
}
