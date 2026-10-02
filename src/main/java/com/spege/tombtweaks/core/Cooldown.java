package com.spege.tombtweaks.core;

/** Arytmetyka pozostalego czasu. Zegar scienny w milisekundach. */
public final class Cooldown {

    private Cooldown() {
    }

    /**
     * @return ile ms zostalo, 0 gdy wolne
     */
    public static long remaining(long lastUseMillis, long cooldownMillis, long nowMillis) {
        if (cooldownMillis <= 0L || lastUseMillis <= 0L) {
            return 0L;
        }
        if (nowMillis < lastUseMillis) {
            return 0L; // zegar sie cofnal - nie zamykamy gracza na wieki
        }
        long elapsed = nowMillis - lastUseMillis;
        return elapsed >= cooldownMillis ? 0L : cooldownMillis - elapsed;
    }

    public static boolean isActive(long lastUseMillis, long cooldownMillis, long nowMillis) {
        return remaining(lastUseMillis, cooldownMillis, nowMillis) > 0L;
    }
}
