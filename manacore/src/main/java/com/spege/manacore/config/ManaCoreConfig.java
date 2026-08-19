package com.spege.manacore.config;

import com.spege.manacore.ManaCoreMod;
import com.spege.manacore.config.categories.EbwCategory;
import com.spege.manacore.config.categories.HudCategory;
import com.spege.manacore.config.categories.PoolCategory;
import com.spege.manacore.config.categories.RegenCategory;
import com.spege.manacore.config.categories.TabCategory;

import net.minecraftforge.common.config.Config;
import net.minecraftforge.common.config.ConfigManager;
import net.minecraftforge.fml.client.event.ConfigChangedEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

/**
 * category = "" is MANDATORY, because every field here is a category object.
 * Omitting it dumps the categories under `general.*` and silently ignores any pre-seeded values.
 * Conversely, if any field were a plain value, an array or a map, `""` would be a hard crash
 * in ConfigManager.sync ("An empty category may not contain anything but objects...").
 */
@Config(modid = ManaCoreMod.MODID, name = ManaCoreMod.MODID, category = "")
public class ManaCoreConfig {

    @Config.Name("pool")
    @Config.LangKey("config.manacore.category.pool")
    @Config.Comment("Player mana pool: base maximum, hard cap and progression tunables.")
    public static final PoolCategory pool = new PoolCategory();

    @Config.Name("regen")
    @Config.LangKey("config.manacore.category.regen")
    @Config.Comment("Passive mana regeneration tunables.")
    public static final RegenCategory regen = new RegenCategory();

    @Config.Name("hud")
    @Config.LangKey("config.manacore.category.hud")
    @Config.Comment("Mana HUD display toggles and appearance.")
    public static final HudCategory hud = new HudCategory();

    @Config.Name("ebw")
    @Config.LangKey("config.manacore.category.ebw")
    @Config.Comment("Electroblob's Wizardry bridge: spell cost hookup and wand upgrade tunables.")
    public static final EbwCategory ebw = new EbwCategory();

    @Config.Name("tab")
    @Config.LangKey("config.manacore.category.tab")
    @Config.Comment("Trinkets and Baubles bridge: mana pool takeover and item tunables.")
    public static final TabCategory tab = new TabCategory();

    @Mod.EventBusSubscriber(modid = ManaCoreMod.MODID)
    private static class EventHandler {

        @SubscribeEvent
        public static void onConfigChanged(ConfigChangedEvent.OnConfigChangedEvent event) {
            if (ManaCoreMod.MODID.equals(event.getModID())) {
                ConfigManager.sync(ManaCoreMod.MODID, Config.Type.INSTANCE);
            }
        }
    }
}
