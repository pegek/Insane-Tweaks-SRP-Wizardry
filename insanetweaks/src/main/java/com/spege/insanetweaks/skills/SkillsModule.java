package com.spege.insanetweaks.skills;

import codersafterdark.reskillable.api.unlockable.Unlockable;
import net.minecraftforge.event.RegistryEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

public class SkillsModule {

    /** All custom traits. Populated lazily. */
    public static final java.util.List<Object> TRAITS = new java.util.ArrayList<>();

    // Keep "compatskills" domain for traits so we don't break existing player saves!
    public static final String DOMAIN = "compatskills";

    // Registration of traits is now handled inside the registerUnlockables event
    // to avoid ClassNotFoundException when Reskillable is missing.
    
    private static void initTraits() {
        if (!TRAITS.isEmpty()) return; // Already initialized

        // Attack Tree
        new TraitFastLearner();
        new TraitAssimilatedWarfare();

        // Defense Tree
        new TraitSpidersGrace();
        new TraitIronStomach();
        new TraitScarredFlesh();

        // Gathering Tree
        new TraitDoubleLoot();
        new TraitEnchantFishing();

        // Mining Tree
        new TraitAstralProspector();
        new TraitStoneFists();

        // Farming Tree
        new TraitAngryFarmer();
        new TraitAdaptedVegetation();

        // Building Tree
        new TraitSupremeEnchanter();
        new TraitBobTheBuilder();

        // Agility Tree
        new TraitMeditation();
        new TraitCoiledSpring();

        // Magic Tree
        new TraitArcaneMastery();
        new TraitSchoolAlteration();
        new TraitSchoolConjuration();
        new TraitSchoolDestruction();
        new TraitArchmage();

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

    @Mod.EventBusSubscriber(modid = com.spege.insanetweaks.InsaneTweaksMod.MODID)
    public static class RegistryHandler {
        @SubscribeEvent
        public static void registerUnlockables(RegistryEvent.Register<Unlockable> event) {
            if (net.minecraftforge.fml.common.Loader.isModLoaded("reskillable") 
                    && com.spege.insanetweaks.config.ModConfig.modules.enableSkillsModule) {
                
                initTraits(); // Populate the list
                
                for (Object trait : TRAITS) {
                    if (trait instanceof Unlockable) {
                        event.getRegistry().register((Unlockable) trait);
                    }
                }
                
                System.out.println("[Insane Tweaks] Registered " + TRAITS.size() + " custom Reskillable traits.");
            }
        }
    }

}
