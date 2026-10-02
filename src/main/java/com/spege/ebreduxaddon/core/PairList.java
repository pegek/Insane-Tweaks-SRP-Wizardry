package com.spege.ebreduxaddon.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Parser listy par "zrodlo|cel" z configu, w formacie listy konwersji Spore.
 *
 * <p>Odrzucone wpisy wracaja osobno, zeby warstwa platformy mogla je zalogowac raz, zamiast
 * zgadywac. Duplikat zrodla: wygrywa pierwszy wpis, kolejne sa odrzucane.
 *
 * @param pairs    zrodlo -> cel, w kolejnosci z configu
 * @param rejected wpisy, ktorych nie dalo sie przeczytac
 */
public record PairList(Map<String, String> pairs, List<String> rejected) {

    public static PairList parse(List<? extends String> entries) {
        Map<String, String> pairs = new LinkedHashMap<>();
        List<String> rejected = new ArrayList<>();
        if (entries != null) {
            for (String raw : entries) {
                if (raw == null) {
                    continue;
                }
                String[] parts = raw.split("\\|", -1);
                if (parts.length != 2) {
                    rejected.add(raw);
                    continue;
                }
                String from = parts[0].trim();
                String to = parts[1].trim();
                if (!isId(from) || !isId(to) || pairs.containsKey(from)) {
                    rejected.add(raw);
                    continue;
                }
                pairs.put(from, to);
            }
        }
        return new PairList(Collections.unmodifiableMap(pairs), Collections.unmodifiableList(rejected));
    }

    /** Minimalna forma "namespace:path": dokladnie jeden dwukropek, obie strony niepuste. */
    static boolean isId(String s) {
        int colon = s.indexOf(':');
        return colon > 0 && colon < s.length() - 1 && s.indexOf(':', colon + 1) < 0;
    }
}
