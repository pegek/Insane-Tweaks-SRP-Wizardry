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
     * The flag is raised by the setters ({@link #setCurrent(double)}, {@link #setCastProgression(double)},
     * {@link #setItemProgression(double)}) on every real value change. The network layer only ever
     * clears it, by calling {@link #setDirty(boolean)} with {@code false} after sending a sync to
     * the client.
     */
    boolean isDirty();

    void setDirty(boolean dirty);
}
