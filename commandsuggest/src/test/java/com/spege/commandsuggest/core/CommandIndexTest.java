package com.spege.commandsuggest.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.junit.Test;

public class CommandIndexTest {

    private static CommandTree drzewo(String nazwa, String... aliasy) {
        CmdNode root = new CmdNode(null, Collections.<CmdArg>emptyList(),
                Collections.<CmdNode>emptyList(), true, null);
        return new CommandTree(nazwa, Arrays.asList(aliasy), root);
    }

    /**
     * {@code list.add(null)} kompiluje sie na {@code List<?>} (null pasuje do kazdego przechwycenia
     * wildcarda), a niemodyfikowalny wrapper rzuca zanim w ogole zajrzy do argumentu — wiec to
     * dziala dla wszystkich czterech typow elementow bez rzutowan i bez ostrzezen kompilatora.
     */
    private static void assertUnmodifiable(List<?> list) {
        try {
            list.add(null);
            fail("lista ma byc niemodyfikowalna");
        } catch (UnsupportedOperationException expected) {
            // ok
        }
    }

    @Test
    public void byNameZnajdujePoNazwieIPoAliasie() {
        CommandIndex idx = new CommandIndex(Arrays.asList(drzewo("gamerule"), drzewo("teleport", "tp")));
        assertEquals("gamerule", idx.byName("gamerule").getName());
        assertEquals("teleport", idx.byName("tp").getName());
        assertNull(idx.byName("nie-ma-takiej"));
    }

    @Test
    public void allNamesZawieraAliasyIJestPosortowane() {
        CommandIndex idx = new CommandIndex(Arrays.asList(drzewo("teleport", "tp"), drzewo("gamerule")));
        assertEquals(Arrays.asList("gamerule", "teleport", "tp"), idx.allNames());
    }

    @Test
    public void pruneUsuwaCaleKomendyPoNazwieKanonicznej() {
        CommandIndex idx = new CommandIndex(Arrays.asList(drzewo("op"), drzewo("teleport", "tp")));
        CommandIndex przyciety = idx.prune(name -> !"op".equals(name));
        assertNull(przyciety.byName("op"));
        assertEquals("teleport", przyciety.byName("tp").getName());
        assertEquals(1, przyciety.getCommands().size());
    }

    @Test
    public void pruneNieRuszaOryginalu() {
        CommandIndex idx = new CommandIndex(Collections.singletonList(drzewo("op")));
        idx.prune(name -> false);
        assertEquals(1, idx.getCommands().size());
    }

    @Test
    public void pruneZAkceptujWszystkoPredykatemNieUsuwaNic() {
        CommandIndex idx = new CommandIndex(Arrays.asList(drzewo("op"), drzewo("teleport", "tp")));
        CommandIndex wynik = idx.prune(name -> true);
        assertEquals(2, wynik.getCommands().size());
    }

    @Test
    public void pruneNaPustymIndeksieDajePustyIndeks() {
        CommandIndex wynik = CommandIndex.EMPTY.prune(name -> true);
        assertTrue(wynik.getCommands().isEmpty());
    }

    @Test
    public void emptyJestPusty() {
        assertTrue(CommandIndex.EMPTY.getCommands().isEmpty());
        assertTrue(CommandIndex.EMPTY.allNames().isEmpty());
        assertNull(CommandIndex.EMPTY.byName("cokolwiek"));
    }

    @Test
    public void nazwaKanonicznaWygrywaZCudzymAliasem() {
        // "tp" jest nazwa kanoniczna komendy A i jednoczesnie aliasem komendy B.
        // Dwa pelne przebiegi w konstruktorze gwarantuja, ze A wygrywa NIEZALEZNIE od kolejnosci
        // na liscie - dlatego sprawdzamy obie kolejnosci.
        CommandTree a = drzewo("tp");
        CommandTree b = drzewo("teleport", "tp");
        assertEquals("tp", new CommandIndex(Arrays.asList(a, b)).byName("tp").getName());
        assertEquals("tp", new CommandIndex(Arrays.asList(b, a)).byName("tp").getName());
    }

    @Test
    public void przyKolizjiAliasowWygrywaPierwszyNaLiscie() {
        // Dwa aliasy tej samej nazwy nie maja rozstrzygniecia "z natury" - utrwalamy to, co robi
        // kod, zeby zmiana byla swiadoma: kto pierwszy na liscie, ten bierze alias.
        CommandTree a = drzewo("alfa", "x");
        CommandTree b = drzewo("beta", "x");
        assertEquals("alfa", new CommandIndex(Arrays.asList(a, b)).byName("x").getName());
        assertEquals("beta", new CommandIndex(Arrays.asList(b, a)).byName("x").getName());
    }

    @Test
    public void modelJestNiemutowalnyOdZewnatrz() {
        List<CmdArg> args = new ArrayList<CmdArg>();
        args.add(new CmdArg("a", ArgType.WORD, null, null, null));
        CmdNode node = new CmdNode("lit", args, null, false, null);
        args.clear(); // nie ma prawa wplynac na node
        assertEquals(1, node.getArgs().size());
        try {
            node.getArgs().add(new CmdArg("b", ArgType.WORD, null, null, null));
            fail("lista argumentow ma byc niemodyfikowalna");
        } catch (UnsupportedOperationException expected) {
            // ok
        }
    }

    @Test
    public void wszystkiePiecListJestNiemodyfikowalnych() {
        CmdArg arg = new CmdArg("a", ArgType.CHOICE, Arrays.asList("x", "y"), null, null);
        assertUnmodifiable(arg.getChoices());

        CmdNode lisc = new CmdNode("lisc", null, null, true, null);
        CmdNode node = new CmdNode("lit", Collections.singletonList(arg), Collections.singletonList(lisc),
                false, null);
        assertUnmodifiable(node.getArgs());
        assertUnmodifiable(node.getSub());

        CommandTree tree = new CommandTree("cmd", Arrays.asList("a1", "a2"), node);
        assertUnmodifiable(tree.getAliases());

        CommandIndex idx = new CommandIndex(Collections.singletonList(tree));
        assertUnmodifiable(idx.getCommands());
    }

    @Test
    public void executableIUsageSaZapamietywane() {
        CmdNode wykonywalny = new CmdNode("a", null, null, true, "uzycie");
        CmdNode niewykonywalny = new CmdNode("b", null, null, false, null);
        assertTrue(wykonywalny.isExecutable());
        assertEquals("uzycie", wykonywalny.getUsage());
        assertFalse(niewykonywalny.isExecutable());
        assertNull(niewykonywalny.getUsage());
    }

    @Test
    public void nullListyStajaSiePuste() {
        CmdNode node = new CmdNode("lit", null, null, false, null);
        assertTrue(node.getArgs().isEmpty());
        assertTrue(node.getSub().isEmpty());
        CmdArg arg = new CmdArg("a", null, null, null, null);
        assertSame(ArgType.UNKNOWN, arg.getType());
        assertTrue(arg.getChoices().isEmpty());
    }
}
