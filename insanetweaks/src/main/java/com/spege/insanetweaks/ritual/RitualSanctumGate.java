package com.spege.insanetweaks.ritual;

/**
 * Where the 3x3 ritual may run: only inside a conquered Sunken Arcane Sanctum. Kept free of
 * Minecraft types so it can be unit-tested; {@link RitualAltarManager} reads the facts from
 * {@code SanctumWorldData} and asks here.
 *
 * <ol>
 * <li>no sanctum at this x/z, or one not yet cleared - refused;</li>
 * <li>the sanctum knows its hull height (sanctums from 1.28.0) - anywhere inside that height. Any
 *     circle in the conquered sanctum counts, the generated dais or one built there from crafted
 *     altars (user decision 2026-10-21: the gate may be loose), but a base on the surface above it
 *     does not;</li>
 * <li>no hull height (records older than 1.28.0, test worlds only) - the footprint alone.</li>
 * </ol>
 */
public final class RitualSanctumGate {

    private RitualSanctumGate() {
    }

    /**
     * @param found   whether a sanctum's footprint contains the centre
     * @param cleared whether that sanctum is cleared; ignored when {@code found} is false
     * @param hullY   its hull's min and max Y, or null
     * @param y       the centre altar's Y
     */
    public static boolean allows(boolean found, boolean cleared, int[] hullY, int y) {
        if (!found || !cleared) {
            return false;
        }
        return hullY == null || (y >= hullY[0] && y <= hullY[1]);
    }
}
