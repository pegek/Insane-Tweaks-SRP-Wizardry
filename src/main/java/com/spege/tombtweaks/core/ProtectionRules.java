package com.spege.tombtweaks.core;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Czego rozkladajacy sie grob nie ma prawa zjesc.
 *
 * <p>Cztery niezalezne reguly; stack jest chroniony, gdy pasuje KTORAKOLWIEK.
 *
 * <p>Zasada, ktorej nie wolno zlamac: <b>pusta lista nie chroni niczego</b>. Odwrotna
 * interpretacja zamienilaby domyslna konfiguracje w grob, ktory nigdy nic nie traci,
 * czyli w feature wygladajacy na dzialajacy i nierobiacy nic.
 */
public final class ProtectionRules {

    private final Set<String> items;
    private final List<String> prefixes;
    private final Set<String> enchantments;
    private final List<String[]> nbtPairs;

    private ProtectionRules(Set<String> items, List<String> prefixes,
                            Set<String> enchantments, List<String[]> nbtPairs) {
        this.items = items;
        this.prefixes = prefixes;
        this.enchantments = enchantments;
        this.nbtPairs = nbtPairs;
    }

    public static ProtectionRules of(List<?> items, List<?> prefixes,
                                     List<?> enchantments, List<?> nbtStrings) {
        Set<String> parsedItems = new HashSet<String>();
        for (String raw : strings(items)) {
            String value = raw.trim();
            if (!value.isEmpty()) {
                parsedItems.add(value);
            }
        }

        List<String> parsedPrefixes = new ArrayList<String>();
        for (String raw : strings(prefixes)) {
            String value = raw.trim();
            if (!value.isEmpty()) {
                parsedPrefixes.add(value);
            }
        }

        Set<String> parsedEnchantments = new HashSet<String>();
        for (String raw : strings(enchantments)) {
            String value = raw.trim();
            if (!value.isEmpty()) {
                parsedEnchantments.add(value);
            }
        }

        List<String[]> parsedPairs = new ArrayList<String[]>();
        for (String raw : strings(nbtStrings)) {
            int equals = raw.indexOf('=');
            if (equals <= 0 || equals == raw.length() - 1) {
                continue; // popsuty wpis jest ignorowany, nigdy fatalny
            }
            String key = raw.substring(0, equals).trim();
            String value = raw.substring(equals + 1).trim();
            if (!key.isEmpty() && !value.isEmpty()) {
                parsedPairs.add(new String[] { key, value });
            }
        }

        return new ProtectionRules(parsedItems, parsedPrefixes, parsedEnchantments, parsedPairs);
    }

    /** Same Stringi z listy configu; null, nie-Stringi i cala lista null sa pomijane. */
    private static List<String> strings(List<?> raw) {
        List<String> out = new ArrayList<String>();
        if (raw == null) {
            return out;
        }
        for (Object element : raw) {
            if (element instanceof String) {
                out.add((String) element);
            }
        }
        return out;
    }

    public boolean isProtected(StackView stack) {
        String id = stack.itemId();

        if (items.contains(id)) {
            return true;
        }
        for (int i = 0; i < prefixes.size(); i++) {
            if (id.startsWith(prefixes.get(i))) {
                return true;
            }
        }
        if (!enchantments.isEmpty()) {
            for (String enchantment : stack.enchantmentIds()) {
                if (enchantments.contains(enchantment)) {
                    return true;
                }
            }
        }
        for (int i = 0; i < nbtPairs.size(); i++) {
            String[] pair = nbtPairs.get(i);
            String value = stack.nbtString(pair[0]);
            if (value != null && value.equals(pair[1])) {
                return true;
            }
        }
        return false;
    }
}
