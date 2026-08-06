package com.spege.reskilltweaks.config;

import com.spege.reskilltweaks.ReskillTweaks;
import com.spege.reskilltweaks.config.categories.ModulesCategory;
import com.spege.reskilltweaks.config.categories.ScarredFleshCategory;
import com.spege.reskilltweaks.config.categories.TraitsCategory;

import net.minecraftforge.common.config.Config;
import net.minecraftforge.common.config.ConfigManager;
import net.minecraftforge.fml.client.event.ConfigChangedEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

/**
 * All three category names — {@code modules}, {@code traits}, {@code scarredFlesh} — are the ones
 * they had in {@code insanetweaks.cfg}, so a tuned pack migrates by copying those blocks across
 * into {@code reskilltweaks.cfg} unchanged. Same call as {@code tombtweaks} keeping its
 * {@code tombstone} category.
 *
 * <p>🚨 {@code category = ""} is required because every field here is a category OBJECT. The
 * inverse is a hard crash: with an empty category, any field Forge's {@code FieldWrapper} accepts —
 * a primitive, an enum, a map, an array — makes {@code ConfigManager.sync} throw "An empty category
 * may not contain anything but objects representing categories!" during mod construction. That is
 * why {@code enableSkillsModule} sits inside {@link ModulesCategory} instead of at the root.
 */
@Config(modid = ReskillTweaks.MODID, name = "reskilltweaks", category = "")
public class ReskillTweaksConfig {

    @Config.Name("modules")
    @Config.LangKey("config.reskilltweaks.category.modules")
    @Config.Comment("Master switches for this mod.")
    public static final ModulesCategory modules = new ModulesCategory();

    @Config.Name("traits")
    @Config.LangKey("config.reskilltweaks.category.traits")
    @Config.Comment("SP costs, parent trees and requirements for the custom Reskillable traits.")
    public static final TraitsCategory traits = new TraitsCategory();

    @Config.Name("scarredFlesh")
    @Config.LangKey("config.reskilltweaks.category.scarredFlesh")
    @Config.Comment("Scarred Flesh trait: the total parasite-affliction level budget.")
    public static final ScarredFleshCategory scarredFlesh = new ScarredFleshCategory();

    @Mod.EventBusSubscriber(modid = ReskillTweaks.MODID)
    private static class EventHandler {
        @SubscribeEvent
        public static void onConfigChanged(final ConfigChangedEvent.OnConfigChangedEvent event) {
            if (event.getModID().equals(ReskillTweaks.MODID)) {
                ConfigManager.sync(ReskillTweaks.MODID, Config.Type.INSTANCE);
            }
        }
    }
}
