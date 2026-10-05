package com.spege.reskilltweaks.skills;

import codersafterdark.reskillable.api.unlockable.Unlockable;
import net.minecraftforge.event.RegistryEvent;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

public class SkillsModule {

    /** All custom traits. Populated lazily. */
    public static final java.util.List<Object> TRAITS = new java.util.ArrayList<>();

    // Keep "compatskills" domain for traits so we don't break existing player saves!
    public static final String DOMAIN = "compatskills";

    /** Mod ids that decide which traits exist at all - see {@link #initTraits()}. */
    public static final String WIZARDRY = "ebwizardry";
    public static final String PARASITES = "srparasites";
    public static final String INSANE_TWEAKS = "insanetweaks";

    // Registration of traits is now handled inside the registerUnlockables event
    // to avoid ClassNotFoundException when Reskillable is missing.
    
    private static void initTraits() {
        if (!TRAITS.isEmpty()) return; // Already initialized

        // Traits tied to another mod are only constructed when that mod is loaded. Constructing is
        // what puts a trait into TRAITS (TraitBase), and TRAITS is the only thing that reaches
        // Reskillable's registry - so a skipped trait is absent from the skill tree and every
        // TraitHandle check for it answers false. Tree positions are fixed per trait, so a gap is
        // just an empty slot; nothing else moves.
        //
        // 🚨 A trait that disappears this way takes its unlock with it: Reskillable drops an
        // unresolvable unlock from player data without a word and does not refund the points.
        // Harmless on a fresh world, but removing Wizardry or SRP from a running world does it.
        boolean wizardry = Loader.isModLoaded(WIZARDRY);
        boolean parasites = Loader.isModLoaded(PARASITES);
        boolean insaneTweaks = Loader.isModLoaded(INSANE_TWEAKS);

        // Attack Tree
        new TraitFastLearner();
        if (insaneTweaks) {
            // The effect is Insane Tweaks' parasite XP fallback, reached through its TraitGate.
            new TraitAssimilatedWarfare();
        }

        // Defense Tree
        new TraitSpidersGrace();
        new TraitIronStomach();
        if (parasites) {
            new TraitScarredFlesh();
        }

        // Gathering Tree
        new TraitDoubleLoot();
        new TraitEnchantFishing();

        // Mining Tree
        new TraitAstralProspector();
        new TraitStoneFists();

        // Farming Tree
        new TraitAngryFarmer();
        if (parasites) {
            // Only answers to SRP's Living/Sentient Scythe, and undoes a crop block SRP imposes.
            new TraitAdaptedVegetation();
        }

        // Building Tree
        new TraitSupremeEnchanter();
        new TraitBobTheBuilder();

        // Agility Tree
        if (wizardry) {
            // Pays out in Wizardry mana (or ManaCore's pool, which itself needs Wizardry).
            new TraitMeditation();
        }
        if (insaneTweaks) {
            // The charge jump is Insane Tweaks' - its packet lives on that mod's channel.
            new TraitCoiledSpring();
        }

        // Magic Tree
        if (wizardry) {
            new TraitArcaneMastery();
            new TraitSchoolAlteration();
            new TraitSchoolConjuration();
            new TraitSchoolDestruction();
            new TraitArchmage();
        }

        logSkipped(wizardry, "Electroblob's Wizardry", "Meditation, Arcane Mastery, the three schools, Archmage");
        logSkipped(parasites, "Scape and Run: Parasites", "Scarred Flesh, Adapted Vegetation");
        logSkipped(insaneTweaks, "Insane Tweaks", "Assimilated Warfare, Coiled Spring");

        // Native Overwrites
        // TraitGoldenOsmosisBuffed used to be listed here, commented out. It was deleted on
        // 2026-08-04: it was a functional duplicate of Reskillable's own TraitGoldenOsmosis, which
        // already repairs mainhand, offhand AND armour with the same rule (damage > 2, repairable
        // with a gold ingot, 1 XP per 3 durability). It also registered under
        // compatskills:golden_osmosis rather than reskillable:golden_osmosis, so it would have been
        // a second buyable copy of the trait rather than an override.
        //
        // Our actual additions on top of the native trait - attack speed, armour and toughness -
        // live in EventHandlerSkills.onLivingUpdate and correctly gate on TraitHandle.GOLDEN_OSMOSIS,
        // i.e. the native id. They are unaffected by this deletion.
    }

    private static void logSkipped(boolean present, String modName, String traits) {
        if (!present) {
            com.spege.reskilltweaks.ReskillTweaks.LOGGER.info(
                    "[ReskillTweaks] {} not installed - its traits are left out of the skill tree: {}.",
                    modName, traits);
        }
    }

    @Mod.EventBusSubscriber(modid = com.spege.reskilltweaks.ReskillTweaks.MODID)
    public static class RegistryHandler {
        @SubscribeEvent
        public static void registerUnlockables(RegistryEvent.Register<Unlockable> event) {
            if (net.minecraftforge.fml.common.Loader.isModLoaded("reskillable") 
                    && com.spege.reskilltweaks.config.ReskillTweaksConfig.modules.enableSkillsModule) {
                
                initTraits(); // Populate the list
                
                for (Object trait : TRAITS) {
                    if (trait instanceof Unlockable) {
                        event.getRegistry().register((Unlockable) trait);
                    }
                }
                
                com.spege.reskilltweaks.ReskillTweaks.LOGGER.info("[ReskillTweaks] Registered {} custom Reskillable traits.", TRAITS.size());
            }
        }
    }

}
