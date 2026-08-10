package com.spege.commandsuggest.core;

/** Jedna pozycja na liscie. {@code type == null} znaczy literal (nazwa komendy albo podkomenda). */
public final class Suggestion {

    private final String text;
    private final ArgType type;

    public Suggestion(String text, ArgType type) {
        this.text = text;
        this.type = type;
    }

    public String getText() {
        return this.text;
    }

    /** {@code null} dla literalu — klient koloruje go inaczej niz wartosc argumentu. */
    public ArgType getType() {
        return this.type;
    }
}
