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
 *
 * <p>{@link #cache} is {@code volatile} for safe publication, not for mutual exclusion. Racing
 * builders on different threads genuinely compute identical arrays - {@link #build()} is a pure
 * function of {@link ModElements#EXTENDED} and {@link ModElements#ABOMINATION}, both settled long
 * before any caller runs - so a duplicated build is harmless and no lock is needed. What {@code
 * volatile} buys is the happens-before edge between {@code build()}'s element stores and the {@code
 * cache = local} publication (JLS 17.4.5): without it, the JMM permits a reader on another thread to
 * observe a non-null {@code cache} whose slots have not yet become visible, i.e. the classic unsafe-
 * publication hazard double-checked locking needs {@code volatile} to avoid. {@link #values()} then
 * clones on every call to mirror {@code Element.values()}, which clones its own backing array, so
 * this stays a drop-in replacement at every call site. And {@code build()} cannot run before {@link
 * ModElements} has settled: {@code ModElements.init()} is the first statement of the {@code @Mod}
 * constructor, FML runs construction for every mod before any mod reaches {@code preInit}, and every
 * caller of {@link #values()} is a mixin on an EBW code path that runs later still.
 */
public final class NativeElements {

    private static volatile Element[] cache;

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
