package com.spege.commandsuggest.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.junit.Test;

public class SuggestionEngineTest {

    private static CommandIndex indeks() {
        // /gamerule <choice rule> <bool value>
        CmdArg rule = new CmdArg("rule", ArgType.CHOICE,
                Arrays.asList("doFireTick", "doDaylightCycle", "keepInventory"), null, null);
        CmdArg value = new CmdArg("value", ArgType.BOOL, null, null, null);
        CommandTree gamerule = new CommandTree("gamerule", null,
                new CmdNode(null, Arrays.asList(rule, value), null, true, null));

        // /cs reload | /cs give <player> <item>
        CmdNode reload = new CmdNode("reload", null, null, true, null);
        CmdNode give = new CmdNode("give",
                Arrays.asList(new CmdArg("who", ArgType.PLAYER, null, null, null),
                        new CmdArg("what", ArgType.ITEM, null, null, null)),
                null, true, null);
        CommandTree cs = new CommandTree("commandsuggest", Arrays.asList("cs"),
                new CmdNode(null, null, Arrays.asList(reload, give), false, null));

        // nieopisana komenda z generycznym ogonem
        CommandTree obca = new CommandTree("obcamoda", null,
                new CmdNode(null,
                        Collections.singletonList(new CmdArg("args", ArgType.GREEDY, null, null, null)),
                        null, true, "/obcamoda <args>"));

        return new CommandIndex(Arrays.asList(gamerule, cs, obca));
    }

    private static Suggestions dla(String linia) {
        return SuggestionEngine.suggest(indeks(), InputParser.parse(linia, linia.length()));
    }

    private static List<String> teksty(Suggestions s) {
        List<String> out = new ArrayList<String>();
        for (Suggestion x : s.getItems()) {
            out.add(x.getText());
        }
        return out;
    }

    @Test
    public void zwyklyTekstNiczegoNiePodpowiada() {
        Suggestions s = SuggestionEngine.suggest(indeks(), InputParser.parse("czesc", 5));
        assertTrue(s.getItems().isEmpty());
        assertFalse(s.needsServerQuery());
    }

    @Test
    public void samUkosnikDajeWszystkieNazwyIAliasy() {
        assertEquals(Arrays.asList("commandsuggest", "cs", "gamerule", "obcamoda"), teksty(dla("/")));
    }

    @Test
    public void prefiksFiltrujeNazwyBezWzgleduNaWielkoscLiter() {
        assertEquals(Arrays.asList("gamerule"), teksty(dla("/GaMe")));
    }

    @Test
    public void nieznanaKomendaOddajeSprawaSerwerowi() {
        Suggestions s = dla("/cosnieznanego cos");
        assertTrue(s.getItems().isEmpty());
        assertTrue(s.needsServerQuery());
    }

    @Test
    public void choiceRozwiazujeSieNaMiejscu() {
        Suggestions s = dla("/gamerule do");
        assertEquals(Arrays.asList("doFireTick", "doDaylightCycle"), teksty(s));
        assertSame(ArgType.CHOICE, s.getArgType());
        assertFalse(s.needsServerQuery());
        assertEquals(10, s.getReplaceStart());
    }

    @Test
    public void boolTezRozwiazujeSieNaMiejscu() {
        Suggestions s = dla("/gamerule keepInventory ");
        assertEquals(Arrays.asList("true", "false"), teksty(s));
        assertSame(ArgType.BOOL, s.getArgType());
    }

    @Test
    public void literalyZSubSaPodpowiadane() {
        Suggestions s = dla("/cs ");
        assertEquals(Arrays.asList("reload", "give"), teksty(s));
        assertNull("literal nie ma typu argumentu", s.getArgType());
    }

    @Test
    public void poWejsciuWLiteralIdziemyWJegoArgumenty() {
        Suggestions s = dla("/cs give ");
        assertTrue("player rozwiazuje klient, nie core", s.getItems().isEmpty());
        assertSame(ArgType.PLAYER, s.getArgType());
        assertFalse("player jest lokalny, zadnego pakietu", s.needsServerQuery());
    }

    @Test
    public void drugiArgumentPoLiterale() {
        Suggestions s = dla("/cs give Steve ");
        assertSame(ArgType.ITEM, s.getArgType());
    }

    @Test
    public void greedyZjadaWszystkoIPytaSerwer() {
        Suggestions s = dla("/obcamoda cokolwiek dalej ");
        assertSame(ArgType.GREEDY, s.getArgType());
        assertTrue(s.needsServerQuery());
        assertEquals("/obcamoda <args>", s.getUsage());
    }

    @Test
    public void nieznanyLiteralNieStrzelaDoSerwera() {
        Suggestions s = dla("/cs nieistniejacy ");
        assertTrue(s.getItems().isEmpty());
        assertFalse("drzewo znamy, wiec wiemy ze nie ma czego podpowiadac", s.needsServerQuery());
    }

    @Test
    public void wyczerpanaSciezkaNiczegoNieProponuje() {
        Suggestions s = dla("/cs reload ");
        assertTrue(s.getItems().isEmpty());
        assertFalse(s.needsServerQuery());
    }

    @Test
    public void usageJestSyntezowaneGdyOpisGoNieMa() {
        assertEquals("/gamerule <rule> <value>", dla("/gamerule do").getUsage());
        assertEquals("/commandsuggest give <who> <what>", dla("/cs give ").getUsage());
    }

    /**
     * Pkt C z recenzji: {@code usageOf} zawsze bierze PELNA liste argumentow wezla, nie tylko
     * te jeszcze nie wpisane. Kiedy edytujemy drugi argument, usage i tak pokazuje oba —
     * "who" tez, chociaz gracz juz go wpisal jako "Steve". To zamierzone: linia usage ma
     * pokazac cala skladnie komendy (tak jak waniliowe "Usage: ..."), a nie tylko ogon —
     * pozycja kursora widac juz w samym popupie z podpowiedziami.
     */
    @Test
    public void usagePozostajePelneGdyDrugiArgumentJestJuzEdytowany() {
        Suggestions s = dla("/cs give Steve ");
        assertEquals("/commandsuggest give <who> <what>", s.getUsage());
    }

    /**
     * Pkt B z recenzji: {@code GREEDY} jest sprawdzane PRZED {@code i == editIndex}, wiec zwraca
     * sie na pierwszy kontakt z argumentem typu greedy — bez wzgledu na to, ktory token linii
     * faktycznie edytujemy. Zalozenie jest takie samo jak w prawdziwej gramatyce komend: greedy
     * jest ostatnim argumentem wezla. Jesli opis zlamie to zalozenie i wstawi argument PO greedy
     * (tu: "tail" typu WORD po "front" typu GREEDY), ten drugi argument jest martwy —
     * SuggestionEngine nigdy go nie zwroci, niezaleznie od tego, ktory token edytujemy.
     * Test przypina to zachowanie, nie ocenia go — zmiana wymaga osobnej decyzji.
     */
    @Test
    public void greedyNieNaKoncuPolykaKazdyKolejnyArgumentWezla() {
        CmdArg front = new CmdArg("front", ArgType.GREEDY, null, null, null);
        CmdArg tail = new CmdArg("tail", ArgType.WORD, null, null, null);
        CommandTree zle = new CommandTree("zle", null,
                new CmdNode(null, Arrays.asList(front, tail), null, true, null));
        CommandIndex idx = new CommandIndex(Collections.singletonList(zle));

        Suggestions s = SuggestionEngine.suggest(idx, InputParser.parse("/zle a b ", 9));

        assertSame("front zjada linie, tail jest nieosiagalny", ArgType.GREEDY, s.getArgType());
        assertEquals("/zle <front> <tail>", s.getUsage());
    }
}
