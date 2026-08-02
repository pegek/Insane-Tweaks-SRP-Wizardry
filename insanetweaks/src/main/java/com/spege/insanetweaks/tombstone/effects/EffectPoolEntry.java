package com.spege.insanetweaks.tombstone.effects;

import net.minecraft.potion.Potion;

/** One resolved whitelist entry: {@code modid:effect[;weight][;maxAmplifier]}. */
public class EffectPoolEntry {

    /** Never null — an entry whose registry name did not resolve is dropped at parse time. */
    public final Potion potion;

    /** Relative draw weight. Always >= 1; a missing or unparsable weight defaults to 1. */
    public final int weight;

    /** Upper clamp on the amplifier Tombstone rolled, or {@code -1} to leave it alone. */
    public final int maxAmplifier;

    public EffectPoolEntry(Potion potion, int weight, int maxAmplifier) {
        this.potion = potion;
        this.weight = weight;
        this.maxAmplifier = maxAmplifier;
    }

    /** Applies {@link #maxAmplifier} to an amplifier Tombstone's own level function produced. */
    public int clampAmplifier(int rolled) {
        if (maxAmplifier < 0) {
            return rolled;
        }
        return Math.min(rolled, maxAmplifier);
    }
}
