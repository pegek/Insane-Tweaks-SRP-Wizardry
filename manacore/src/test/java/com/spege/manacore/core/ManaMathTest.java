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
    public void clampNaGranicachZakresu() {
        assertEquals(0.0D, ManaMath.clamp(0.0D, 0.0D, 10.0D), EPS);
        assertEquals(10.0D, ManaMath.clamp(10.0D, 0.0D, 10.0D), EPS);
    }

    @Test
    public void wydatekNieSchodziPonizejZera() {
        assertEquals(0.0D, ManaMath.afterSpend(3.0D, 10.0D), EPS);
        assertEquals(7.0D, ManaMath.afterSpend(10.0D, 3.0D), EPS);
        assertEquals(0.0D, ManaMath.afterSpend(5.0D, 5.0D), EPS);
    }

    @Test
    public void regenNiePrzekraczaMaksimum() {
        assertEquals(10.0D, ManaMath.afterRegen(9.5D, 10.0D, 2.0D), EPS);
        assertEquals(6.0D, ManaMath.afterRegen(5.0D, 10.0D, 1.0D), EPS);
    }

    @Test
    public void regenObcinaNadmiarPonadMaksimum() {
        // After unequipping a bauble that granted bonus maximum, `current` can exceed the new
        // `max`. The surplus is confiscated rather than kept - bonus maximum raises the ceiling,
        // it never hands out the mana itself.
        assertEquals(10.0D, ManaMath.afterRegen(15.0D, 10.0D, 2.0D), EPS);
        assertEquals(10.0D, ManaMath.afterRegen(15.0D, 10.0D, 0.0D), EPS);
    }

    @Test
    public void regenNieSchodziPonizejZeraPrzyUjemnymMaksimum() {
        // Unreachable through ManaAttributes.getMaxMana, which clamps the sum of both attributes
        // at zero, but a caller passing a negative max must not be handed a negative pool.
        assertEquals(0.0D, ManaMath.afterRegen(0.0D, -5.0D, 1.0D), EPS);
    }

    @Test
    public void progresjaZatrzymujeSieNaSuficie() {
        assertEquals(4.0D, ManaMath.afterProgressionGain(3.5D, 4.0D, 1.0D), EPS);
        assertEquals(4.0D, ManaMath.afterProgressionGain(4.0D, 4.0D, 1.0D), EPS);
        assertEquals(1.5D, ManaMath.afterProgressionGain(1.0D, 4.0D, 0.5D), EPS);
    }

    @Test
    public void progresjaNieObnizaJuzZbankowanejWartosci() {
        // The admin lowered the cap in the config after the player had already banked progression.
        // The banked amount must stay untouched, not get clamped down.
        assertEquals(60.0D, ManaMath.afterProgressionGain(60.0D, 50.0D, 5.0D), EPS);
        assertEquals(50.0D, ManaMath.afterProgressionGain(50.0D, 50.0D, 5.0D), EPS);
    }

    @Test
    public void regenNaTickPrzeliczaSekundyNaTicki() {
        // 5 mana every 2 seconds = 5 / 40 ticks
        assertEquals(0.125D, ManaMath.regenPerTick(5.0D, 2.0D), EPS);
    }

    @Test
    public void regenNaTickZwracaZeroDlaNiepoprawnejCzestotliwosci() {
        assertEquals(0.0D, ManaMath.regenPerTick(5.0D, 0.0D), EPS);
        assertEquals(0.0D, ManaMath.regenPerTick(5.0D, -1.0D), EPS);
    }
}
