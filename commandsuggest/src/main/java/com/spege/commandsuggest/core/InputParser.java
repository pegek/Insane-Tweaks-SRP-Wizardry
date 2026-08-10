package com.spege.commandsuggest.core;

/**
 * Dzieli linie czatu tak samo, jak zrobi to serwer.
 *
 * <p>🚨 Wzorzec jest z {@code MinecraftServer.getTabCompletions}: obetnij wiodacy {@code /},
 * potem {@code split(" ", -1)}. Limit {@code -1} jest load-bearing — bez niego {@code "gamerule "}
 * daje jeden token zamiast dwoch i podpowiadalibysmy nazwe komendy zamiast jej pierwszego
 * argumentu. Zadnych cudzyslowow: wanilia ich nie zna i rozjechalibysmy sie z tym, co komenda
 * naprawde dostanie.
 */
public final class InputParser {

    private InputParser() {
    }

    /**
     * @param raw       cala tresc pola czatu
     * @param cursorPos pozycja kursora; tekst za kursorem jest ignorowany, tak jak w
     *                  {@code TabCompleter.complete()}. Wartosc wieksza niz dlugosc {@code raw}
     *                  jest przycinana zamiast wywalac wyjatek — popup liczy z zapamietanej pary
     *                  tekst/kursor, a wklejenie albo cofniecie historii moze skrocic pole miedzy
     *                  dwoma odczytami.
     */
    public static ParsedInput parse(String raw, int cursorPos) {
        if (raw == null || cursorPos <= 0) {
            return ParsedInput.NOT_A_COMMAND;
        }
        if (cursorPos > raw.length()) {
            cursorPos = raw.length();
        }
        String upToCursor = raw.substring(0, cursorPos);
        if (!upToCursor.startsWith("/")) {
            return ParsedInput.NOT_A_COMMAND;
        }

        String body = upToCursor.substring(1);
        String[] tokens = body.split(" ", -1);
        int editIndex = tokens.length - 1;

        int prefixStart = 1; // za ukosnikiem
        for (int i = 0; i < editIndex; i++) {
            prefixStart += tokens[i].length() + 1; // +1 za spacje
        }
        return new ParsedInput(true, tokens, editIndex, prefixStart);
    }
}
