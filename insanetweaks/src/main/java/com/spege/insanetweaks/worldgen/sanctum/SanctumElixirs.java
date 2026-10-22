package com.spege.insanetweaks.worldgen.sanctum;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * The recipe of one Sanctum Elixir, the shulker's placeholder reward until the loot pass. Pure
 * Java: the caller passes {@link #NOISE_VALUES} values in [0, 1) (positional noise), and gets back
 * effect entries {id, amplifier, ticks}.
 *
 * <ul>
 *   <li>2-3 distinct vanilla effects at level II (fire resistance and night vision have one
 *   level), 3-7 minutes each;</li>
 *   <li>50%: one insanetweaks effect, Cleanse or Nourished (level II - it does nothing at I),
 *   1-3 minutes. Immune Bond (works only through its spell), Gilded Stasis (freezes the drinker)
 *   and Wit's Hunger (a curse) are left out;</li>
 *   <li>50%: one EBW buff-spell effect, 1-3 minutes.</li>
 * </ul>
 */
public final class SanctumElixirs {

    public static final int NOISE_VALUES = 16;

    private static final String[] VANILLA = { "minecraft:speed", "minecraft:strength", "minecraft:regeneration",
            "minecraft:resistance", "minecraft:fire_resistance", "minecraft:haste", "minecraft:jump_boost",
            "minecraft:absorption", "minecraft:night_vision" };
    private static final String[] INSANE = { "insanetweaks:cleanse", "insanetweaks:nourished" };
    private static final int[] INSANE_AMPLIFIER = { 0, 1 };
    private static final String[] WIZARDRY = { "ebwizardry:ward", "ebwizardry:empowerment", "ebwizardry:font_of_mana",
            "ebwizardry:fireskin", "ebwizardry:ice_shroud", "ebwizardry:static_aura", "ebwizardry:sixth_sense",
            "ebwizardry:frost_step" };

    private SanctumElixirs() {
    }

    public static List<String[]> recipe(double[] n) {
        List<String[]> out = new ArrayList<String[]>();
        List<String> pool = new ArrayList<String>(Arrays.asList(VANILLA));
        int count = n[0] < 0.5D ? 2 : 3;
        for (int i = 0; i < count; i++) {
            String id = pool.remove(pick(n[1 + i], pool.size()));
            boolean single = id.equals("minecraft:fire_resistance") || id.equals("minecraft:night_vision");
            out.add(entry(id, single ? 0 : 1, ticks(n[5 + i], 3600, 8400)));
        }
        if (n[9] < 0.5D) {
            int k = pick(n[10], INSANE.length);
            out.add(entry(INSANE[k], INSANE_AMPLIFIER[k], ticks(n[11], 1200, 3600)));
        }
        if (n[12] < 0.5D) {
            out.add(entry(WIZARDRY[pick(n[13], WIZARDRY.length)], 0, ticks(n[14], 1200, 3600)));
        }
        return out;
    }

    private static int pick(double v, int size) {
        return Math.min(size - 1, (int) (v * size));
    }

    private static int ticks(double v, int min, int max) {
        return Math.min(max, min + (int) (v * (max - min + 1)));
    }

    private static String[] entry(String id, int amplifier, int ticks) {
        return new String[] { id, Integer.toString(amplifier), Integer.toString(ticks) };
    }
}
