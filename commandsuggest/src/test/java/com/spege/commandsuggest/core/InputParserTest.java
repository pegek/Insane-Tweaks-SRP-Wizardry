package com.spege.commandsuggest.core;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class InputParserTest {

    @Test
    public void zwyklyTekstToNieKomenda() {
        assertFalse(InputParser.parse("czesc", 5).isCommand());
        assertFalse(InputParser.parse("", 0).isCommand());
    }

    @Test
    public void samUkosnikDajePustyPrefiksNaPozycjiZero() {
        ParsedInput in = InputParser.parse("/", 1);
        assertTrue(in.isCommand());
        assertArrayEquals(new String[] { "" }, in.getTokens());
        assertEquals(0, in.getEditIndex());
        assertEquals("", in.getPrefix());
        assertEquals(1, in.getPrefixStart());
    }

    @Test
    public void nazwaKomendyWTrakciePisania() {
        ParsedInput in = InputParser.parse("/game", 5);
        assertArrayEquals(new String[] { "game" }, in.getTokens());
        assertEquals(0, in.getEditIndex());
        assertEquals("game", in.getPrefix());
        assertEquals(1, in.getPrefixStart());
    }

    @Test
    public void spacjaNaKoncuOtwieraNastepnyArgument() {
        ParsedInput in = InputParser.parse("/gamerule ", 10);
        assertArrayEquals(new String[] { "gamerule", "" }, in.getTokens());
        assertEquals(1, in.getEditIndex());
        assertEquals("", in.getPrefix());
        assertEquals(10, in.getPrefixStart());
    }

    @Test
    public void argumentWTrakciePisania() {
        ParsedInput in = InputParser.parse("/gamerule doD", 13);
        assertArrayEquals(new String[] { "gamerule", "doD" }, in.getTokens());
        assertEquals(1, in.getEditIndex());
        assertEquals("doD", in.getPrefix());
        assertEquals(10, in.getPrefixStart());
    }

    @Test
    public void trzeciArgumentMaPoprawnyOffset() {
        ParsedInput in = InputParser.parse("/gamerule doDaylightCycle tr", 28);
        assertEquals(2, in.getEditIndex());
        assertEquals("tr", in.getPrefix());
        assertEquals(26, in.getPrefixStart());
    }

    @Test
    public void liczySieTylkoTekstPrzedKursorem() {
        // kursor tuz za "doD", reszta linii jest za nim i nie ma prawa wplywac na podpowiedz
        ParsedInput in = InputParser.parse("/gamerule doD aylightCycle true", 13);
        assertArrayEquals(new String[] { "gamerule", "doD" }, in.getTokens());
        assertEquals("doD", in.getPrefix());
    }

    @Test
    public void podwojnaSpacjaDajePustyTokenTakJakWWanilii() {
        // "a  b".split(" ", -1) == ["a", "", "b"] - komenda dostanie dokladnie to samo
        ParsedInput in = InputParser.parse("/cmd  x", 7);
        assertArrayEquals(new String[] { "cmd", "", "x" }, in.getTokens());
        assertEquals(2, in.getEditIndex());
    }

    @Test
    public void kursorNaPoczatkuNieWywalaSie() {
        assertFalse(InputParser.parse("/gamerule", 0).isCommand());
    }

    @Test
    public void prefiksNieKomendyToPustyStringANieWyjatek() {
        // czytane z petli rysujacej, wiec musi byc totalne - patrz javadoc getPrefix
        assertEquals("", InputParser.parse("czesc", 5).getPrefix());
        assertEquals("", ParsedInput.NOT_A_COMMAND.getPrefix());
    }
}
