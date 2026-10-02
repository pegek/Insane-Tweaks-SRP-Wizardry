package com.spege.tombtweaks.core;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Ile trwa cooldown ktorej ksiegi.
 *
 * <p>Format wpisu: {@code nazwa_rejestrowa;minuty}. Popsuty wpis jest pomijany, nigdy fatalny -
 * blad w configu nie ma prawa wywalic serwera. Minuty sa przycinane do 12 godzin, bo
 * wieksza wartosc to prawie na pewno pomylka, a nie zamiar.
 */
public final class CooldownRules {

    private static final long MILLIS_PER_MINUTE = 60_000L;
    private static final long MAX_MINUTES = 720L;

    private final Map<String, Long> millisById;

    private CooldownRules(Map<String, Long> millisById) {
        this.millisById = millisById;
    }

    public static CooldownRules parse(List<String> entries) {
        Map<String, Long> parsed = new HashMap<String, Long>();
        for (String raw : entries) {
            int separator = raw.indexOf(';');
            if (separator <= 0 || separator == raw.length() - 1) {
                continue;
            }
            String id = raw.substring(0, separator).trim();
            if (id.isEmpty()) {
                continue;
            }
            long minutes;
            try {
                minutes = Long.parseLong(raw.substring(separator + 1).trim());
            } catch (NumberFormatException malformed) {
                continue;
            }
            if (minutes <= 0L) {
                continue;
            }
            if (minutes > MAX_MINUTES) {
                minutes = MAX_MINUTES;
            }
            parsed.put(id, Long.valueOf(minutes * MILLIS_PER_MINUTE));
        }
        return new CooldownRules(parsed);
    }

    /** @return dlugosc cooldownu w ms, albo 0 gdy ta ksiega go nie ma */
    public long millisFor(String itemId) {
        Long millis = millisById.get(itemId);
        return millis == null ? 0L : millis.longValue();
    }

    public boolean isEmpty() {
        return millisById.isEmpty();
    }
}
