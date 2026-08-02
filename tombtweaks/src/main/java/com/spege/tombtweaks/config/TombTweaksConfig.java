package com.spege.tombtweaks.config;

import net.minecraftforge.common.config.Config;
import net.minecraftforge.common.config.ConfigManager;
import net.minecraftforge.fml.client.event.ConfigChangedEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

import com.spege.tombtweaks.TombstoneTweaks;
import com.spege.tombtweaks.config.categories.TombstoneCategory;

/**
 * Everything this mod can be told to do, in {@code config/tombtweaks.cfg}.
 *
 * <p>The single {@code tombstone} category is not redundancy — it is the category this whole module
 * used to occupy in {@code insanetweaks.cfg}, kept byte-for-byte identical so migrating a tuned
 * pack is a copy of one block rather than a hand-translation of ninety values.
 *
 * <p>🚨 {@code category = ""} is required. Without it Forge files every category under
 * {@code general.*} and any root-level value seeded by a pack is silently ignored.
 */
@Config(modid = TombstoneTweaks.MODID, name = TombstoneTweaks.MODID, category = "")
public class TombTweaksConfig {

    @Config.Name("tombstone")
    @Config.LangKey("config.tombtweaks.category.tombstone")
    @Config.Comment("Bugfixes and mechanic alterations for Corail Tombstone.")
    public static final TombstoneCategory tombstone = new TombstoneCategory();

    @Mod.EventBusSubscriber(modid = TombstoneTweaks.MODID)
    private static class EventHandler {
        @SubscribeEvent
        public static void onConfigChanged(final ConfigChangedEvent.OnConfigChangedEvent event) {
            if (event.getModID().equals(TombstoneTweaks.MODID)) {
                ConfigManager.sync(TombstoneTweaks.MODID, Config.Type.INSTANCE);
                // The effect whitelist is parsed from registry names once and cached; drop it so
                // edits to tombstone.effectpools apply without a restart.
                com.spege.tombtweaks.effects.EffectPoolRegistry.invalidate();
            }
        }
    }
}
