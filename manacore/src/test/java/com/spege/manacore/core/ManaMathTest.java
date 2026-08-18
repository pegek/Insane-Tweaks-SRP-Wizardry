package com.spege.manacore.core;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class ManaMathTest {

    private static final double EPS = 1.0e-9D;

    @Test
    public void clampTrzymaWartoscWZakresie() {
        assertEquals(5.0D, ManaMath.clamp(5.0D, 0.0D, 10.0D), EPS);
        assertEquals(0.0D, ManaMath.clamp(-3.0D, 0.0D, 10.0D), EPS);
        assertEquals(10.0D, ManaMath.clamp(42.0D, 0.0D, 10.0D), EPS);
    }

    @Test
    public void clampZwracaMinGdyZakresJestOdwrocony() {
        assertEquals(10.0D, ManaMath.clamp(5.0D, 10.0D, 0.0D), EPS);
    }

    @Test
    public void wydatekNieSchodziPonizejZera() {
        assertEquals(0.0D, ManaMath.afterSpend(3.0D, 10.0D), EPS);
        assertEquals(7.0D, ManaMath.afterSpend(10.0D, 3.0D), EPS);
    }

    @Test
    public void regenNiePrzekraczaMaksimum() {
        assertEquals(10.0D, ManaMath.afterRegen(9.5D, 10.0D, 2.0D), EPS);
        assertEquals(6.0D, ManaMath.afterRegen(5.0D, 10.0D, 1.0D), EPS);
    }

    @Test
    public void regenNieObnizaPuliPowyzejMaksimum() {
        // Po zdjeciu bauble'a `current` moze przekraczac nowe `max`. Regen nie ma tego obcinac.
        assertEquals(15.0D, ManaMath.afterRegen(15.0D, 10.0D, 2.0D), EPS);
    }

    @Test
    public void progresjaZatrzymujeSieNaSuficie() {
        assertEquals(4.0D, ManaMath.afterProgressionGain(3.5D, 1.0D, 4.0D), EPS);
        assertEquals(4.0D, ManaMath.afterProgressionGain(4.0D, 1.0D, 4.0D), EPS);
        assertEquals(1.5D, ManaMath.afterProgressionGain(1.0D, 0.5D, 4.0D), EPS);
    }

    @Test
    public void regenNaTickPrzeliczaSekundyNaTicki() {
        // 5 many co 2 sekundy = 5 / 40 ticka
        assertEquals(0.125D, ManaMath.regenPerTick(5.0D, 2.0D), EPS);
    }

    @Test
    public void regenNaTickZwracaZeroDlaNiepoprawnejCzestotliwosci() {
        assertEquals(0.0D, ManaMath.regenPerTick(5.0D, 0.0D), EPS);
        assertEquals(0.0D, ManaMath.regenPerTick(5.0D, -1.0D), EPS);
    }
}
