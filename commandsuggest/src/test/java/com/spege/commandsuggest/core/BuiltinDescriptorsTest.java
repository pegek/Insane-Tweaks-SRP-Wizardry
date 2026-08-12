package com.spege.commandsuggest.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import org.junit.Test;

/**
 * Sprawdza, ze wbudowane opisy komend wysylane w jarze
 * ({@code assets/commandsuggest/commands/*.json}, wymienione w {@code index.json}) faktycznie
 * parsuja sie przez {@link JsonTreeReader}, zamiast ufac recznemu przepisaniu z planu na dysk.
 * {@code JsonTreeReader} jest surowy (brakujacy {@code command}, wpis w {@code sub} bez
 * {@code lit}, greedy nie na koncu — kazde rzuca), a {@code DescriptorLoader} po prostu loguje
 * WARN i pomija zepsuty plik zamiast zatrzymac start serwera — bez tego testu zla skladnia
 * wbudowanego opisu wsiadlaby do jara i objawila sie dopiero jako brakujaca podpowiedz w grze,
 * bez ani jednej linii bledu w kompilacji. Zamierzony jako stala bariera regresji przy kazdym
 * kolejnym wbudowanym opisie, nie jednorazowy check zadania 17 — stad brak dopisku
 * "tymczasowy" w nazwie.
 *
 * <p>Pliki sa czytane z classpathu testowego (ten sam {@code src/main/resources}), nie
 * przez {@code DescriptorLoader} — {@code core} celowo nie zna zadnej klasy spoza siebie.
 */
public class BuiltinDescriptorsTest {

    private static final String DIR = "/assets/commandsuggest/commands/";

    private static String readResource(String name) throws IOException {
        InputStream in = BuiltinDescriptorsTest.class.getResourceAsStream(DIR + name);
        if (in == null) {
            fail("brak zasobu na classpath: " + DIR + name);
        }
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[4096];
            int n;
            while ((n = in.read(buf)) != -1) {
                out.write(buf, 0, n);
            }
            return new String(out.toByteArray(), StandardCharsets.UTF_8);
        } finally {
            in.close();
        }
    }

    @Test
    public void commandsuggestJsonParsujeSieIMaDwiePodkomendy() throws IOException {
        CommandTree t = JsonTreeReader.read(readResource("commandsuggest.json"));
        assertEquals("commandsuggest", t.getName());
        assertTrue(t.getAliases().isEmpty());

        CmdNode root = t.getRoot();
        assertEquals(2, root.getSub().size());

        CmdNode reload = root.getSub().get(0);
        assertEquals("reload", reload.getLiteral());
        assertTrue(reload.isExecutable());
        assertEquals("/commandsuggest reload - reread command descriptions", reload.getUsage());

        CmdNode refresh = root.getSub().get(1);
        assertEquals("refresh", refresh.getLiteral());
        assertTrue(refresh.isExecutable());
        assertEquals("/commandsuggest refresh - resend your command tree", refresh.getUsage());
    }

    @Test
    public void gameruleJsonParsujeSieIMaDwadziesciaCzteryReguly() throws IOException {
        CommandTree t = JsonTreeReader.read(readResource("gamerule.json"));
        assertEquals("gamerule", t.getName());

        CmdNode root = t.getRoot();
        assertTrue(root.isExecutable());
        assertEquals("/gamerule <rule> [value]", root.getUsage());
        assertEquals(2, root.getArgs().size());

        CmdArg rule = root.getArgs().get(0);
        assertEquals("rule", rule.getName());
        assertSame(ArgType.CHOICE, rule.getType());
        List<String> choices = rule.getChoices();
        assertEquals(24, choices.size());
        assertTrue(choices.contains("announceAdvancements"));
        assertTrue(choices.contains("randomTickSpeed"));
        assertTrue(choices.contains("spectatorsGenerateChunks"));

        CmdArg value = root.getArgs().get(1);
        assertEquals("value", value.getName());
        assertSame(ArgType.WORD, value.getType());
    }

    @Test
    public void indexJsonWymieniaObaPliki() throws IOException {
        String index = readResource("index.json");
        assertTrue(index.contains("commandsuggest.json"));
        assertTrue(index.contains("gamerule.json"));
    }
}
