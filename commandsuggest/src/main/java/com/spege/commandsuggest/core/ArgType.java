package com.spege.commandsuggest.core;

import java.util.HashMap;
import java.util.Map;

/**
 * Typ argumentu. Wartosc enuma rozstrzyga <b>skad biora sie podpowiedzi</b> i to jest caly zysk
 * z typowania: {@link #isServerResolved()} == false znaczy "klient wie sam", a wiec zadnego
 * {@code CPacketTabComplete}, a wiec zadnego obcinania listy przez VintageFix do 100 pozycji
 * z linijka smiecia na czacie.
 *
 * <p>{@code id} jest kluczem w plikach JSON — jest danymi na dysku, nie nazwa w kodzie.
 * Zmiana ktoregokolwiek uniewaznia opisy, ktore ludzie maja w {@code config/}.
 */
public enum ArgType {

    /** Jedno slowo, tresc nieznana — pytamy serwer. */
    WORD("word", true),
    /** Wszystko do konca linii — pytamy serwer. */
    GREEDY("greedy", true),
    /** Komenda nieopisana albo typ z przyszlej wersji formatu. */
    UNKNOWN("unknown", true),

    INT("int", false),
    FLOAT("float", false),
    BOOL("bool", false),
    /** Zamknieta lista wartosci, podana wprost w opisie. */
    CHOICE("choice", false),
    PLAYER("player", false),
    ENTITY("entity", false),
    BLOCKPOS("blockpos", false),
    ITEM("item", false),
    BLOCK("block", false),
    DIMENSION("dimension", false);

    private static final Map<String, ArgType> BY_ID;

    static {
        Map<String, ArgType> m = new HashMap<String, ArgType>();
        for (ArgType t : values()) {
            m.put(t.id, t);
        }
        BY_ID = m;
    }

    private final String id;
    private final boolean serverResolved;

    ArgType(String id, boolean serverResolved) {
        this.id = id;
        this.serverResolved = serverResolved;
    }

    public String getId() {
        return this.id;
    }

    /** {@code true} = wartosci trzeba wyprosic waniliowym tab-complete od serwera. */
    public boolean isServerResolved() {
        return this.serverResolved;
    }

    /**
     * Nieznany identyfikator daje {@link #UNKNOWN}, nigdy wyjatku — opisy pisza ludzie.
     *
     * <p>Uwaga: wlasny id {@link #UNKNOWN} to string {@code "unknown"}, wiec
     * {@code byId("unknown")} i {@code byId("literowka")} zwracaja to samo — zeby odroznic
     * literowke od jawnego "unknown" w JSON-ie, wywolujacy musi sam porownac surowy string
     * z {@code ArgType.UNKNOWN.getId()} przed wywolaniem tej metody.
     */
    public static ArgType byId(String id) {
        if (id == null) {
            return UNKNOWN;
        }
        ArgType t = BY_ID.get(id);
        return t != null ? t : UNKNOWN;
    }
}
