package com.spege.srpwizmixins.config;

import net.minecraftforge.common.config.Config;
import net.minecraftforge.common.config.ConfigManager;
import net.minecraftforge.fml.client.event.ConfigChangedEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

import com.spege.srpwizmixins.SrpWizMixins;
import com.spege.srpwizmixins.config.categories.SrpCompatCategory;

@Config(modid = SrpWizMixins.MODID, name = SrpWizMixins.MODID)
public class SrpWizMixinsConfig {

    @Config.Name("srpCompat")
    @Config.Comment("Native SRParasites 1.10.7 fixes — each toggle independent.")
    public static final SrpCompatCategory srpCompat = new SrpCompatCategory();

    @Mod.EventBusSubscriber(modid = SrpWizMixins.MODID)
    private static class EventHandler {
        @SubscribeEvent
        public static void onConfigChanged(final ConfigChangedEvent.OnConfigChangedEvent event) {
            if (event.getModID().equals(SrpWizMixins.MODID)) {
                ConfigManager.sync(SrpWizMixins.MODID, Config.Type.INSTANCE);
            }
        }
    }
}
