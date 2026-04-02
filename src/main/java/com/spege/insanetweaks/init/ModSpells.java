package com.spege.insanetweaks.init;

import com.spege.insanetweaks.InsaneTweaksMod;
import com.spege.insanetweaks.config.ModConfig;
import com.spege.insanetweaks.spells.SpellSummonFerCow;
import com.spege.insanetweaks.spells.SpellPurifyingPulse;
import com.spege.insanetweaks.spells.SpellSummonPrimitiveSummoner;
import com.spege.insanetweaks.spells.SpellSummonPrimitiveYelloweye;
import com.spege.insanetweaks.spells.SpellTestProjectile;
import electroblob.wizardry.spell.Spell;
import net.minecraftforge.event.RegistryEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.registries.IForgeRegistry;

@Mod.EventBusSubscriber(modid = InsaneTweaksMod.MODID)
public class ModSpells {

    public static final Spell SUMMON_FER_COW = new SpellSummonFerCow();
    public static final Spell SUMMON_PRIMITIVE_SUMMONER = new SpellSummonPrimitiveSummoner();
    public static final Spell SUMMON_PRIMITIVE_YELLOWEYE = new SpellSummonPrimitiveYelloweye();
    public static final Spell PURIFYING_PULSE = new SpellPurifyingPulse();
    public static final Spell TEST_PROJECTILE = new SpellTestProjectile();

    @SubscribeEvent
    public static void registerSpells(RegistryEvent.Register<Spell> event) {
        if (!ModConfig.modules.enableSpells) {
            return;
        }

        IForgeRegistry<Spell> registry = event.getRegistry();
        registry.register(SUMMON_FER_COW);
        registry.register(SUMMON_PRIMITIVE_SUMMONER);
        registry.register(SUMMON_PRIMITIVE_YELLOWEYE);
        registry.register(PURIFYING_PULSE);
        registry.register(TEST_PROJECTILE);
    }
}
