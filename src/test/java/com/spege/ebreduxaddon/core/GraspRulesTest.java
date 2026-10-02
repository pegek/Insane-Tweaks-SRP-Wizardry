package com.spege.ebreduxaddon.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GraspRulesTest {

    @Test
    void executesMobsBelowThresholdOnly() {
        assertTrue(GraspRules.shouldExecute(false, 4, 20, 0.2));
        assertFalse(GraspRules.shouldExecute(false, 4.1, 20, 0.2));
        assertFalse(GraspRules.shouldExecute(true, 1, 20, 0.2));
    }

    @Test
    void brokenInputNeverExecutes() {
        assertFalse(GraspRules.shouldExecute(false, 0, 20, 0.2));
        assertFalse(GraspRules.shouldExecute(false, 5, 0, 0.2));
        assertFalse(GraspRules.shouldExecute(false, 5, 20, Double.NaN));
        assertTrue(GraspRules.shouldExecute(false, 20, 20, 7.0));
    }

    @Test
    void reachUsesSlack() {
        assertTrue(GraspRules.inReach(225, 10, 1.5));
        assertFalse(GraspRules.inReach(226, 10, 1.5));
        assertTrue(GraspRules.inReach(100, 10, 0.5));
    }

    @Test
    void staleness() {
        assertFalse(GraspRules.stale(105, 100, 5));
        assertTrue(GraspRules.stale(106, 100, 5));
        assertTrue(GraspRules.stale(50, 100, 5));
    }
}
