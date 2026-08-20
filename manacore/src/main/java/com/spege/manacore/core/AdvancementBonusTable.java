package com.spege.manacore.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Parses the {@code advancements.bonuses} config table - lines of {@code namespace:path=amount} -
 * into a lookup of advancement id to max-mana bonus.
 *
 * <p>ZERO Minecraft types, deliberately: advancement ids stay plain strings here and become
 * {@code ResourceLocation}s only in the handler. That is what lets this class carry the whole
 * fiddly part of the feature - the part with an actual chance of being wrong - under JUnit on a
 * plain JVM, the same split that gives {@code commandsuggest} its test suite.
 *
 * <p>A malformed entry is dropped and recorded in {@link Table#rejected()} rather than throwing.
 * One typo must not disable the other fifteen lines, and a config edit is exactly where typos
 * happen.
 */
public final class AdvancementBonusTable {

    private AdvancementBonusTable() {
    }

    /** Result of parsing: the usable entries, plus a note for every line that was thrown out. */
    public static final class Table {

        private final Map<String, Double> bonuses;
        private final List<String> rejected;

        Table(Map<String, Double> bonuses, List<String> rejected) {
            this.bonuses = bonuses;
            this.rejected = rejected;
        }

        /** Advancement id to bonus, in config order. Never null. */
        public Map<String, Double> bonuses() {
            return this.bonuses;
        }

        /** One human-readable line per dropped entry, ready to log. Empty when all parsed. */
        public List<String> rejected() {
            return this.rejected;
        }

        public boolean isEmpty() {
            return this.bonuses.isEmpty();
        }
    }

    /**
     * Parses the raw config array.
     *
     * <p>Blank and null lines are skipped silently - a blank line in a config file is not a
     * mistake worth a warning. Everything else that fails to parse lands in
     * {@link Table#rejected()}.
     */
    public static Table parse(String[] entries) {
        Map<String, Double> bonuses = new LinkedHashMap<String, Double>();
        List<String> rejected = new ArrayList<String>();

        if (entries != null) {
            for (String raw : entries) {
                if (raw == null) {
                    continue;
                }
                String entry = raw.trim();
                if (entry.isEmpty()) {
                    continue;
                }
                parseOne(entry, bonuses, rejected);
            }
        }

        return new Table(Collections.unmodifiableMap(bonuses), Collections.unmodifiableList(rejected));
    }

    private static void parseOne(String entry, Map<String, Double> bonuses, List<String> rejected) {
        // Split on the LAST '=': an advancement id contains a colon and slashes but never an
        // equals sign, while the value may carry a minus or a decimal point. Splitting from the
        // right is therefore unambiguous where a plain split("=") would not be.
        int split = entry.lastIndexOf('=');
        if (split < 0) {
            rejected.add(entry + " (no '=' separating the id from the amount)");
            return;
        }

        String id = entry.substring(0, split).trim();
        String value = entry.substring(split + 1).trim();

        if (id.isEmpty()) {
            rejected.add(entry + " (empty advancement id)");
            return;
        }
        // Required, never defaulted to "minecraft:". Silently reinterpreting `crystal` as
        // `minecraft:crystal` would produce an entry that quietly matches nothing, which is
        // harder to notice than a warning.
        if (id.indexOf(':') < 0) {
            rejected.add(entry + " (advancement id has no namespace, expected 'modid:path')");
            return;
        }

        double amount;
        try {
            amount = Double.parseDouble(value);
        } catch (NumberFormatException e) {
            rejected.add(entry + " (amount '" + value + "' is not a number)");
            return;
        }
        // Double.parseDouble accepts "NaN" and "Infinity" as valid literals, so this is a real
        // input path, not a theoretical one - and either value would poison the attribute.
        if (Double.isNaN(amount) || Double.isInfinite(amount)) {
            rejected.add(entry + " (amount must be a finite number)");
            return;
        }

        // Last one wins: appending a corrected line at the end of the list overrides an earlier
        // one, rather than being silently ignored in favour of it.
        bonuses.put(id, Double.valueOf(amount));
    }

    /**
     * Clamps a summed bonus into {@code [0, cap]}.
     *
     * <p>Negative individual amounts are allowed in the table (an advancement that costs the
     * player maximum mana is a reasonable mechanic), so the sum can legitimately go negative -
     * the floor here is what keeps that from driving the pool below zero. A negative cap is
     * treated as zero rather than as an inverted range.
     */
    public static double clampTotal(double sum, double cap) {
        if (Double.isNaN(sum)) {
            return 0.0D;
        }
        double ceiling = cap > 0.0D ? cap : 0.0D;
        if (sum < 0.0D) {
            return 0.0D;
        }
        return sum > ceiling ? ceiling : sum;
    }
}
