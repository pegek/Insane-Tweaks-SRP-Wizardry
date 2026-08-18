package com.spege.manacore.cap;

public class ManaPool implements IManaPool {

    private double current;
    private double progressionBonus;
    private boolean dirty;

    @Override
    public double getCurrent() {
        return this.current;
    }

    @Override
    public void setCurrent(double value) {
        if (this.current != value) {
            this.current = value;
            this.dirty = true;
        }
    }

    @Override
    public double getProgressionBonus() {
        return this.progressionBonus;
    }

    @Override
    public void setProgressionBonus(double value) {
        if (this.progressionBonus != value) {
            this.progressionBonus = value;
            this.dirty = true;
        }
    }

    @Override
    public boolean isDirty() {
        return this.dirty;
    }

    @Override
    public void setDirty(boolean dirty) {
        this.dirty = dirty;
    }
}
