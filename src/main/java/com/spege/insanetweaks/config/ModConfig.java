package com.spege.insanetweaks.config;

import com.spege.insanetweaks.InsaneTweaksMod;
import com.spege.insanetweaks.config.categories.ClientCategory;
import com.spege.insanetweaks.config.categories.EntitiesCategory;
import com.spege.insanetweaks.config.categories.GrimoireCategory;
import com.spege.insanetweaks.config.categories.InteractionsCategory;
import com.spege.insanetweaks.config.categories.ModulesCategory;
import com.spege.insanetweaks.config.categories.SanctuaryCategory;
import com.spege.insanetweaks.config.categories.SrpCompatCategory;
import com.spege.insanetweaks.config.categories.ThrallCategory;
import com.spege.insanetweaks.config.categories.TombstoneCategory;
import com.spege.insanetweaks.config.categories.TraitsCategory;
import com.spege.insanetweaks.config.categories.TweaksCategory;

import net.minecraftforge.common.config.Config;
import net.minecraftforge.common.config.ConfigManager;
import net.minecraftforge.fml.client.event.ConfigChangedEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

@Config(modid = InsaneTweaksMod.MODID, name = "insanetweaks", category = "")
public class ModConfig {

    @Config.Name("modules")
    @Config.LangKey("config.insanetweaks.category.modules")
    @Config.Comment("Toggle main systems and cross-mod integrations.")
    public static final ModulesCategory modules = new ModulesCategory();

    @Config.Name("tweaks")
    @Config.LangKey("config.insanetweaks.category.tweaks")
    @Config.Comment("Specific bugfixes and mechanic alterations.")
    public static final TweaksCategory tweaks = new TweaksCategory();

    @Config.Name("interactions")
    @Config.LangKey("config.insanetweaks.category.interactions")
    @Config.Comment("Master switches for cross-mod interactions that depend on optional mods.")
    public static final InteractionsCategory interactions = new InteractionsCategory();

    @Config.Name("traits")
    @Config.LangKey("config.insanetweaks.category.traits")
    @Config.Comment("SP costs, parent trees and requirements for custom Reskillable traits.")
    public static final TraitsCategory traits = new TraitsCategory();

    @Config.Name("tombstone")
    @Config.LangKey("config.insanetweaks.category.tombstone")
    @Config.Comment("Bugfixes and mechanic alterations for Corail Tombstone.")
    public static final TombstoneCategory tombstone = new TombstoneCategory();

    @Config.Name("thrall")
    @Config.LangKey("config.insanetweaks.category.thrall")
    @Config.Comment("The Thrall companion entity: slots, work modes, AI tunables.")
    public static final ThrallCategory thrall = new ThrallCategory();

    @Config.Name("entities")
    @Config.LangKey("config.insanetweaks.category.entities")
    @Config.Comment("Custom entities added by the mod.")
    public static final EntitiesCategory entities = new EntitiesCategory();

    @Config.Name("client")
    @Config.LangKey("config.insanetweaks.category.client")
    @Config.Comment("Visual toggles and debugging tools.")
    public static final ClientCategory client = new ClientCategory();

    @Config.Name("srpCompat")
    @Config.LangKey("config.insanetweaks.category.srpCompat")
    @Config.Comment("Native patch module for Scape and Run: Parasites (SRP 1.10.7). Per-fix toggles, default OFF.")
    public static final SrpCompatCategory srpCompat = new SrpCompatCategory();

    @Config.Name("sanctuary")
    @Config.LangKey("config.insanetweaks.category.sanctuary")
    @Config.Comment("Sanctuary Dome tunables (radius tiers, fuel, cleanse, dimension blacklist). Master toggle is modules.enableSanctuary.")
    public static final SanctuaryCategory sanctuary = new SanctuaryCategory();

    @Config.Name("grimoire")
    @Config.LangKey("config.insanetweaks.category.grimoire")
    @Config.Comment("Tunables for the Grimoire enchantment (boost formula, owner-binding, drop/anvil protection). Master toggle is modules.enableGrimoire.")
    public static final GrimoireCategory grimoire = new GrimoireCategory();

    @Mod.EventBusSubscriber(modid = InsaneTweaksMod.MODID)
    private static class EventHandler {
        @SubscribeEvent
        public static void onConfigChanged(final ConfigChangedEvent.OnConfigChangedEvent event) {
            if (event.getModID().equals(InsaneTweaksMod.MODID)) {
                ConfigManager.sync(InsaneTweaksMod.MODID, Config.Type.INSTANCE);
            }
        }
    }
}
