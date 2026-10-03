package com.spege.ebreduxaddon;

import com.spege.ebreduxaddon.feature.ArmorHandler;
import com.spege.ebreduxaddon.feature.GraspState;
import com.spege.ebreduxaddon.feature.ModEffects;
import com.spege.ebreduxaddon.feature.ModEntities;
import com.spege.ebreduxaddon.feature.ModItems;
import com.spege.ebreduxaddon.feature.WandBonusHandler;
import com.spege.ebreduxaddon.feature.ModSpells;
import com.spege.ebreduxaddon.feature.effect.CleansingEffect;
import com.spege.ebreduxaddon.platform.Config;
import com.spege.ebreduxaddon.platform.IdLists;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Mod(EbreduxAddon.MODID)
public final class EbreduxAddon {

    public static final String MODID = "ebreduxaddon";
    public static final Logger LOGGER = LogManager.getLogger(MODID);

    public EbreduxAddon() {
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, Config.SPEC, "ebreduxaddon-common.toml");

        IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();
        ModSpells.register(modBus);
        ModEffects.register(modBus);
        ModEntities.register(modBus);
        ModItems.register(modBus);

        MinecraftForge.EVENT_BUS.register(new CleansingEffect.Ticker());
        MinecraftForge.EVENT_BUS.register(new GraspState.Sweeper());
        MinecraftForge.EVENT_BUS.register(new WandBonusHandler.WandForgeEvents());
        MinecraftForge.EVENT_BUS.register(new ArmorHandler.ArmorForgeEvents());
        WandBonusHandler.registerRedux();
        // Listy id z configu przeliczone od razu po starcie, zeby ewentualne ostrzezenie o literowce
        // bylo w logu startu, a nie przy pierwszym rzucie.
        MinecraftForge.EVENT_BUS.addListener((ServerStartedEvent e) -> IdLists.warmUp());

        // Wersja z kontenera moda (manifest jara), nie ze stalej: stala rozjezdza sie z build.gradle.
        LOGGER.info("[EbreduxAddon] {} loading",
                ModLoadingContext.get().getActiveContainer().getModInfo().getVersion());
    }

    public static ResourceLocation id(String path) {
        return new ResourceLocation(MODID, path);
    }
}
