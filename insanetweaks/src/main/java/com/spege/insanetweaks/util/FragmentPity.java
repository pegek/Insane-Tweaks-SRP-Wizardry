package com.spege.insanetweaks.util;

/**
 * Corrupted Seed Fragment drop with bad-luck protection: a normal roll, plus a guaranteed drop on
 * the {@code pityKills}-th eligible kill in a row without one. Pure arithmetic; the per-player
 * counter itself lives in persisted player NBT ({@code CorruptedFragmentDropHandler}).
 */
public final class FragmentPity {

    private FragmentPity() {
    }

    /**
     * @param roll           a uniform [0,1) roll
     * @param dryKillsBefore eligible kills since the last drop, not counting this one
     * @param pityKills      0 disables the guarantee
     */
    public static boolean drops(double roll, double chance, int dryKillsBefore, int pityKills) {
        if (roll < chance) {
            return true;
        }
        return pityKills > 0 && dryKillsBefore + 1 >= pityKills;
    }

    public static int nextDryKills(int dryKillsBefore, boolean dropped) {
        return dropped ? 0 : dryKillsBefore + 1;
    }
}
