package com.spege.insanetweaks.ritual;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Which altar-exclusive items ended up with no ritual. Minecraft-free so it can be unit-tested.
 *
 * <p>Needed because the two halves run at different times: our table recipes for these items are
 * stripped during {@code RegistryEvent.Register<IRecipe>}, before the rituals exist (FML init). A
 * ritual that is then dropped for a missing ingredient leaves its item with no recipe anywhere, and
 * nothing else would say so.
 */
public final class RitualCoverage {

    private RitualCoverage() {
    }

    /** Registry names in {@code exclusive} that no ritual produces, sorted. */
    public static List<String> uncovered(Collection<String> exclusive, Collection<String> produced) {
        Set<String> made = new HashSet<String>(produced);
        List<String> out = new ArrayList<String>();
        for (String id : exclusive) {
            if (!made.contains(id)) {
                out.add(id);
            }
        }
        Collections.sort(out);
        return out;
    }
}
