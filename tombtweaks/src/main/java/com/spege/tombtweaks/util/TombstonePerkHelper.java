package com.spege.tombtweaks.util;

import java.util.HashMap;
import java.util.Map;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.common.Loader;

/**
 * Reads a player's level in one of this mod's Corail Tombstone perks, from code that may run
 * without Tombstone installed.
 *
 * <p>That last part is the whole reason this class exists. The Enigmatic Legacy mixins behind
 * "Relief for the Damned" apply whenever Enigmatic Legacy is present — Tombstone has no say in
 * it. Without isolation, a pack with Enigmatic Legacy but no Tombstone would hit a
 * {@code NoClassDefFoundError} the first time the Cursed Ring dealt damage.
 *
 * <p>So every Tombstone type is confined to the nested {@link Holder}, which is only ever
 * classloaded after {@link #AVAILABLE} has been checked. With Tombstone absent this returns 0
 * for everything and the mixins become transparent.
 *
 * <p>Deliberately NOT in a {@code mixins.*} package: classes referenced from inside a mixin
 * package throw {@code IllegalClassLoadError}.
 */
public final class TombstonePerkHelper {

    /**
     * Registry name of the perk that feeds on parasite kills.
     *
     * <p>The {@code insanetweaks} namespace is not a leftover — see
     * {@code TombstonePerks#LEGACY_NAMESPACE}: both perks keep the name they were first registered
     * under so existing worlds keep their invested levels.
     */
    public static final String PERK_ASSIMILATED_KNOWLEDGE = "insanetweaks:assimilated_knowledge";

    /** Registry name of the perk that softens the Ring of the Seven Curses. Same namespace note. */
    public static final String PERK_RELIEF_FOR_THE_DAMNED = "insanetweaks:relief_for_the_damned";

    private static final boolean AVAILABLE = Loader.isModLoaded("tombstone");

    private TombstonePerkHelper() {
    }

    /** True when Corail Tombstone is installed, so perks exist at all. */
    public static boolean isAvailable() {
        return AVAILABLE;
    }

    /**
     * The player's level in the named perk, including any Tombstone level bonus, or 0 when
     * Tombstone is absent, the perk is disabled, or anything at all goes wrong.
     */
    public static int getPerkLevel(EntityPlayer player, String perkRegistryName) {
        if (!AVAILABLE || player == null) {
            return 0;
        }
        try {
            return Holder.getLevel(player, perkRegistryName);
        } catch (Throwable t) {
            // A broken lookup must never take damage handling down with it.
            return 0;
        }
    }

    /**
     * Everything below touches Tombstone classes and is therefore loaded lazily, on the first
     * call that already knows Tombstone is present.
     */
    private static final class Holder {

        private static final Map<String, ovh.corail.tombstone.api.capability.Perk> CACHE =
                new HashMap<String, ovh.corail.tombstone.api.capability.Perk>();

        private Holder() {
        }

        static int getLevel(EntityPlayer player, String perkRegistryName) {
            ovh.corail.tombstone.api.capability.Perk perk = resolve(perkRegistryName);
            if (perk == null) {
                return 0;
            }
            ovh.corail.tombstone.api.capability.ITBCapability cap = player.getCapability(
                    ovh.corail.tombstone.capability.TBCapabilityProvider.TB_CAPABILITY, null);
            if (cap == null) {
                return 0;
            }
            return cap.getPerkLevelWithBonus(player, perk);
        }

        private static ovh.corail.tombstone.api.capability.Perk resolve(String perkRegistryName) {
            if (CACHE.containsKey(perkRegistryName)) {
                return CACHE.get(perkRegistryName);
            }
            ovh.corail.tombstone.api.capability.Perk perk = null;
            net.minecraftforge.registries.IForgeRegistry<ovh.corail.tombstone.api.capability.Perk> registry =
                    net.minecraftforge.fml.common.registry.GameRegistry
                            .findRegistry(ovh.corail.tombstone.api.capability.Perk.class);
            if (registry != null) {
                perk = registry.getValue(new ResourceLocation(perkRegistryName));
            }
            // Cache misses too: the registry does not change after startup, and a null here
            // just means the perk was never registered.
            CACHE.put(perkRegistryName, perk);
            return perk;
        }
    }
}
