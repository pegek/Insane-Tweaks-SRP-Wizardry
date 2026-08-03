package com.spege.tombtweaks.util;

import javax.annotation.Nullable;

import com.spege.tombtweaks.config.TombTweaksConfig;
import com.spege.tombtweaks.config.categories.TombstoneCategory;

/**
 * Maps a native Tombstone perk's name onto its config section.
 *
 * <p>Lives outside {@code com.spege.tombtweaks.mixins} on purpose — a mixin may call a plain class,
 * but nothing that is not itself a mixin may live in that package. Both perk mixins carried their
 * own copy of this switch until the point-cost lever was added; one table is enough.
 *
 * <p>Only the ten perks Tombstone registers itself are listed. This mod's own two perks read their
 * config directly through {@code PerkTombTweaksBase}, so they are deliberately absent: a {@code null}
 * here means "not ours to cap", which is exactly right for them and for any perk a third mod adds.
 */
public final class PerkConfigLookup {

    private PerkConfigLookup() {}

    /** The config section for {@code perkName}, or {@code null} if no native perk goes by that name. */
    @Nullable
    public static TombstoneCategory.PerkConfig byName(@Nullable String perkName) {
        if (perkName == null) return null;

        TombstoneCategory ts = TombTweaksConfig.tombstone;
        switch (perkName) {
            case "alchemist":       return ts.alchemist;
            case "concentration":   return ts.concentration;
            case "gladiator":       return ts.gladiator;
            case "jailer":          return ts.jailer;
            case "memento_mori":    return ts.mementoMori;
            case "rune_inscriber":  return ts.runeInscriber;
            case "scribe":          return ts.scribe;
            case "shadow_walker":   return ts.shadowWalker;
            case "treasure_seeker": return ts.treasureSeeker;
            case "witch_doctor":    return ts.witchDoctor;
            default:                return null;
        }
    }
}
