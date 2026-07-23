package com.spege.insanetweaks.sanctuary;

/** Display state of the cleanse function. Derived, not persisted. */
public enum CleanseState {
    RUNNING, // enabled, tier active, fuel available
    STALLED, // enabled, tier active, but out of fuel
    OFF;     // disabled by toggle, or no active tier

    public static CleanseState of(int tier, boolean cleanseEnabled, boolean cleanseStalled) {
        if (!cleanseEnabled || tier < 1) { return OFF; }
        return cleanseStalled ? STALLED : RUNNING;
    }
}
