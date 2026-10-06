package com.spege.insanetweaks.entities.sapling;

/**
 * Why a Corrupted Seed cannot be planted here, or why a running defense is paused. One lang key
 * each ({@code msg.insanetweaks.sapling.problem.<id>}), shared by the planting refusal, the action
 * bar and the boss bar.
 */
public enum SiteProblem {
    OWNER_AWAY("owner_away"),
    NO_RING("no_ring"),
    DIMENSION("dimension"),
    PHASE_TOO_LOW("phase_too_low"),
    NO_INFESTATION("no_infestation"),
    NO_GROUND("no_ground"),
    ROOFED("roofed"),
    CROWDED("crowded"),
    SANCTUARY("sanctuary"),
    ALREADY_ACTIVE("already_active");

    private final String id;

    SiteProblem(String id) {
        this.id = id;
    }

    public String langKey() {
        return "msg.insanetweaks.sapling.problem." + this.id;
    }
}
