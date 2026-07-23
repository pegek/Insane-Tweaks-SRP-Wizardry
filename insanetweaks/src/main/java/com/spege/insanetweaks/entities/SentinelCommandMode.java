package com.spege.insanetweaks.entities;

public enum SentinelCommandMode {
    FOLLOW(0, "follow"),
    GUARD(1, "guard");

    private final int id;
    private final String translationSuffix;

    SentinelCommandMode(int id, String translationSuffix) {
        this.id = id;
        this.translationSuffix = translationSuffix;
    }

    public int getId() {
        return this.id;
    }

    public String getTranslationSuffix() {
        return this.translationSuffix;
    }

    public static SentinelCommandMode fromId(int id) {
        for (SentinelCommandMode mode : values()) {
            if (mode.id == id) {
                return mode;
            }
        }

        return FOLLOW;
    }
}
