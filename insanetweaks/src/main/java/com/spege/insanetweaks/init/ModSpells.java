package com.spege.insanetweaks.init;

import com.spege.insanetweaks.InsaneTweaksMod;
import com.spege.insanetweaks.config.ModConfig;
import com.spege.insanetweaks.spells.SpellSummonWizard;
import com.spege.insanetweaks.spells.SpellSummonFerCow;
import com.spege.insanetweaks.spells.SpellPurifyingPulse;
import com.spege.insanetweaks.spells.SpellParasiteShroud;
import com.spege.insanetweaks.spells.SpellSummonPrimitiveSummoner;
import com.spege.insanetweaks.spells.SpellSummonLightBomber;
import com.spege.insanetweaks.spells.SpellSummonPrimitiveYelloweye;
import com.spege.insanetweaks.spells.SpellCallOfDemise;
import com.spege.insanetweaks.spells.SpellImmuneBond;
import com.spege.insanetweaks.spells.SpellSummonThrall;
import com.spege.insanetweaks.spells.SpellCleanse;
import com.spege.insanetweaks.spells.SpellDispatcherGrasp;
import com.spege.insanetweaks.spells.SpellYelloweyeGland;
import electroblob.wizardry.spell.Spell;
import net.minecraftforge.event.RegistryEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.registries.IForgeRegistry;

@Mod.EventBusSubscriber(modid = InsaneTweaksMod.MODID)
public class ModSpells {

    public static final Spell SUMMON_WIZARD = new SpellSummonWizard();
    public static final Spell SUMMON_FER_COW = new SpellSummonFerCow();
    public static final Spell SUMMON_PRIMITIVE_SUMMONER = new SpellSummonPrimitiveSummoner();
    public static final Spell SUMMON_PRIMITIVE_YELLOWEYE = new SpellSummonPrimitiveYelloweye();
    public static final Spell SUMMON_LIGHT_BOMBER = new SpellSummonLightBomber();
    public static final Spell PURIFYING_PULSE = new SpellPurifyingPulse();
    public static final Spell CALL_OF_DEMISE = new SpellCallOfDemise();
    public static final Spell YELLOWEYE_GLAND = new SpellYelloweyeGland();
    public static final Spell PARASITE_SHROUD = new SpellParasiteShroud();
    public static final Spell IMMUNE_BOND = new SpellImmuneBond();
    public static final Spell SUMMON_THRALL = new SpellSummonThrall();
    public static final Spell CLEANSE = new SpellCleanse();
    public static final Spell DISPATCHER_GRASP = new SpellDispatcherGrasp();

    @SubscribeEvent
    public static void registerSpells(RegistryEvent.Register<Spell> event) {
        if (!ModConfig.modules.enableSpells) {
            return;
        }

        IForgeRegistry<Spell> registry = event.getRegistry();
        registry.register(SUMMON_WIZARD);
        registry.register(SUMMON_FER_COW);
        registry.register(SUMMON_PRIMITIVE_SUMMONER);
        registry.register(SUMMON_PRIMITIVE_YELLOWEYE);
        registry.register(SUMMON_LIGHT_BOMBER);
        registry.register(PURIFYING_PULSE);
        registry.register(CALL_OF_DEMISE);
        registry.register(YELLOWEYE_GLAND);
        registry.register(PARASITE_SHROUD);
        registry.register(IMMUNE_BOND);
        registry.register(SUMMON_THRALL);
        registry.register(CLEANSE);
        registry.register(DISPATCHER_GRASP);
    }

    /**
     * Retired spells, still present in the id map of every world that saw them.
     *
     * <p>Wizardry's spell registry never calls {@code disableSaving()}, so a removed spell leaves
     * its name in {@code level.dat} and Forge shows the missing-entries screen on load.
     * {@code ignore()}, not {@code remap()}: ignoring leaves the numeric slot dead instead of
     * reassigning it, and {@code ItemSpellBook} / {@code ItemScroll} store that number as their
     * metadata - shifting it would turn existing books into different spells.
     */
    private static final java.util.Set<String> RETIRED = java.util.Collections.singleton(
            "test_projectile"); // dev-only fireball, every source flag off; removed in 1.20.3

    @SubscribeEvent
    public static void onMissingSpells(RegistryEvent.MissingMappings<Spell> event) {
        for (RegistryEvent.MissingMappings.Mapping<Spell> m : event.getMappings()) {
            if (InsaneTweaksMod.MODID.equals(m.key.getResourceDomain())
                    && RETIRED.contains(m.key.getResourcePath())) {
                m.ignore();
            }
        }
    }
}
