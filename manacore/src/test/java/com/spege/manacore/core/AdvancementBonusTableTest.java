package com.spege.manacore.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.Test;

import com.spege.manacore.core.AdvancementBonusTable.Table;

public class AdvancementBonusTableTest {

    private static final double EPS = 1.0e-9D;

    private static Table parse(String... entries) {
        return AdvancementBonusTable.parse(entries);
    }

    @Test
    public void parsujePoprawnyWpis() {
        Table t = parse("ebwizardry:crystal=5");
        assertEquals(1, t.bonuses().size());
        assertEquals(5.0D, t.bonuses().get("ebwizardry:crystal").doubleValue(), EPS);
        assertTrue(t.rejected().isEmpty());
    }

    @Test
    public void tnieBialeZnakiWokolIdIWartosci() {
        Table t = parse("   ebwizardry:crystal  =  5.5   ");
        assertEquals(5.5D, t.bonuses().get("ebwizardry:crystal").doubleValue(), EPS);
        assertTrue(t.rejected().isEmpty());
    }

    @Test
    public void przyjmujeIdZeSciezkaWieloczlonowa() {
        // The handbook advancements are nested, so ids really do contain slashes.
        Table t = parse("ebwizardry:handbook/spells=3");
        assertEquals(3.0D, t.bonuses().get("ebwizardry:handbook/spells").doubleValue(), EPS);
    }

    @Test
    public void odrzucaWpisBezZnakuRownosci() {
        Table t = parse("ebwizardry:crystal 5");
        assertTrue(t.isEmpty());
        assertEquals(1, t.rejected().size());
    }

    @Test
    public void odrzucaIdBezPrzestrzeniNazw() {
        // Deliberately NOT defaulted to "minecraft:" - see AdvancementBonusTable.parseOne.
        Table t = parse("crystal=5");
        assertTrue(t.isEmpty());
        assertEquals(1, t.rejected().size());
    }

    @Test
    public void odrzucaPusteIdIPustaWartosc() {
        Table t = parse("=5", "ebwizardry:crystal=");
        assertTrue(t.isEmpty());
        assertEquals(2, t.rejected().size());
    }

    @Test
    public void odrzucaWartoscNieliczbowa() {
        Table t = parse("ebwizardry:crystal=duzo");
        assertTrue(t.isEmpty());
        assertEquals(1, t.rejected().size());
    }

    @Test
    public void odrzucaNanINieskonczonosc() {
        // Double.parseDouble accepts both of these literals, so this is a real input path.
        Table t = parse("ebwizardry:a=NaN", "ebwizardry:b=Infinity", "ebwizardry:c=-Infinity");
        assertTrue(t.isEmpty());
        assertEquals(3, t.rejected().size());
    }

    @Test
    public void przyjmujeWartoscUjemna() {
        // An advancement that COSTS the player maximum mana is allowed by design; the sum is
        // floored at zero later, in clampTotal.
        Table t = parse("ebwizardry:cursed=-10");
        assertEquals(-10.0D, t.bonuses().get("ebwizardry:cursed").doubleValue(), EPS);
        assertTrue(t.rejected().isEmpty());
    }

    @Test
    public void dzieliNaOSTATNIMZnakuRownosciNieNaPierwszym() {
        // A split on the FIRST '=' would leave "5=7" as the value and reject the entry.
        Table t = parse("ebwizardry:odd=name=7");
        assertEquals(7.0D, t.bonuses().get("ebwizardry:odd=name").doubleValue(), EPS);
    }

    @Test
    public void przyDuplikacieWygrywaOstatniWpis() {
        Table t = parse("ebwizardry:crystal=5", "ebwizardry:crystal=25");
        assertEquals(1, t.bonuses().size());
        assertEquals(25.0D, t.bonuses().get("ebwizardry:crystal").doubleValue(), EPS);
    }

    @Test
    public void jedenZlyWpisNiePsujePozostalych() {
        Table t = parse("ebwizardry:crystal=5", "smieci", "ebwizardry:master=20");
        assertEquals(2, t.bonuses().size());
        assertEquals(1, t.rejected().size());
    }

    @Test
    public void pomijaPusteINuloweLinieBezOstrzezenia() {
        // A blank line in a config file is not a mistake worth warning about.
        Table t = AdvancementBonusTable.parse(new String[] {"", "   ", null, "ebwizardry:crystal=5"});
        assertEquals(1, t.bonuses().size());
        assertTrue(t.rejected().isEmpty());
    }

    @Test
    public void znosiPustaINulowaTablice() {
        assertTrue(AdvancementBonusTable.parse(new String[0]).isEmpty());
        assertTrue(AdvancementBonusTable.parse(null).isEmpty());
    }

    @Test
    public void zachowujeKolejnoscZConfigu() {
        Table t = parse("ebwizardry:a=1", "ebwizardry:b=2", "ebwizardry:c=3");
        List<String> keys = new ArrayList<String>(t.bonuses().keySet());
        assertEquals("ebwizardry:a", keys.get(0));
        assertEquals("ebwizardry:b", keys.get(1));
        assertEquals("ebwizardry:c", keys.get(2));
    }

    @Test
    public void clampTotalPrzepuszczaWartoscPonizejSufitu() {
        assertEquals(160.0D, AdvancementBonusTable.clampTotal(160.0D, 200.0D), EPS);
    }

    @Test
    public void clampTotalScinaDoSufitu() {
        assertEquals(200.0D, AdvancementBonusTable.clampTotal(260.0D, 200.0D), EPS);
    }

    @Test
    public void clampTotalPodlogujeUjemnaSumeDoZera() {
        assertEquals(0.0D, AdvancementBonusTable.clampTotal(-40.0D, 200.0D), EPS);
    }

    @Test
    public void clampTotalTraktujeUjemnySufitJakZero() {
        assertEquals(0.0D, AdvancementBonusTable.clampTotal(50.0D, -5.0D), EPS);
        assertEquals(0.0D, AdvancementBonusTable.clampTotal(50.0D, 0.0D), EPS);
    }

    @Test
    public void clampTotalOdrzucaNan() {
        assertEquals(0.0D, AdvancementBonusTable.clampTotal(Double.NaN, 200.0D), EPS);
    }
}
