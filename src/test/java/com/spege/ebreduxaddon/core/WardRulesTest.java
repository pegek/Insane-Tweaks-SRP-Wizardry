package com.spege.ebreduxaddon.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WardRulesTest {

    @Test
    void firesOnTheIntervalOnlyInCombat() {
        assertTrue(WardRules.due(200, 200, true));
        assertTrue(WardRules.due(400, 200, true));
        assertFalse(WardRules.due(199, 200, true));
        assertFalse(WardRules.due(200, 200, false));
        assertFalse(WardRules.due(0, 200, true));
    }

    @Test
    void badIntervalDoesNotDivideByZero() {
        assertTrue(WardRules.due(7, 0, true));
        assertTrue(WardRules.due(7, -5, true));
    }

    @Test
    void onlyOtherHiveMembersThatAreNotMycomancers() {
        assertTrue(WardRules.receives(false, true, false, true));
        assertFalse(WardRules.receives(true, true, true, true), "itself");
        assertFalse(WardRules.receives(false, true, true, true), "another mycomancer");
        assertFalse(WardRules.receives(false, false, false, true), "player or cow");
        assertFalse(WardRules.receives(false, true, false, false), "dead");
    }
}
