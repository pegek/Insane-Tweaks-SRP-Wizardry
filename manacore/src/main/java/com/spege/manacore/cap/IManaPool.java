package com.spege.manacore.cap;

public interface IManaPool {

    double getCurrent();

    /** Sets the value; if it differs from the previous one, raises the {@code dirty} flag. */
    void setCurrent(double value);

    /** Permanent progression accrued from successful spell casts. */
    double getCastProgression();

    /** Sets the value; if it differs from the previous one, raises the {@code dirty} flag. */
    void setCastProgression(double value);

    /** Permanent progression accrued from consumed items (Trinkets and Baubles' Mana Crystal, etc). */
    double getItemProgression();

    /** Sets the value; if it differs from the previous one, raises the {@code dirty} flag. */
    void setItemProgression(double value);

    /**
     * Flat maximum mana granted outright, outside the two progression budgets and their caps:
     * today the {@code /mana setmax} command, later one-shot awards such as an achievement.
     *
     * <p>This exists because a max-mana bonus that lives only as an attribute modifier does NOT
     * survive death. Vanilla builds a fresh EntityPlayerMP on respawn and never copies the
     * attribute map, so anything not stored here and re-applied afterwards is silently lost.
     *
     * <p>Only for sources that cannot be recomputed from anything else. A source derived from
     * external state - a Reskillable skill level, a worn item - should own an attribute modifier
     * with its own UUID and recompute it, rather than adding into this single shared number.
     */
    double getGrantedMax();

    /** Sets the value; if it differs from the previous one, raises the {@code dirty} flag. */
    void setGrantedMax(double value);

    /**
     * The flag is raised by the setters ({@link #setCurrent(double)}, {@link #setCastProgression(double)},
     * {@link #setItemProgression(double)}, {@link #setGrantedMax(double)}) on every real value change. The network layer only ever
     * clears it, by calling {@link #setDirty(boolean)} with {@code false} after sending a sync to
     * the client.
     */
    boolean isDirty();

    void setDirty(boolean dirty);
}
