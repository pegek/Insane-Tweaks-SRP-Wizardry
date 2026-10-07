package com.spege.insanetweaks.anchor;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The Petrified Anchor's weighted mob pool, parsed from config lines {@code "modid:entity;weight"}.
 * A missing weight means 1; zero, negative or unparseable weights and blank lines are rejected
 * (and reported by the caller). Pure Java, so it is unit-tested.
 */
public final class AnchorPool {

    private final String[] ids;
    private final int[] cumulative;
    private final int total;
    private final List<String> rejected;

    private AnchorPool(List<String> ids, List<Integer> weights, List<String> rejected) {
        this.ids = ids.toArray(new String[0]);
        this.cumulative = new int[weights.size()];
        int sum = 0;
        for (int i = 0; i < weights.size(); i++) {
            sum += weights.get(i).intValue();
            this.cumulative[i] = sum;
        }
        this.total = sum;
        this.rejected = Collections.unmodifiableList(rejected);
    }

    public static AnchorPool parse(String[] lines) {
        List<String> ids = new ArrayList<String>();
        List<Integer> weights = new ArrayList<Integer>();
        List<String> rejected = new ArrayList<String>();
        for (String raw : lines) {
            if (raw == null || raw.trim().isEmpty()) {
                continue;
            }
            String[] parts = raw.split(";", -1);
            String id = parts[0].trim();
            int weight = 1;
            if (parts.length > 1) {
                try {
                    weight = Integer.parseInt(parts[1].trim());
                } catch (NumberFormatException e) {
                    weight = 0;
                }
            }
            if (id.isEmpty() || weight <= 0 || parts.length > 2) {
                rejected.add(raw);
                continue;
            }
            ids.add(id);
            weights.add(Integer.valueOf(weight));
        }
        return new AnchorPool(ids, weights, rejected);
    }

    public boolean isEmpty() {
        return this.total <= 0;
    }

    /** The id whose weight band contains {@code roll} (in [0, 1)), or null for an empty pool. */
    public String pick(double roll) {
        if (isEmpty()) {
            return null;
        }
        int ticket = (int) Math.floor(roll * this.total);
        if (ticket >= this.total) {
            ticket = this.total - 1;
        }
        for (int i = 0; i < this.cumulative.length; i++) {
            if (ticket < this.cumulative[i]) {
                return this.ids[i];
            }
        }
        return this.ids[this.ids.length - 1];
    }

    public List<String> rejected() {
        return this.rejected;
    }
}
