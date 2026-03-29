package com.spege.insanetweaks.config;

import com.spege.insanetweaks.InsaneTweaksMod;
import net.minecraftforge.common.config.Config;
import net.minecraftforge.common.config.ConfigManager;
import net.minecraftforge.fml.client.event.ConfigChangedEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

@Config(modid = InsaneTweaksMod.MODID, name = "insanetweaks", category = "")
public class ModConfig {

    @Config.Name("[ 1 ] MODULES & INTEGRATIONS")
    @Config.Comment("Toggle main systems and cross-mod integrations.")
    public static final Modules modules = new Modules();

    @Config.Name("[ 2 ] TWEAKS & FIXES")
    @Config.Comment("Configure specific bugfixes and mechanic alterations.")
    public static final Tweaks tweaks = new Tweaks();

    @Config.Name("[ 3 ] SKILL TRAITS")
    @Config.Comment("Configure SP costs, parent trees, and requirements for custom traits.")
    public static final Traits traits = new Traits();

    @Config.Name("[ 4 ] CLIENT & DEBUG")
    @Config.Comment("Visual toggles and debugging tools.")
    public static final Client client = new Client();

    // ========================================================================
    // CATEGORY 1: MODULES & INTEGRATIONS
    // ========================================================================
    public static class Modules {
        @Config.Comment("Enables the SRParasites and Electroblob's Wizardry Bridge. This includes the evolving Living Armor, Sentient Spellblades, and the Sentient Aegis shield.")
        @Config.Name("Enable SRP-EBWizardry Bridge")
        @Config.RequiresMcRestart
        public boolean enableSrpEbWizardryBridge = true;

        @Config.Comment("Enables the Skills Event Handlers port for CompatSkills/Reskillable Traits. Requires Reskillable Fork and Compat Skills Fork to function. IF YOU HAVE CUSTOM MADE RESKILLABLE CONFIG IT IT RECOMENNDED TO DO BACKUP, i'm doing automatic backup tho")
        @Config.Name("Enable Skills Module")
        @Config.RequiresMcRestart
        public boolean enableSkillsModule = true;

        @Config.Comment("Registers Cost, Potency and Speedcast Core items for use with Electroblob's Wizardry armor upgrades. Disable if you don't use EBWizardry.")
        @Config.Name("Enable Custom Cores (EBWizardry)")
        @Config.RequiresMcRestart
        public boolean enableCustomCores = true;

        @Config.Comment({
                "Enables the Bauble Fruit system. Bauble Fruits are one-time consumable items that permanently",
                "grant the player an additional bauble slot when eaten.",
                "Requires BaublesEX (v2.0.0+) to function. Does nothing if only the original Baubles (v1.5.x) is installed." })
        @Config.Name("Enable Bauble Fruits")
        @Config.RequiresMcRestart
        public boolean enableBaubleFruits = true;
    }

    // ========================================================================
    // CATEGORY 2: TWEAKS & FIXES
    // ========================================================================
    public static class Tweaks {
        @Config.Comment("Fixes the Curse of Possession exploit with Corail Tombstone. Cursed items will now properly vanish on death instead of hiding in your grave. Use /restorecursed to get them back")
        @Config.Name("Enable Curse of Possession Patch")
        public boolean enableCurseOfPossessionPatch = false;

        @Config.Comment({
                "Additional effects removed by the CLEANSE effect, on top of all effects where isBeneficial() == false.",
                "Add effect IDs here if a mod's negative effect is not getting removed automatically.",
                "Example: minecraft:glowing" })
        @Config.Name("Cleanse Effect List")
        public String[] cleanseAdditionalEffects = { "srparasites:novision", "srparasites:prey", "srparasites:viral" };
    }

    // ========================================================================
    // CATEGORY 3: SKILL TRAITS
    // ========================================================================
    public static class Traits {
        @Config.Name("Fast Learner")
        public TraitConfig fastLearner = new TraitConfig(8, "attack", new String[] { "reskillable:attack|10" });

        @Config.Name("Spider's Grace")
        public TraitConfig spidersGrace = new TraitConfig(12, "defense", new String[] { "reskillable:defense|50" });

        @Config.Name("Iron Stomach")
        public TraitConfig ironStomach = new TraitConfig(6, "defense", new String[] { "reskillable:defense|15" });

        @Config.Name("Double Loot")
        public TraitConfig doubleLoot = new TraitConfig(6, "gathering", new String[] { "reskillable:gathering|10" });

        @Config.Name("Enchant Fishing")
        public TraitConfig enchantFishing = new TraitConfig(12, "gathering",
                new String[] { "reskillable:gathering|45" });

        @Config.Name("Astral Prospector")
        public TraitConfig astralProspector = new TraitConfig(15, "mining", new String[] { "reskillable:mining|45" });

        @Config.Name("Supreme Enchanter")
        public TraitConfig supremeEnchanter = new TraitConfig(15, "building",
                new String[] { "reskillable:building|45" });

        @Config.Name("Meditation")
        public TraitConfig meditation = new TraitConfig(8, "agility",
                new String[] { "reskillable:agility|25", "reskillable:magic|15" });

        @Config.Name("Arcane Mastery")
        public TraitConfig arcaneMastery = new TraitConfig(4, "magic", new String[] { "reskillable:magic|20" });

        @Config.Name("School of Alteration")
        public TraitConfig schoolOfAlteration = new TraitConfig(6, "magic", new String[] { "reskillable:magic|30" });

        @Config.Name("School of Conjuration")
        public TraitConfig schoolOfConjuration = new TraitConfig(6, "magic", new String[] { "reskillable:magic|30" });

        @Config.Name("School of Destruction")
        public TraitConfig schoolOfDestruction = new TraitConfig(6, "magic", new String[] { "reskillable:magic|30" });

        @Config.Name("Archmage")
        public TraitConfig archmage = new TraitConfig(10, "magic", new String[] { "reskillable:magic|50" });
    }

    // ========================================================================
    // TRAIT CONFIG HELPER
    // ========================================================================
    public static class TraitConfig {
        @Config.Name("SP Cost")
        @Config.Comment("Skill point cost to unlock this trait.")
        @Config.RequiresMcRestart
        public int cost;

        @Config.Name("Parent Skill Tree")
        @Config.Comment("The Reskillable skill tree this trait belongs to (e.g. magic, attack, defense).")
        @Config.RequiresMcRestart
        public String parentSkill;

        @Config.Name("Requirements")
        @Config.Comment("List of required skills and levels. Format: reskillable:skill_name|level")
        @Config.RequiresMcRestart
        public String[] requirements;

        public TraitConfig(int cost, String parentSkill, String[] requirements) {
            this.cost = cost;
            this.parentSkill = parentSkill;
            this.requirements = requirements;
        }
    }

    // ========================================================================
    // CATEGORY 4: CLIENT & DEBUG
    // ========================================================================
    public static class Client {
        @Config.Comment({ "Hides the CLEANSE effect icon and text from the HUD and inventory effect panel.",
                "Enable this flag to suppress the effect from appearing in the GUI entirely.",
                "The mechanic itself still works regardless of this setting.",
                "Intended as a temporary toggle while the effect's visuals are being refined." })
        @Config.Name("Hide Cleanse Effect from GUI")
        public boolean hideCleanseHudEffect = true;

        @Config.Comment("Should mod display info whether he found any cursed items in players inventory on death")
        @Config.Name("Display info on death")
        public boolean displayInfoOnDeath = false;

        @Config.Comment("Whether to display debug info for various mod mechanics. I highly urge you to keep it on until the mod is in its alpha version.")
        @Config.Name("Display DEBUG INFO")
        public boolean displayDebugInfo = false;
    }

    // ========================================================================
    // CONFIG SYNC (auto-saves on GUI change)
    // ========================================================================
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
