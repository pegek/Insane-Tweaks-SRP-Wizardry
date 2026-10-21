package com.spege.insanetweaks.ritual;

/**
 * Where the 3x3 ritual may run: only in a conquered Sunken Arcane Sanctum. Kept free of Minecraft
 * types so it can be unit-tested; {@link RitualAltarManager} reads the facts from
 * {@code SanctumWorldData} and asks here.
 *
 * <p>Narrowest known place wins:
 * <ol>
 * <li>no sanctum at this x/z, or one not yet cleared - refused;</li>
 * <li>the sanctum recorded its dais - only that exact centre altar;</li>
 * <li>no dais recorded but a hull height (sanctums from 1.28.0) - anywhere inside that height, which
 *     already rules out a base built on the surface above it;</li>
 * <li>neither (records older than 1.28.0, test worlds only) - the footprint alone.</li>
 * </ol>
 */
public final class RitualSanctumGate {

    private RitualSanctumGate() {
    }

    /**
     * @param found   whether a sanctum's footprint contains the centre
     * @param cleared whether that sanctum is cleared; ignored when {@code found} is false
     * @param dais    x, y, z of its recorded dais centre, or null
     * @param hullY   its hull's min and max Y, or null
     */
    public static boolean allows(boolean found, boolean cleared, int[] dais, int[] hullY, int x, int y, int z) {
        if (!found || !cleared) {
            return false;
        }
        if (dais != null) {
            return dais[0] == x && dais[1] == y && dais[2] == z;
        }
        if (hullY != null) {
            return y >= hullY[0] && y <= hullY[1];
        }
        return true;
    }
}
