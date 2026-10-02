package com.spege.tombtweaks.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;

class DecayScheduleTest {

    private static final long START = 6_000L;
    private static final long INTERVAL = 1_200L;

    @Test
    void nicPrzedProgiem() {
        assertFalse(DecaySchedule.shouldDecay(0L, START, INTERVAL));
        assertFalse(DecaySchedule.shouldDecay(START - 1L, START, INTERVAL));
    }

    @Test
    void pierwszeOdpalenieDokladnieNaProgu() {
        assertTrue(DecaySchedule.shouldDecay(START, START, INTERVAL));
    }

    @Test
    void dokladnieJednoOdpalenieNaInterwal() {
        int fired = 0;
        for (long tick = START; tick < START + 10L * INTERVAL; tick++) {
            if (DecaySchedule.shouldDecay(tick, START, INTERVAL)) {
                fired++;
            }
        }
        assertEquals(10, fired);
    }

    @Test
    void zerowyInterwalNieDzieliPrzezZero() {
        assertFalse(DecaySchedule.shouldDecay(999_999L, START, 0L));
        assertFalse(DecaySchedule.shouldDecay(999_999L, START, -5L));
    }

    @Test
    void cofnietyLicznikMilczy() {
        // countTicks moze wrocic do zera, gdy grob zostanie postawiony na nowo.
        assertFalse(DecaySchedule.shouldDecay(10L, START, INTERVAL));
    }
}
