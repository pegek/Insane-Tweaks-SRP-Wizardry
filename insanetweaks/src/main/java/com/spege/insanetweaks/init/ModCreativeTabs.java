package com.spege.insanetweaks.init;

import javax.annotation.Nonnull;

import com.spege.insanetweaks.items.nunchaku.DragonsteelNunchakuItems;

import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

/**
 * Zakładki kreatywne moda. Do tej pory nie było żadnej — wszystko lądowało w zakładkach vanilli.
 *
 * <p>{@link #BETTER_SURVIVAL_WEAPONS} zbiera linię broni budowanych na Better Survival: dziś trzy
 * dragonsteelowe nunchaku, docelowo warianty Living/Sentient i kolejne typy broni dragonsteel.
 *
 * <p>🚨 Ta klasa jest ŁADOWANA LENIWIE i tak ma zostać. Konstruktor {@code CreativeTabs} dopisuje
 * się do globalnej tablicy zakładek, więc samo dotknięcie klasy tworzy zakładkę w GUI. Dotykamy jej
 * wyłącznie z {@code DragonsteelNunchakuItems.register()}, czyli pod strażą {@code available()} —
 * bez Better Survival i RLDragonsteel pusta zakładka nigdy nie powstaje.
 */
public final class ModCreativeTabs {

    /**
     * Broń oparta o Better Survival. Etykieta wchodzi do klucza tłumaczenia jako
     * {@code itemGroup.<etykieta>}.
     *
     * <p>UWAGA na klucz w .lang: 1.12.2 składa go w {@code CreativeTabs.getTranslatedTabLabel()}
     * jako {@code "itemGroup." + etykieta} — BEZ końcówki {@code .name} (ta jest dopiero od 1.14).
     * Vanilla ma dokładnie {@code itemGroup.combat=Combat}.
     */
    public static final CreativeTabs BETTER_SURVIVAL_WEAPONS = new CreativeTabs("insanetweaks_weapons") {
        @Override
        @Nonnull
        public ItemStack getTabIconItem() {
            Item icon = DragonsteelNunchakuItems.icon();
            // Fallback nieosiągalny w praktyce - zakładkę tworzymy dopiero po rejestracji broni -
            // ale getTabIconItem woła GUI, a null ItemStack to crash renderu.
            return icon == null ? new ItemStack(Items.STICK) : new ItemStack(icon);
        }
    };

    private ModCreativeTabs() {
    }
}
