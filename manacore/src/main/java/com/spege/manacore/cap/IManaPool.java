package com.spege.manacore.cap;

public interface IManaPool {

    double getCurrent();

    /** Sets the value; if it differs from the previous one, raises the {@code dirty} flag. */
    void setCurrent(double value);

    double getProgressionBonus();

    /** Sets the value; if it differs from the previous one, raises the {@code dirty} flag. */
    void setProgressionBonus(double value);

    /**
     * The flag is raised by the setters ({@link #setCurrent(double)}, {@link #setProgressionBonus(double)})
     * on every real value change. The network layer only ever clears it, by calling
     * {@link #setDirty(boolean)} with {@code false} after sending a sync to the client.
     */
    boolean isDirty();

    void setDirty(boolean dirty);
}
