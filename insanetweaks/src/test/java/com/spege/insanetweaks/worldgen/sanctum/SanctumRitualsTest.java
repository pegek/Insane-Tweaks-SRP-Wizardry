package com.spege.insanetweaks.worldgen.sanctum;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class SanctumRitualsTest {

    /**
     * ASC 1.8.3 lays the pattern grid out centred on the centrepiece rune and compares every cell,
     * so a ritual whose grid centre is some other rune can never start (Elemental and Warlock
     * Attunement). Every ritual we build must pass this.
     */
    @Test
    public void gridCentreIsTheCentrepiece() {
        for (int r = 0; r < SanctumRituals.RITUAL_COUNT; r++) {
            String[] p = SanctumRituals.pattern(r);
            int h = p.length;
            int w = p[0].length();
            assertEquals(SanctumRituals.id(r), SanctumRituals.centrepiece(r), p[h / 2].charAt(w / 2));
            assertNotNull(SanctumRituals.rune(r, SanctumRituals.centrepiece(r)));
            for (String row : p) {
                assertEquals("square rows", w, row.length());
            }
        }
    }

    @Test
    public void everyKeyResolves() {
        for (int r = 0; r < SanctumRituals.RITUAL_COUNT; r++) {
            for (String row : SanctumRituals.pattern(r)) {
                for (char c : row.toCharArray()) {
                    if (c != ' ') {
                        String id = SanctumRituals.rune(r, c);
                        assertNotNull(SanctumRituals.id(r) + " key " + c, id);
                        assertTrue(id, id.startsWith("ancientspellcraft:rune_"));
                    }
                }
            }
        }
    }

    @Test
    public void onlyCondensingHasACatalyst() {
        assertEquals(null, SanctumRituals.catalyst(SanctumRituals.REJUVENATION));
        assertEquals(null, SanctumRituals.catalyst(SanctumRituals.BONFIRE));
        assertEquals("ebwizardry:condenser_upgrade", SanctumRituals.catalyst(SanctumRituals.CONDENSING));
    }
}
