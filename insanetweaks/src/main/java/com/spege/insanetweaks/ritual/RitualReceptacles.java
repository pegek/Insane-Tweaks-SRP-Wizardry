package com.spege.insanetweaks.ritual;

/**
 * The receptacle rule of the 3x3 Imbuement Altar ritual, kept free of Minecraft types so it can be
 * unit-tested.
 *
 * <p>A ritual needs four receptacles, one hanging on the outer face of each side's middle altar, all
 * four lit, and at least one of them burning Abomination. {@link RitualAltarManager} reads the four
 * receptacles in N, E, S, W order and hands their element ordinals here.
 */
public final class RitualReceptacles {

    /** Ordinal stand-in for a receptacle that holds no element. */
    public static final int DARK = -1;

    private static final int COUNT = 4;

    private RitualReceptacles() {
    }

    /**
     * @param ordinals           the four receptacles' element ordinals, {@link #DARK} for an empty one
     * @param abominationOrdinal the Abomination element's ordinal; negative when the element is not
     *                           registered, in which case nothing can ever be lit
     */
    public static boolean lit(int[] ordinals, int abominationOrdinal) {
        if (ordinals == null || ordinals.length != COUNT || abominationOrdinal < 0) {
            return false;
        }
        boolean abomination = false;
        for (int i = 0; i < COUNT; i++) {
            if (ordinals[i] == DARK) {
                return false;
            }
            if (ordinals[i] == abominationOrdinal) {
                abomination = true;
            }
        }
        return abomination;
    }
}
