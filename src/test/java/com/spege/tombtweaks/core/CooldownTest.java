package com.spege.tombtweaks.core;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CooldownTest {

    private static final long MINUTE = 60_000L;

    @Test
    void parserCzytaParyNazwaMinuty() {
        CooldownRules rules = CooldownRules.parse(Arrays.asList(
                "tombstone:book_of_disenchantment;6",
                "tombstone:book_of_magic_impregnation;12"));

        assertEquals(6L * MINUTE, rules.millisFor("tombstone:book_of_disenchantment"));
        assertEquals(12L * MINUTE, rules.millisFor("tombstone:book_of_magic_impregnation"));
    }

    @Test
    void nieznanaKsiegaNieMaCooldownu() {
        CooldownRules rules = CooldownRules.parse(Arrays.asList("tombstone:book_of_scribe;5"));
        assertEquals(0L, rules.millisFor("tombstone:book_of_oblivion"));
    }

    @Test
    void zeroMinutOznaczaWylaczony() {
        CooldownRules rules = CooldownRules.parse(Arrays.asList("tombstone:book_of_scribe;0"));
        assertEquals(0L, rules.millisFor("tombstone:book_of_scribe"));
    }

    @Test
    void popsuteWpisySaPomijaneANieFatalne() {
        CooldownRules rules = CooldownRules.parse(Arrays.asList(
                "bez_srednika",
                "tombstone:book_of_scribe;nie_liczba",
                ";7",
                "tombstone:book_of_scribe;5"));

        assertEquals(5L * MINUTE, rules.millisFor("tombstone:book_of_scribe"));
    }

    @Test
    void minutySaOgraniczoneDoDwunastuGodzin() {
        CooldownRules rules = CooldownRules.parse(Arrays.asList("tombstone:book_of_scribe;9000"));
        assertEquals(720L * MINUTE, rules.millisFor("tombstone:book_of_scribe"));
    }

    @Test
    void ujemneMinutyToBrakCooldownu() {
        CooldownRules rules = CooldownRules.parse(Arrays.asList("tombstone:book_of_scribe;-3"));
        assertEquals(0L, rules.millisFor("tombstone:book_of_scribe"));
    }

    @Test
    void pustaKonfiguracjaJestPusta() {
        assertTrue(CooldownRules.parse(new ArrayList<String>()).isEmpty());
    }

    @Test
    void nullINieStringiWKonfiguracjiSaPomijane() {
        CooldownRules rules = CooldownRules.parse(Arrays.asList(null, 7, "tombstone:book_of_scribe;5"));
        assertEquals(5L * MINUTE, rules.millisFor("tombstone:book_of_scribe"));
    }

    @Test
    void nullowaListaToPusteReguly() {
        assertTrue(CooldownRules.parse(null).isEmpty());
    }

    @Test
    void pozostalyCzasMalejeIDochodziDoZera() {
        long lastUse = 1_000_000L;
        long cooldown = 10L * MINUTE;

        assertEquals(cooldown, Cooldown.remaining(lastUse, cooldown, lastUse));
        assertEquals(cooldown - MINUTE, Cooldown.remaining(lastUse, cooldown, lastUse + MINUTE));
        assertEquals(0L, Cooldown.remaining(lastUse, cooldown, lastUse + cooldown));
        assertEquals(0L, Cooldown.remaining(lastUse, cooldown, lastUse + cooldown + 1L));
    }

    @Test
    void brakUzyciaToBrakCooldownu() {
        assertEquals(0L, Cooldown.remaining(0L, 10L * MINUTE, 5_000_000L));
        assertFalse(Cooldown.isActive(0L, 10L * MINUTE, 5_000_000L));
    }

    @Test
    void cofnietyZegarNieBlokujeGracza() {
        // Zegar systemowy moze sie cofnac (zmiana czasu, synchronizacja NTP).
        // Nie wolno przez to zamknac graczowi ksiegi na wieki.
        assertEquals(0L, Cooldown.remaining(5_000_000L, 10L * MINUTE, 1_000L));
        assertFalse(Cooldown.isActive(5_000_000L, 10L * MINUTE, 1_000L));
    }

    @Test
    void aktywnyDopokiCosZostalo() {
        long lastUse = 1_000_000L;
        assertTrue(Cooldown.isActive(lastUse, 10L * MINUTE, lastUse + MINUTE));
        assertFalse(Cooldown.isActive(lastUse, 10L * MINUTE, lastUse + 10L * MINUTE));
    }
}
