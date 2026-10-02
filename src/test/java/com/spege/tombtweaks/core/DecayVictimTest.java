package com.spege.tombtweaks.core;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.IntUnaryOperator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DecayVictimTest {

    /** Deterministyczna "losowosc": zawsze pierwszy element. */
    private static final IntUnaryOperator FIRST = bound -> 0;
    /** Zawsze ostatni. */
    private static final IntUnaryOperator LAST = bound -> bound - 1;

    private static List<Integer> none() {
        return new ArrayList<Integer>();
    }

    @Test
    void pustyGrobNieOddajeNiczego() {
        assertEquals(DecayVictim.NOTHING, DecayVictim.pick(none(), none(), true, FIRST));
        assertEquals(DecayVictim.NOTHING, DecayVictim.pick(none(), none(), false, FIRST));
    }

    @Test
    void niechronioneIdaPierwsze() {
        List<Integer> unprotected = Arrays.asList(3, 4);
        List<Integer> guarded = Arrays.asList(9);

        assertEquals(3, DecayVictim.pick(unprotected, guarded, true, FIRST));
        assertEquals(4, DecayVictim.pick(unprotected, guarded, true, LAST));
    }

    @Test
    void samoChronioneIZwolnienieOznaczaZatrzymanie() {
        assertEquals(DecayVictim.NOTHING,
                     DecayVictim.pick(none(), Arrays.asList(9, 10), true, FIRST));
    }

    @Test
    void samoChronioneIKolejnoscOznaczaZjedzenieChronionego() {
        assertEquals(9, DecayVictim.pick(none(), Arrays.asList(9, 10), false, FIRST));
        assertEquals(10, DecayVictim.pick(none(), Arrays.asList(9, 10), false, LAST));
    }

    @Test
    void losowanieMiesciSieWZakresie() {
        List<Integer> unprotected = Arrays.asList(1, 2, 3);
        for (int i = 0; i < 3; i++) {
            final int fixed = i;
            int picked = DecayVictim.pick(unprotected, none(), true, bound -> fixed);
            assertTrue(unprotected.contains(picked));
        }
    }
}
