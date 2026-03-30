package com.spege.insanetweaks.init;

import com.spege.insanetweaks.InsaneTweaksMod;
import com.spege.insanetweaks.spells.SpellSummonPig;
import com.spege.insanetweaks.spells.SpellTestProjectile;
import electroblob.wizardry.spell.Spell;
import net.minecraftforge.event.RegistryEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.registries.IForgeRegistry;

@Mod.EventBusSubscriber(modid = InsaneTweaksMod.MODID)
public class ModSpells {

    public static final Spell SUMMON_PIG = new SpellSummonPig();
    public static final Spell TEST_PROJECTILE = new SpellTestProjectile();

    @SubscribeEvent
    public static void registerSpells(RegistryEvent.Register<Spell> event) {
        IForgeRegistry<Spell> registry = event.getRegistry();
        registry.register(SUMMON_PIG);
        registry.register(TEST_PROJECTILE);
    }
}
