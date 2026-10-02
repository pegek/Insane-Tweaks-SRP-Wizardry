package com.spege.ebreduxaddon.feature;

import com.binaris.wizardry.api.content.spell.Spell;
import com.binaris.wizardry.core.registry.EBRegistries;
import com.spege.ebreduxaddon.EbreduxAddon;
import com.spege.ebreduxaddon.feature.spell.CleanseSpell;
import com.spege.ebreduxaddon.feature.spell.GraspSpell;
import com.spege.ebreduxaddon.feature.spell.SpineVolleySpell;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

/**
 * Zaklecia addonu w rejestrze Redux. Redux tworzy go z disableSaving(), wiec usuniete kiedys
 * zaklecie nie zostawi dziury w level.dat (inaczej niz na 1.12.2).
 *
 * <p>Liczby z properties() w klasach to tylko wartosci awaryjne: Redux nadpisuje je JSON-em
 * z data/ebreduxaddon/spells/ przy kazdym przeladowaniu danych.
 */
public final class ModSpells {

    private static final DeferredRegister<Spell> SPELLS = DeferredRegister.create(EBRegistries.SPELL, EbreduxAddon.MODID);

    public static final RegistryObject<Spell> CLEANSE = SPELLS.register("cleanse", CleanseSpell::new);
    public static final RegistryObject<Spell> GRASP = SPELLS.register("grasp", GraspSpell::new);
    public static final RegistryObject<Spell> SPINE_VOLLEY = SPELLS.register("spine_volley", SpineVolleySpell::new);

    private ModSpells() {
    }

    public static void register(IEventBus modBus) {
        SPELLS.register(modBus);
    }
}
