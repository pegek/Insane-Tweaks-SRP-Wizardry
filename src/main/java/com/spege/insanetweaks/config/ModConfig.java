package com.spege.insanetweaks.config;

import com.spege.insanetweaks.InsaneTweaksMod;
import net.minecraftforge.common.config.Config;
import net.minecraftforge.common.config.ConfigManager;
import net.minecraftforge.fml.client.event.ConfigChangedEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

@Config(modid = InsaneTweaksMod.MODID)
public class ModConfig {

    @Config.Comment("Should mod display info whether he found any cursed items in players inventory on death")
    @Config.Name("Display info on death")
    public static boolean displayInfoOnDeath = false;

    @Config.Comment("Fixes the Curse of Possession exploit with Corail Tombstone. Cursed items will now properly vanish on death instead of hiding in your grave.")
    @Config.Name("Enable Curse of Possession Patch")
    public static boolean enableCurseOfPossessionPatch = true;

    @Config.Comment("Registers Cost, Potency and Speedcast Core items for use with Electroblob's Wizardry armor upgrades. Disable if you don't use EBWizardry.")
    @Config.Name("Enable Custom Cores (EBWizardry)")
    public static boolean enableCustomCores = true;

    @Config.Comment("Enables the SRParasites and Electroblob's Wizardry Bridge. This includes the evolving BattleMage Armor, Sentient Spellblades, and the Sentient Aegis shield.")
    @Config.Name("Enable SRP-EBWizardry Bridge")
    public static boolean enableSrpEbWizardryBridge = true;

    @Config.Comment("Whether to display debug info for various mod mechanics. I highly urge you to keep it on until the mod is in its alpha version.")
    @Config.Name("Display DEBUG INFO")
    public static boolean displayDebugInfo = false;

    @Config.Comment({"Additional effects removed by the CLEANSE effect, on top of all effects where isBeneficial() == false.",
                     "Add effect IDs here if a mod's negative effect is not getting removed automatically.",
                     "Example: minecraft:glowing"})
    @Config.Name("Cleanse Effect List")
    public static String[] cleanseAdditionalEffects = {"srparasites:no_vision"};

    @Config.Comment({"Hides the CLEANSE effect icon and text from the HUD and inventory effect panel.",
                     "Enable this flag to suppress the effect from appearing in the GUI entirely.",
                     "The mechanic itself still works regardless of this setting.",
                     "Intended as a temporary toggle while the effect's visuals are being refined."})
    @Config.Name("Hide Cleanse Effect from GUI")
    public static boolean hideCleanseHudEffect = true;

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
