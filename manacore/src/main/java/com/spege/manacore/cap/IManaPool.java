package com.spege.manacore.cap;

public interface IManaPool {

    double getCurrent();

    void setCurrent(double value);

    double getProgressionBonus();

    void setProgressionBonus(double value);

    /** Ustawiane przez warstwę sieci; oznacza, że klient wymaga odświeżenia. */
    boolean isDirty();

    void setDirty(boolean dirty);
}
