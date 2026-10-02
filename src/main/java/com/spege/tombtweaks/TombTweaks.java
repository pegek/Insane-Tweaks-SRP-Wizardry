package com.spege.tombtweaks;

import com.spege.tombtweaks.platform.Config;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.common.Mod;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Mod(TombTweaks.MODID)
public final class TombTweaks {

    public static final String MODID = "tombtweaks";
    public static final Logger LOGGER = LogManager.getLogger(MODID);

    public TombTweaks() {
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, Config.SPEC, "tombtweaks-common.toml");
        // Wersja czytana z kontenera moda - czyli z manifestu jara, przez
        // ${file.jarVersion} w mods.toml. NIE trzymamy drugiej kopii w stalej:
        // taka stala rozjezdza sie z build.gradle i wtedy log klamie o tym,
        // co jest faktycznie zainstalowane.
        LOGGER.info("[TombTweaks] {} loading",
                ModLoadingContext.get().getActiveContainer().getModInfo().getVersion());
    }
}
