package com.spege.commandsuggest.core;

import static org.junit.Assert.assertEquals;
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
    public void nullListyStajaSiePuste() {
        CmdNode node = new CmdNode("lit", null, null, false, null);
        assertTrue(node.getArgs().isEmpty());
        assertTrue(node.getSub().isEmpty());
        CmdArg arg = new CmdArg("a", null, null, null, null);
        assertSame(ArgType.UNKNOWN, arg.getType());
        assertTrue(arg.getChoices().isEmpty());
    }
}
