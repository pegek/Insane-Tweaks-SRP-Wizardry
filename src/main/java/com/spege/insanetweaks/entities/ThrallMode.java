package com.spege.insanetweaks.entities;

public enum ThrallMode {
    FOLLOW("follow"),
    STAY("stay"),
    WOODCUTTING("woodcutting"),
    MINESHAFT("mineshaft"),
    FARMING("farming"),
    PORTER("porter"),
    COLLECTING("collecting");

    private final String translationSuffix;

    ThrallMode(String translationSuffix) {
        this.translationSuffix = translationSuffix;
    }

    public String getTranslationSuffix() {
        return translationSuffix;
    }
}
