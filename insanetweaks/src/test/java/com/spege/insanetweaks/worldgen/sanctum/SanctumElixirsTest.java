package com.spege.insanetweaks.worldgen.sanctum;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

import org.junit.Test;

public class SanctumElixirsTest {

    private static final int RUNS = 2000;

    private static double[] noise(Random r) {
        double[] n = new double[SanctumElixirs.NOISE_VALUES];
        for (int i = 0; i < n.length; i++) {
            n[i] = r.nextDouble();
        }
        return n;
    }

    private static boolean vanilla(String id) {
        return id.startsWith("minecraft:");
    }

    @Test
    public void vanillaTwoOrThreeDistinct() {
        Random r = new Random(1L);
        for (int i = 0; i < RUNS; i++) {
            Set<String> ids = new HashSet<String>();
            int count = 0;
            for (String[] e : SanctumElixirs.recipe(noise(r))) {
                if (vanilla(e[0])) {
                    count++;
                    assertTrue("duplicate " + e[0], ids.add(e[0]));
                }
            }
            assertTrue("vanilla count " + count, count >= 2 && count <= 3);
        }
    }

    @Test
    public void durationsInRange() {
        Random r = new Random(2L);
        for (int i = 0; i < RUNS; i++) {
            for (String[] e : SanctumElixirs.recipe(noise(r))) {
                int ticks = Integer.parseInt(e[2]);
                if (vanilla(e[0])) {
                    assertTrue(e[0] + " " + ticks, ticks >= 3600 && ticks <= 8400);
                } else {
                    assertTrue(e[0] + " " + ticks, ticks >= 1200 && ticks <= 3600);
                }
            }
        }
    }

    @Test
    public void extrasAtMostOneEach() {
        Random r = new Random(3L);
        boolean sawInsane = false;
        boolean sawWizardry = false;
        for (int i = 0; i < RUNS; i++) {
            int insane = 0;
            int wizardry = 0;
            List<String[]> recipe = SanctumElixirs.recipe(noise(r));
            for (String[] e : recipe) {
                insane += e[0].startsWith("insanetweaks:") ? 1 : 0;
                wizardry += e[0].startsWith("ebwizardry:") ? 1 : 0;
            }
            assertTrue(insane <= 1 && wizardry <= 1);
            sawInsane |= insane == 1;
            sawWizardry |= wizardry == 1;
        }
        assertTrue(sawInsane && sawWizardry);
    }

    @Test
    public void noImmuneBondNoStasisNoCurse() {
        Random r = new Random(4L);
        for (int i = 0; i < RUNS; i++) {
            for (String[] e : SanctumElixirs.recipe(noise(r))) {
                assertFalse(e[0].equals("insanetweaks:immune_bond"));
                assertFalse(e[0].equals("insanetweaks:gilded_stasis"));
                assertFalse(e[0].equals("insanetweaks:wits_hunger"));
            }
        }
    }

    @Test
    public void nourishedIsLevelTwo() {
        Random r = new Random(5L);
        for (int i = 0; i < RUNS; i++) {
            for (String[] e : SanctumElixirs.recipe(noise(r))) {
                if (e[0].equals("insanetweaks:nourished")) {
                    assertTrue(Integer.parseInt(e[1]) >= 1);
                }
            }
        }
    }
}
