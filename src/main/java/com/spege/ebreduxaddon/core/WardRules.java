package com.spege.ebreduxaddon.core;

/**
 * Oslona grzybni Mykomanty (spec 0.3, sekcja 4): kiedy dziala i kto ja dostaje. Bez typow Minecrafta,
 * zeby dalo sie to sprawdzic na golym JVM.
 */
public final class WardRules {

    private WardRules() {
    }

    /**
     * Oslona odpala co {@code interval} tickow i tylko w walce. Interwal ponizej 1 traktujemy jak 1,
     * zeby zly config nie dzielil przez zero.
     */
    public static boolean due(int tickCount, int interval, boolean hasTarget) {
        return hasTarget && tickCount > 0 && tickCount % Math.max(1, interval) == 0;
    }

    /**
     * Kto dostaje efekt: tylko czlonkowie roju Spore, bez samego rzucajacego i bez innych Mykomant -
     * dwie Mykomanty obok siebie nie maja wzmacniac sie nawzajem w nieskonczonosc.
     */
    public static boolean receives(boolean self, boolean hiveMember, boolean mycomancer, boolean alive) {
        return alive && hiveMember && !self && !mycomancer;
    }
}
