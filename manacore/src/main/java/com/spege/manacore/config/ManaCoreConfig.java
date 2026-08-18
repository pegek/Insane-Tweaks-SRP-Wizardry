package com.spege.manacore.config;

import com.spege.manacore.ManaCoreMod;
import com.spege.manacore.config.categories.HudCategory;
import com.spege.manacore.config.categories.PoolCategory;
import com.spege.manacore.config.categories.RegenCategory;

import net.minecraftforge.common.config.Config;
import net.minecraftforge.common.config.ConfigManager;
import net.minecraftforge.fml.client.event.ConfigChangedEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

/**
 * category = "" jest OBOWIAZKOWE, bo wszystkie pola sa obiektami kategorii.
 * Pominiecie go wrzuca kategorie pod `general.*` i po cichu ignoruje wstepnie ustawione wartosci.
 * Odwrotnie: gdyby ktores pole bylo wartoscia prosta, tablica lub mapa, `""` bylby twardym crashem
 * w ConfigManager.sync ("An empty category may not contain anything but objects...").
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
