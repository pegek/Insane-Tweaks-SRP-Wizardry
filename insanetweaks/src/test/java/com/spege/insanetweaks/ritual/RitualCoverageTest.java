package com.spege.insanetweaks.ritual;

import static org.junit.Assert.assertEquals;

import java.util.Arrays;
import java.util.Collections;

import org.junit.Test;

public class RitualCoverageTest {

    @Test
    public void everyExclusiveItemHasARitual_nothingUncovered() {
        assertEquals(Collections.emptyList(), RitualCoverage.uncovered(
                Arrays.asList("insanetweaks:living_wand", "insanetweaks:magic_nucleus"),
                Arrays.asList("insanetweaks:magic_nucleus", "insanetweaks:living_wand", "xat:glowing_gem")));
    }

    @Test
    public void droppedRitual_isReportedSorted() {
        assertEquals(Arrays.asList("insanetweaks:living_aegis", "insanetweaks:living_wand"),
                RitualCoverage.uncovered(
                        Arrays.asList("insanetweaks:living_wand", "insanetweaks:magic_nucleus",
                                "insanetweaks:living_aegis"),
                        Arrays.asList("insanetweaks:magic_nucleus")));
    }

    @Test
    public void noRitualsAtAll_everythingUncovered() {
        assertEquals(Arrays.asList("insanetweaks:a", "insanetweaks:b"),
                RitualCoverage.uncovered(Arrays.asList("insanetweaks:b", "insanetweaks:a"),
                        Collections.<String>emptyList()));
    }
}
