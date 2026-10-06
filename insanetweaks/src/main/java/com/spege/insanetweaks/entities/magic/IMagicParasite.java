package com.spege.insanetweaks.entities.magic;

/**
 * Implemented by this mod's magic parasite classes only. Never added to SRP's own hierarchy, which
 * must not gain interfaces (see CLAUDE.md, mixin rules). The state is a synced data parameter that
 * the glow layer reads.
 */
public interface IMagicParasite {

    byte STATE_IDLE = 0;
    byte STATE_TELEGRAPH = 1;
    byte STATE_WARD = 2;

    byte getArcaneState();

    void setArcaneState(byte state);
}
