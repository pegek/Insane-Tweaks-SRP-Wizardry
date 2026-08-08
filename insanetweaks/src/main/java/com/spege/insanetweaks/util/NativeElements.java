package com.spege.insanetweaks.util;

import java.util.ArrayList;
import java.util.List;

import com.spege.insanetweaks.init.ModElements;

import electroblob.wizardry.constants.Element;

/**
 * Wizardry's elements minus Abomination.
 *
 * <p>Every mixin that narrows one of EBW's own random-element pickers calls this and nothing else,
 * so widening the element's reach later - letting it generate wizards, shrines or crystals - is a
 * matter of deleting redirects, not of rewriting call sites.
 *
 * <p>Returns a fresh copy per call, matching {@code Element.values()}, which clones its backing
 * array too. Callers index and iterate the result and must not see each other's mutations.
 */
public final class NativeElements {

    private static Element[] cache;

    private NativeElements() {
    }

    public static Element[] values() {
        Element[] local = cache;
        if (local == null) {
            local = build();
            cache = local;
        }
        return local.clone();
    }

    private static Element[] build() {
        Element[] all = Element.values();
        if (!ModElements.EXTENDED) {
            return all;
        }

        List<Element> kept = new ArrayList<Element>(all.length);
        for (Element element : all) {
            if (element != ModElements.ABOMINATION) {
                kept.add(element);
            }
        }
        return kept.toArray(new Element[0]);
    }
}
