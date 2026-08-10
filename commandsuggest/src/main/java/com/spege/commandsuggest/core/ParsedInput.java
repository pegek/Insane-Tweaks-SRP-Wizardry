package com.spege.commandsuggest.core;

/** Wynik podzialu linii czatu. Niemutowalny. */
public final class ParsedInput {

    /** Zwracane dla wszystkiego, co nie zaczyna sie od ukosnika. */
    public static final ParsedInput NOT_A_COMMAND = new ParsedInput(false, new String[0], 0, 0);

    private final boolean command;
    private final String[] tokens;
    private final int editIndex;
    private final int prefixStart;

    ParsedInput(boolean command, String[] tokens, int editIndex, int prefixStart) {
        this.command = command;
        this.tokens = tokens;
        this.editIndex = editIndex;
        this.prefixStart = prefixStart;
    }

    public boolean isCommand() {
        return this.command;
    }

    /** {@code tokens[0]} to nazwa komendy bez ukosnika. Kopia — wolno modyfikowac. */
    public String[] getTokens() {
        return this.tokens.clone();
    }

    /** Indeks tokenu, na ktorym stoi kursor. */
    public int getEditIndex() {
        return this.editIndex;
    }

    /**
     * Tresc edytowanego tokenu — to ona filtruje liste. Dla {@link #NOT_A_COMMAND} zwraca pusty
     * string, a nie wyjatek: ten obiekt czyta petla rysujaca popup, wolana co klatke, a najczestsze
     * wejscie w czacie to zwykly tekst bez ukosnika. Wyjatek w tym miejscu bylby crashem renderu
     * za to, ze gracz cos napisal.
     */
    public String getPrefix() {
        return this.editIndex < this.tokens.length ? this.tokens[this.editIndex] : "";
    }

    /** Offset w ORYGINALNEJ linii, pod ktorym zaczyna sie edytowany token. Potrzebny przy podmianie. */
    public int getPrefixStart() {
        return this.prefixStart;
    }
}
