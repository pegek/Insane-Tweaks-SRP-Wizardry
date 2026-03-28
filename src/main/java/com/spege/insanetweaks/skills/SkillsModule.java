package com.spege.insanetweaks.skills;

import codersafterdark.reskillable.api.unlockable.Unlockable;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.event.RegistryEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

import java.util.ArrayList;
import java.util.List;

@Mod.EventBusSubscriber
public class SkillsModule {

    public static final List<Unlockable> TRAITS = new ArrayList<>();

    // Keep "compatskills" domain for traits so we don't break existing player saves!
    public static final String DOMAIN = "compatskills";

    // Attack Tree
    public static final Unlockable FAST_LEARNER = new TraitFastLearner();

    // Defense Tree
    public static final Unlockable SPIDERS_GRACE = new TraitSpidersGrace();
    public static final Unlockable IRON_STOMACH = new TraitIronStomach();

    // Gathering Tree
    public static final Unlockable DOUBLE_LOOT = new TraitDoubleLoot();
    public static final Unlockable ENCHANT_FISHING = new TraitEnchantFishing();

    // Mining Tree
    public static final Unlockable ASTRAL_PROSPECTOR = new TraitAstralProspector();

    // Building Tree
    public static final Unlockable SUPREME_ENCHANTER = new TraitSupremeEnchanter();

    // Agility Tree
    public static final Unlockable MEDITATION = new TraitMeditation();

    // Magic Tree
    public static final Unlockable ARCANE_MASTERY = new TraitArcaneMastery();
    public static final Unlockable SCHOOL_ALTERATION = new TraitSchoolAlteration();
    public static final Unlockable SCHOOL_CONJURATION = new TraitSchoolConjuration();
    public static final Unlockable SCHOOL_DESTRUCTION = new TraitSchoolDestruction();
    public static final Unlockable POWER_CREEP = new TraitPowerCreep();

    @SubscribeEvent
    public static void registerUnlockables(RegistryEvent.Register<Unlockable> event) {
        // Register all traits mapped from our List
        for (Unlockable trait : TRAITS) {
            event.getRegistry().register(trait);
        }
        System.out.println("[Insane Tweaks] Registered " + TRAITS.size() + " custom Reskillable traits.");
    }

}
