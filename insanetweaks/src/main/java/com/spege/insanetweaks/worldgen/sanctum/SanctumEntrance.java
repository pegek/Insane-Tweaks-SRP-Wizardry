package com.spege.insanetweaks.worldgen.sanctum;

import java.util.function.IntPredicate;

/**
 * Where the entrance shaft ends in a dimension with buildings over the ground (Lost Cities): the
 * first floor at or above the street level that has two blocks of headroom - a street, or a
 * building's ground floor - instead of the measured top, which there is a roof. Pure Java.
 */
public final class SanctumEntrance {

    private SanctumEntrance() {
    }

    /**
     * @param street   lowest y a street can be at in this dimension
     * @param measured the column's measured top solid block
     * @param solid    whether the block at a y blocks movement
     * @return the y of the floor block the shaft ends in
     */
    public static int topY(int street, int measured, IntPredicate solid) {
        for (int y = street; y <= measured; y++) {
            if (solid.test(y) && !solid.test(y + 1) && !solid.test(y + 2)) {
                return y;
            }
        }
        return measured;
    }
}
