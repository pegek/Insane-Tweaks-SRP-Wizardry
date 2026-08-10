package com.spege.commandsuggest.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.EnumSet;
import org.junit.Test;

public class ArgTypeTest {

    @Test
    public void byIdRozpoznajeZnaneTypy() {
        assertEquals(ArgType.PLAYER, ArgType.byId("player"));
        assertEquals(ArgType.INT, ArgType.byId("int"));
        assertEquals(ArgType.GREEDY, ArgType.byId("greedy"));
    }

    @Test
    public void byIdNieWywalaSieNaSmieciach() {
        assertEquals(ArgType.UNKNOWN, ArgType.byId("zupelnie-nowy-typ"));
        assertEquals(ArgType.UNKNOWN, ArgType.byId(null));
    }

    @Test
    public void serverResolvedTylkoDlaTrzechTypow() {
        EnumSet<ArgType> serverResolved = EnumSet.of(ArgType.WORD, ArgType.GREEDY, ArgType.UNKNOWN);
        for (ArgType t : ArgType.values()) {
            if (serverResolved.contains(t)) {
                assertTrue(t.name(), t.isServerResolved());
            } else {
                assertFalse(t.name(), t.isServerResolved());
            }
        }
    }

    @Test
    public void idJestStabilnyBoIdzieDoJsona() {
        for (ArgType t : ArgType.values()) {
            assertEquals(t, ArgType.byId(t.getId()));
        }
    }
}
