package com.spege.commandsuggest.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Wynik jednego przeliczenia podpowiedzi. Niemutowalny. */
public final class Suggestions {

    public static final Suggestions EMPTY = new Suggestions(null, null, "", 0, null);

    private final List<Suggestion> items;
    private final ArgType argType;
    private final String prefix;
    private final int replaceStart;
    private final String usage;

    public Suggestions(List<Suggestion> items, ArgType argType, String prefix, int replaceStart,
            String usage) {
        this.items = items == null || items.isEmpty()
                ? Collections.<Suggestion>emptyList()
                : Collections.unmodifiableList(new ArrayList<Suggestion>(items));
        this.argType = argType;
        this.prefix = prefix != null ? prefix : "";
        this.replaceStart = replaceStart;
        this.usage = usage;
    }

    /** Juz rozwiazane pozycje: nazwy komend, literaly, {@code choice}, {@code bool}. */
    public List<Suggestion> getItems() {
        return this.items;
    }

    /**
     * Typ edytowanego argumentu albo {@code null}, gdy edytowany jest literal lub nie ma czego
     * podpowiadac. Klient uzywa tego, zeby dolozyc wartosci z rejestrow — {@code core} ich nie zna.
     */
    public ArgType getArgType() {
        return this.argType;
    }

    /** Tresc edytowanego tokenu. */
    public String getPrefix() {
        return this.prefix;
    }

    /** Offset w linii czatu, od ktorego podmieniamy tekst po zatwierdzeniu. */
    public int getReplaceStart() {
        return this.replaceStart;
    }

    /** Linia pod lista albo {@code null}. */
    public String getUsage() {
        return this.usage;
    }

    /** Czy klient ma wyslac waniliowy {@code CPacketTabComplete}. */
    public boolean needsServerQuery() {
        return this.argType != null && this.argType.isServerResolved();
    }
}
