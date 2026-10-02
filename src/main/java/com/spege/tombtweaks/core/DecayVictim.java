package com.spege.tombtweaks.core;

import java.util.List;
import java.util.function.IntUnaryOperator;

/**
 * Ktory zajety slot rozkladajacy sie grob oddaje nastepny.
 *
 * <p>Dwie listy kandydatow, nie jedna. Stacki niechronione sa zjadane pierwsze; chronione
 * sa w ogole nieosiagalne, dopoki zostalo cokolwiek innego. Gdy zostaly tylko one, decyduje
 * {@code neverDecay}: prawda = ochrona jest zwolnieniem (grob staje w miejscu),
 * falsz = ochrona byla tylko kolejnoscia.
 */
public final class DecayVictim {

    public static final int NOTHING = -1;

    private DecayVictim() {
    }

    /**
     * @param unprotected indeksy slotow ze stackiem niechronionym
     * @param guarded     indeksy slotow ze stackiem chronionym
     * @param neverDecay  czy ochrona jest zwolnieniem, czy tylko kolejnoscia
     * @param random      bound -&gt; wartosc z przedzialu [0, bound); wartosc spoza przedzialu
     *                    jest zawijana, nie rzucana - to dziala w ticku serwera grobu,
     *                    a wyjatek tam wywalilby serwer
     */
    public static int pick(List<Integer> unprotected, List<Integer> guarded,
                           boolean neverDecay, IntUnaryOperator random) {
        List<Integer> pool = unprotected;
        if (pool.isEmpty()) {
            if (guarded.isEmpty() || neverDecay) {
                return NOTHING;
            }
            pool = guarded;
        }
        return pool.get(Math.floorMod(random.applyAsInt(pool.size()), pool.size())).intValue();
    }
}
