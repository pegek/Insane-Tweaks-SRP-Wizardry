package com.spege.manacore.core;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class CostMathTest {

    private static final double EPS = 1.0e-9D;

    @Test
    public void kosztRozlozonyPlaciPolowkiCoPolSekundy() {
        assertEquals(3, CostMath.distributedCost(5, 20));
        assertEquals(2, CostMath.distributedCost(5, 10));
        assertEquals(0, CostMath.distributedCost(5, 7));
    }

    @Test
    public void kosztRozlozonyDlaTikuZeroLiczyPelnaRate() {
        assertEquals(3, CostMath.distributedCost(5, 0));
    }

    @Test
    public void kosztRozlozonySumujeSieDoPelnegoKosztuNaSekunde() {
        int total = 0;
        for (int tick = 20; tick < 40; tick++) {
            total += CostMath.distributedCost(7, tick);
        }
        assertEquals(7, total);
    }

    @Test
    public void kosztRozlozonyDlaZerowegoKosztuJestZerowy() {
        assertEquals(0, CostMath.distributedCost(0, 20));
    }

    @Test
    public void kosztMnozySiePrzezWszystkieWspolczynniki() {
        assertEquals(15.0D, CostMath.resolveCost(10, 1.5D, 2.0D, 0.5D), EPS);
    }

    @Test
    public void kosztNigdyNieSchodziPonizejZera() {
        assertEquals(0.0D, CostMath.resolveCost(10, -1.0D, 1.0D, 1.0D), EPS);
    }

    @Test
    public void zerowyWspolczynnikDajeCzarZaDarmo() {
        assertEquals(0.0D, CostMath.resolveCost(10, 1.0D, 0.0D, 1.0D), EPS);
    }

    @Test
    public void refundRosnieZPojemnosciaRozdzki() {
        assertEquals(0.30D, CostMath.refundFraction(700, 100, 100, 0.05D), EPS);
    }

    @Test
    public void refundJestZerowyGdyRozdzkaNieMaNadwyzki() {
        assertEquals(0.0D, CostMath.refundFraction(100, 100, 100, 0.05D), EPS);
        assertEquals(0.0D, CostMath.refundFraction(50, 100, 100, 0.05D), EPS);
    }

    @Test
    public void refundJestObcinanyDoStuProcent() {
        assertEquals(1.0D, CostMath.refundFraction(100000, 100, 100, 0.05D), EPS);
    }

    @Test
    public void refundJestZerowyDlaNiepoprawnegoKroku() {
        assertEquals(0.0D, CostMath.refundFraction(700, 100, 0, 0.05D), EPS);
    }

    @Test
    public void przelicznikDzialaWObieStrony() {
        assertEquals(50.0F, CostMath.toForeignUnits(100.0D, 0.5D), 1.0e-6F);
        assertEquals(100.0D, CostMath.fromForeignUnits(50.0F, 0.5D), EPS);
    }

    @Test
    public void przelicznikZeroTraktujemyJakJedenDoJednego() {
        assertEquals(100.0F, CostMath.toForeignUnits(100.0D, 0.0D), 1.0e-6F);
        assertEquals(100.0D, CostMath.fromForeignUnits(100.0F, 0.0D), EPS);
    }

    @Test
    public void kosztNieskonczonyJestOdrzucany() {
        assertEquals(0.0D, CostMath.resolveCost(10, 1.0D, 1.0D, Double.POSITIVE_INFINITY), EPS);
        assertEquals(0.0D, CostMath.resolveCost(10, Double.NEGATIVE_INFINITY, 1.0D, 1.0D), EPS);
    }

    @Test
    public void kosztNieBedacyLiczbaJestOdrzucany() {
        assertEquals(0.0D, CostMath.resolveCost(10, Double.NaN, 1.0D, 1.0D), EPS);
        assertEquals(0.0D, CostMath.resolveCost(10, 1.0D, Double.NaN, 1.0D), EPS);
        assertEquals(0.0D, CostMath.resolveCost(10, 1.0D, 1.0D, Double.NaN), EPS);
    }

    @Test
    public void refundJestZerowyGdyUlamekNieJestLiczba() {
        assertEquals(0.0D, CostMath.refundFraction(700, 100, 100, Double.NaN), EPS);
    }

    @Test
    public void kosztRozlozonyOdrzucaUjemnyKoszt() {
        assertEquals(0, CostMath.distributedCost(-5, 20));
        assertEquals(0, CostMath.distributedCost(-1, 20));
    }

    @Test
    public void kosztRozlozonySymetriaDlaUjemnychTickow() {
        assertEquals(3, CostMath.distributedCost(5, -20));
        assertEquals(0, CostMath.distributedCost(5, -7));
    }

    @Test
    public void kosztSekundowyZaokraglaWGoreZebyTaniCzarNieBylDarmowy() {
        assertEquals(1, CostMath.continuousSecondCost(0.4D));
        assertEquals(1, CostMath.continuousSecondCost(1.0D));
        assertEquals(2, CostMath.continuousSecondCost(1.2D));
    }

    @Test
    public void kosztSekundowyOdrzucaWartosciNiepoprawne() {
        assertEquals(0, CostMath.continuousSecondCost(0.0D));
        assertEquals(0, CostMath.continuousSecondCost(-3.0D));
        assertEquals(0, CostMath.continuousSecondCost(Double.NaN));
        assertEquals(0, CostMath.continuousSecondCost(Double.POSITIVE_INFINITY));
    }
}
