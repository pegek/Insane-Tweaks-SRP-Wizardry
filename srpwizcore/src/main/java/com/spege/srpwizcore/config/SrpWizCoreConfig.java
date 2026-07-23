package com.spege.srpwizcore.config;

import net.minecraftforge.common.config.Config;
import net.minecraftforge.common.config.ConfigManager;
import net.minecraftforge.fml.client.event.ConfigChangedEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

import com.spege.srpwizcore.SrpWizCore;
import com.spege.srpwizcore.config.categories.OtgCompatCategory;
import com.spege.srpwizcore.config.categories.FutureMcCompatCategory;
import com.spege.srpwizcore.config.categories.ThreadingCompatCategory;

@Config(modid = SrpWizCore.MODID, name = SrpWizCore.MODID, category = "")
public class SrpWizCoreConfig {

    @Config.Name("otgCompat")
    @Config.Comment("OpenTerrainGenerator structure-gen null-biome guards.")
    public static final OtgCompatCategory otgCompat = new OtgCompatCategory();

    @Config.Name("futureMcCompat")
    @Config.Comment("FutureMC bamboo worldgen race guard.")
    public static final FutureMcCompatCategory futureMcCompat = new FutureMcCompatCategory();

    @Config.Name("threadingCompat")
    @Config.Comment("EntityThreading concurrency patches (EntityTracker).")
    public static final ThreadingCompatCategory threadingCompat = new ThreadingCompatCategory();

    @Mod.EventBusSubscriber(modid = SrpWizCore.MODID)
    private static class EventHandler {
        @SubscribeEvent
        public static void onConfigChanged(final ConfigChangedEvent.OnConfigChangedEvent event) {
            if (event.getModID().equals(SrpWizCore.MODID)) {
                ConfigManager.sync(SrpWizCore.MODID, Config.Type.INSTANCE);
            }
        }
    }
}
