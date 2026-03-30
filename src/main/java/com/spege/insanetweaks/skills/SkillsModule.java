package com.spege.insanetweaks.skills;

import codersafterdark.reskillable.api.unlockable.Unlockable;
import net.minecraft.util.ResourceLocation;
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

        // Gathering Tree
        new TraitDoubleLoot();
        new TraitEnchantFishing();

        // Mining Tree
        new TraitAstralProspector();

        // Farming Tree
        new TraitAngryFarmer();
        new TraitAdaptedVegetation();

        // Building Tree
        new TraitSupremeEnchanter();
        new TraitBobTheBuilder();

        // Agility Tree
        new TraitMeditation();

        // Magic Tree
        new TraitArcaneMastery();
        new TraitSchoolAlteration();
        new TraitSchoolConjuration();
        new TraitSchoolDestruction();
        new TraitArchmage();
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
