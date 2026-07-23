package com.spege.insanetweaks.sanctuary;

/** Why the dome is (or is not) active. Ordinal is synced to the client. */
public enum SanctuaryStatus {
    // Ordinal is persisted in NBT and synced to clients: APPEND ONLY, never reorder existing members.
    ACTIVE,          // tier >= 1, region registered
    NO_PYRAMID,      // no complete pyramid under the core
    DIM_BLACKLISTED; // this dimension is warded (dome inert)

    public static SanctuaryStatus byId(int id) {
        SanctuaryStatus[] v = values();
        return (id >= 0 && id < v.length) ? v[id] : NO_PYRAMID;
    }
}
