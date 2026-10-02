package com.spege.tombtweaks.core;

import java.util.Set;

/**
 * Co core wie o jednym stacku. Implementowane w warstwie platform, zeby core
 * nie musial znac ItemStacka.
 */
public interface StackView {

    /** Nazwa rejestrowa, np. "minecraft:diamond_sword". Nigdy null. */
    String itemId();

    /** Nazwy rejestrowe enchantow stacka. Pusty zbior gdy brak. */
    Set<String> enchantmentIds();

    /** Wartosc stringowego klucza NBT najwyzszego poziomu, albo null gdy go nie ma. */
    String nbtString(String key);
}
