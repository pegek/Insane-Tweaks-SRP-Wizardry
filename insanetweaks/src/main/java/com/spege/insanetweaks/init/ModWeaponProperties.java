package com.spege.insanetweaks.init;

import com.oblivioussp.spartanweaponry.api.weaponproperty.WeaponProperty;
import com.spege.insanetweaks.items.spellblade.property.ParasiteBleedingProperty;
import com.spege.insanetweaks.items.spellblade.property.ParasiteHeavyProperty;

/**
 * Central registry of InsaneTweaks weapon properties.
 *
 * BLEEDING / HEAVY / UNCAPPED were previously pulled from swparasites
 * ({@code com.existingeevee.swparasites.init.ParasiteSWProperties}). They are now local
 * re-implementations so the mod no longer compile-depends on SW: Parasites. They still rely
 * only on the SpartanWeaponry base API ({@code spartanweaponry}) and srparasites, both of
 * which remain hard dependencies. The {@code type} strings match the originals
 * ({@code "bleeding"}, {@code "heavy"}, {@code "uncapped"}) so existing tooltip handling
 * is unchanged.
 */
public class ModWeaponProperties {

    // FLESHBOUND used to live here as a WeaponProperty. It was removed because no item ever added it
    // to its property list, so the branch in FleshboundEventHandler that looked for it could not
    // fire - Fleshbound has only ever reached a weapon through the 'grip' advanced property.

    /** 3 rolls of 25% chance to apply srparasites BLEED_E (replaces ParasiteSWProperties.BLEEDING_3). */
    public static final ParasiteBleedingProperty BLEEDING_3 = new ParasiteBleedingProperty(3);

    /** -50% attack speed while held (replaces ParasiteSWProperties.HEAVY_2). */
    public static final ParasiteHeavyProperty HEAVY_2 = new ParasiteHeavyProperty(true);

    /** Pure display marker (replaces ParasiteSWProperties.UNCAPPED). No callback, no behaviour. */
    public static final WeaponProperty UNCAPPED = new WeaponProperty("uncapped", "insanetweaks", 0, 0.0f);
}
