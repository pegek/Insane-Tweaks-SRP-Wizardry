package com.spege.commandsuggest.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

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
        assertTrue(ArgType.WORD.isServerResolved());
        assertTrue(ArgType.GREEDY.isServerResolved());
        assertTrue(ArgType.UNKNOWN.isServerResolved());
        assertFalse(ArgType.PLAYER.isServerResolved());
        assertFalse(ArgType.ITEM.isServerResolved());
        assertFalse(ArgType.INT.isServerResolved());
    }

    @Test
    public void idJestStabilnyBoIdzieDoJsona() {
        for (ArgType t : ArgType.values()) {
            assertEquals(t, ArgType.byId(t.getId()));
        }
    }
}
