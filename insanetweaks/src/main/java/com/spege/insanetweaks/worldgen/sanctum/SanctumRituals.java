package com.spege.insanetweaks.worldgen.sanctum;

/**
 * The Ancient Spellcraft rituals a sanctum's ritual room can hold, copied from ASC 1.8.3's
 * {@code assets/ancientspellcraft/rituals/*.json}. Pure Java.
 *
 * <p>ASC matches a ritual by laying its pattern grid out centred on the centrepiece rune
 * ({@code TileRune.update}: row i is {@code i - h/2} blocks north, column j is {@code j - w/2}
 * blocks west) and comparing every cell, blanks included, in four rotations. So only rituals whose
 * grid centre IS the centrepiece can ever start - Elemental and Warlock Attunement cannot, and are
 * not offered. The room is always built without its centrepiece; the player places it.
 */
public final class SanctumRituals {

    public static final int REJUVENATION = 0;
    public static final int BONFIRE = 1;
    public static final int CONDENSING = 2;
    public static final int RITUAL_COUNT = 3;

    private static final String[] IDS = { "rejuvenation", "bonfire", "condensing" };

    private static final String[][] PATTERNS = {
            { " M ", "MOM", " M " },
            { " S ", "APN", " W " },
            { "C O N", " D E ", "  I  ", " S N ", "N   G" } };

    /** Per ritual: pairs of key char and rune name. */
    private static final String[][] KEYS = {
            { "M", "mannaz", "O", "odal" },
            { "S", "sowilo", "A", "ansuz", "P", "peorth", "N", "naudiz", "W", "wynn" },
            { "C", "kaunan", "O", "odal", "N", "naudiz", "D", "dagaz", "E", "ehwaz", "I", "isaz", "S", "sowilo",
                    "G", "gyfu" } };

    private static final char[] CENTREPIECES = { 'O', 'P', 'I' };

    private static final String[] CATALYSTS = { null, null, "ebwizardry:condenser_upgrade" };

    private SanctumRituals() {
    }

    public static String id(int ritual) {
        return IDS[ritual];
    }

    public static String[] pattern(int ritual) {
        return PATTERNS[ritual].clone();
    }

    /** The rune item id for a pattern character, or null if the ritual has no such key. */
    public static String rune(int ritual, char key) {
        String[] keys = KEYS[ritual];
        for (int i = 0; i < keys.length; i += 2) {
            if (keys[i].charAt(0) == key) {
                return "ancientspellcraft:rune_" + keys[i + 1];
            }
        }
        return null;
    }

    public static char centrepiece(int ritual) {
        return CENTREPIECES[ritual];
    }

    /** An item thrown onto the centrepiece to begin the ritual, or null if completing it is enough. */
    public static String catalyst(int ritual) {
        return CATALYSTS[ritual];
    }
}
