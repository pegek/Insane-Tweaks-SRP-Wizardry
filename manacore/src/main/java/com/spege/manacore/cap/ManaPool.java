package com.spege.manacore.cap;

public class ManaPool implements IManaPool {

    private double current;
    private double castProgression;
    private double itemProgression;
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
    public double getCastProgression() {
        return this.castProgression;
    }

    @Override
    public void setCastProgression(double value) {
        if (this.castProgression != value) {
            this.castProgression = value;
            this.dirty = true;
        }
    }

    @Override
    public double getItemProgression() {
        return this.itemProgression;
    }

    @Override
    public void setItemProgression(double value) {
        if (this.itemProgression != value) {
            this.itemProgression = value;
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
