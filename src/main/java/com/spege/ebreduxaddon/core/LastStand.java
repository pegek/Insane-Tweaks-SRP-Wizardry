package com.spege.ebreduxaddon.core;

/**
 * Last Stand, bonus pelnego zestawu z 1.12.2 (tam: Grave Defiance, ArmorEventHandler.onLivingDeath).
 * W stylu Totemu Niesmiertelnosci: smierc zostaje anulowana, gracz wstaje z kilkoma HP, dostaje
 * krotka nietykalnosc i oczyszczenie, a zestaw idzie na cooldown.
 *
 * <p>Czas liczymy w tickach gry swiata (wspolnych dla wszystkich wymiarow), jak na 1.12.2, wiec
 * cooldown nie plynie, gdy serwer stoi.
 */
public final class LastStand {

    private LastStand() {
    }

    /**
     * @param bypassesInvulnerability zrodlo w stylu /kill albo pustki: bonus go nie zatrzymuje, zeby
     *                                nie spalic cooldownu na smierc, ktora i tak nastapi
     * @param fullSet                 cztery sloty zbroi zajete przez Grafted lub Sentient, w dowolnym
     *                                miksie (jak na 1.12.2: Living lub Sentient)
     * @param lastFiredTick           &lt;= 0, gdy jeszcze nigdy
     */
    public static boolean fires(boolean enabled, boolean bypassesInvulnerability, boolean fullSet,
                                long nowTick, long lastFiredTick, long cooldownTicks) {
        return enabled && !bypassesInvulnerability && fullSet
                && cooledDown(nowTick, lastFiredTick, cooldownTicks);
    }

    /**
     * Cofniety zegar (np. /time set na mniejsza wartosc, przywrocony backup) liczy sie jako
     * uplyniety cooldown. Inaczej bonus bylby martwy, az czas dogoni stara wartosc.
     */
    public static boolean cooledDown(long nowTick, long lastFiredTick, long cooldownTicks) {
        return lastFiredTick <= 0L
                || nowTick < lastFiredTick
                || nowTick - lastFiredTick >= Math.max(0L, cooldownTicks);
    }

    /** Okno nietykalnosci po odpaleniu: [last, last + window). */
    public static boolean immune(long nowTick, long lastFiredTick, long windowTicks) {
        return lastFiredTick > 0L && nowTick >= lastFiredTick && nowTick - lastFiredTick < windowTicks;
    }
}
