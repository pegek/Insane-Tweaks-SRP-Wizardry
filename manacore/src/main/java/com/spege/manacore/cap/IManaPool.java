package com.spege.manacore.cap;

public interface IManaPool {

    double getCurrent();

    /** Ustawia wartość; jeśli różni się od poprzedniej, podnosi flagę {@code dirty}. */
    void setCurrent(double value);

    double getProgressionBonus();

    /** Ustawia wartość; jeśli różni się od poprzedniej, podnosi flagę {@code dirty}. */
    void setProgressionBonus(double value);

    /**
     * Flagę podnoszą settery ({@link #setCurrent(double)}, {@link #setProgressionBonus(double)})
     * przy każdej realnej zmianie wartości. Warstwa sieci ją wyłącznie czyści, wołając
     * {@link #setDirty(boolean)} z {@code false} po wysłaniu synchronizacji do klienta.
     */
    boolean isDirty();

    void setDirty(boolean dirty);
}
