package com.spege.commandsuggest.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.Arrays;

import org.junit.Test;

public class JsonTreeReaderTest {

    private static final String SRPEVOLUTION =
            "{"
            + "\"command\": \"srpevolution\","
            + "\"aliases\": [\"srpevo\"],"
            + "\"sub\": ["
            + "  {\"lit\": \"set\", \"exec\": true, \"args\": ["
            + "      {\"name\": \"target\", \"type\": \"player\"},"
            + "      {\"name\": \"points\", \"type\": \"int\", \"min\": 0}"
            + "  ]},"
            + "  {\"lit\": \"info\", \"exec\": true}"
            + "]}";

    @Test
    public void czytaPrzykladZeSpecu() {
        CommandTree t = JsonTreeReader.read(SRPEVOLUTION);
        assertEquals("srpevolution", t.getName());
        assertEquals(Arrays.asList("srpevo"), t.getAliases());

        CmdNode root = t.getRoot();
        assertNull(root.getLiteral());
        assertTrue(root.getArgs().isEmpty());
        assertEquals(2, root.getSub().size());

        CmdNode set = root.getSub().get(0);
        assertEquals("set", set.getLiteral());
        assertTrue(set.isExecutable());
        assertEquals(2, set.getArgs().size());
        assertEquals("target", set.getArgs().get(0).getName());
        assertSame(ArgType.PLAYER, set.getArgs().get(0).getType());
        assertSame(ArgType.INT, set.getArgs().get(1).getType());
        assertEquals(Double.valueOf(0.0d), set.getArgs().get(1).getMin());
        assertNull(set.getArgs().get(1).getMax());

        CmdNode info = root.getSub().get(1);
        assertEquals("info", info.getLiteral());
        assertTrue(info.getArgs().isEmpty());
    }

    @Test
    public void brakPolaCommandToCzytelnyBlad() {
        try {
            JsonTreeReader.read("{\"sub\": []}");
            fail("mialo rzucic");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage(), e.getMessage().contains("command"));
        }
    }

    @Test
    public void popsutyJsonToTenSamWyjatek() {
        try {
            JsonTreeReader.read("to nie jest json");
            fail("mialo rzucic");
        } catch (IllegalArgumentException expected) {
            // ok
        }
    }

    @Test
    public void nieznanyTypArgumentuNieWywalaPliku() {
        CommandTree t = JsonTreeReader.read(
                "{\"command\":\"x\",\"args\":[{\"name\":\"a\",\"type\":\"typ-z-przyszlosci\"}]}");
        assertSame(ArgType.UNKNOWN, t.getRoot().getArgs().get(0).getType());
    }

    @Test
    public void choicesIZakresy() {
        CommandTree t = JsonTreeReader.read(
                "{\"command\":\"x\",\"args\":[{\"name\":\"a\",\"type\":\"choice\","
                + "\"choices\":[\"tak\",\"nie\"]},"
                + "{\"name\":\"b\",\"type\":\"float\",\"min\":-1.5,\"max\":2.5}]}");
        assertEquals(Arrays.asList("tak", "nie"), t.getRoot().getArgs().get(0).getChoices());
        assertEquals(Double.valueOf(-1.5d), t.getRoot().getArgs().get(1).getMin());
        assertEquals(Double.valueOf(2.5d), t.getRoot().getArgs().get(1).getMax());
    }

    @Test
    public void subZagniezdzaSieDowolnieGleboko() {
        CommandTree t = JsonTreeReader.read(
                "{\"command\":\"x\",\"sub\":[{\"lit\":\"a\",\"sub\":[{\"lit\":\"b\",\"sub\":["
                + "{\"lit\":\"c\",\"exec\":true,\"usage\":\"/x a b c\"}]}]}]}");
        CmdNode c = t.getRoot().getSub().get(0).getSub().get(0).getSub().get(0);
        assertEquals("c", c.getLiteral());
        assertEquals("/x a b c", c.getUsage());
    }

    @Test
    public void subBezLitJestPomijanyZamiastWymyslacToken() {
        // Wezel bez 'lit' nie ma jak byc trafiony przez gracza - pomijamy caly ten wezel
        // (i jego poddrzewo), zamiast wstawiac zmyslony token "?" albo wywalac caly plik.
        CommandTree t = JsonTreeReader.read(
                "{\"command\":\"x\",\"sub\":["
                + "{\"exec\":true},"
                + "{\"lit\":\"ok\",\"exec\":true}"
                + "]}");
        assertEquals(1, t.getRoot().getSub().size());
        assertEquals("ok", t.getRoot().getSub().get(0).getLiteral());
    }

    @Test
    public void zlyTypElementuAliasesDajeIllegalArgumentException() {
        // Gson na obiekcie zamiast stringa rzuca UnsupportedOperationException - kontrakt tej
        // metody to IllegalArgumentException, wiec musi byc przepakowany.
        try {
            JsonTreeReader.read("{\"command\":\"x\",\"aliases\":[{}]}");
            fail("mialo rzucic");
        } catch (IllegalArgumentException expected) {
            // ok
        }
    }

    @Test
    public void elementArgsKtoryNieJestObiektemDajeIllegalArgumentException() {
        // Gson na elemencie-nie-obiekcie rzuca IllegalStateException - przepakowane na kontrakt.
        try {
            JsonTreeReader.read("{\"command\":\"x\",\"args\":[\"nie-obiekt\"]}");
            fail("mialo rzucic");
        } catch (IllegalArgumentException expected) {
            // ok
        }
    }

    @Test
    public void elementSubKtoryNieJestObiektemDajeIllegalArgumentException() {
        try {
            JsonTreeReader.read("{\"command\":\"x\",\"sub\":[123]}");
            fail("mialo rzucic");
        } catch (IllegalArgumentException expected) {
            // ok
        }
    }

    @Test
    public void nieliczbowyMinDalejDajeIllegalArgumentException() {
        // NumberFormatException dziedziczy po IllegalArgumentException, wiec ten przypadek
        // spelnial kontrakt juz przed poprawka B/C - pinujemy to, zeby nie zniknelo przy
        // nastepnej zmianie w readArg.
        try {
            JsonTreeReader.read("{\"command\":\"x\",\"args\":[{\"min\":\"abc\"}]}");
            fail("mialo rzucic");
        } catch (IllegalArgumentException expected) {
            // ok
        }
    }

    @Test
    public void nielogicznyExecNieCrashujeINieStajeSieWykonywalny() {
        // "yes" nie jest JSON-owym booleanem - Boolean.parseBoolean cicho dawalby false, ale
        // przez String.valueOf. Sprawdzamy, ze nie ma wyjatku i ze wezel dostaje domyslne false,
        // a nie zaskakujaca koercja stringa.
        CommandTree t = JsonTreeReader.read("{\"command\":\"x\",\"exec\":\"yes\"}");
        assertFalse(t.getRoot().isExecutable());
    }

    @Test
    public void brakiSaDomyslne() {
        CommandTree t = JsonTreeReader.read("{\"command\":\"x\"}");
        assertTrue(t.getAliases().isEmpty());
        assertTrue(t.getRoot().getArgs().isEmpty());
        assertTrue(t.getRoot().getSub().isEmpty());
        assertFalse(t.getRoot().isExecutable());
        assertNull(t.getRoot().getUsage());
    }
}
