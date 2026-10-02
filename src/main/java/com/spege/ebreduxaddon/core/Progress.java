package com.spege.ebreduxaddon.core;

/** Liczniki postepu sprzetu: dodawanie bez przepelnienia i decyzja o ewolucji. */
public final class Progress {

    private Progress() {
    }

    /** current + add, nasycone na Long.MAX_VALUE. Ujemne i NaN-owe przyrosty sa ignorowane. */
    public static long add(long current, double add) {
        long base = Math.max(0L, current);
        if (!(add > 0.0)) {
            return base;
        }
        double sum = (double) base + Math.floor(add);
        return sum >= (double) Long.MAX_VALUE ? Long.MAX_VALUE : (long) sum;
    }

    /** Prog &lt;= 0 znaczy "ewolucja wylaczona". */
    public static boolean shouldEvolve(long points, long evolveAt) {
        return evolveAt > 0 && points >= evolveAt;
    }

    /**
     * Czesc obrazen przypadajaca na jedna z {@code pieces} noszonych czesci. Zero czesci albo
     * obrazenia &lt;= 0 daja 0.
     */
    public static double share(double damage, int pieces) {
        if (pieces <= 0 || !(damage > 0.0) || Double.isInfinite(damage)) {
            return 0.0;
        }
        return damage / pieces;
    }
}
