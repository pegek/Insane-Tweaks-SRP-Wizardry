package com.spege.commandsuggest.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Jeden argument. Niemutowalny.
 *
 * <p>Spec mowil o {@code Map<String,Object> props}; tutaj sa konkretne pola, bo caly zbior
 * wlasciwosci to trzy pozycje i nie ma po co ich pakowac ani rzutowac. {@code min}/{@code max}
 * sa {@code Double} takze dla {@link ArgType#INT} — jedna para pol obsluguje oba typy liczbowe,
 * a wartosc calkowita miesci sie w double bez straty do 2^53.
 *
 * <p>{@code name} tutaj cicho przyjmuje default {@code "arg"} na {@code null}, w odroznieniu od
 * {@link CommandTree}, ktory na {@code null} rzuca. To nie przeoczenie: nazwa argumentu to
 * <b>etykieta do wyswietlenia</b> — pojawia sie jako {@code <name>} w zsyntetyzowanej linii usage
 * i nigdzie indziej — podczas gdy nazwa komendy to <b>klucz wyszukiwania</b> w mapie
 * {@link CommandIndex} i w sortowanym {@code TreeSet}. Brakujaca etykieta psuje jedna linijke
 * podpowiedzi; brakujacy klucz wywala renderer.
 */
public final class CmdArg {

    private final String name;
    private final ArgType type;
    private final List<String> choices;
    private final Double min;
    private final Double max;

    public CmdArg(String name, ArgType type, List<String> choices, Double min, Double max) {
        this.name = name != null ? name : "arg";
        this.type = type != null ? type : ArgType.UNKNOWN;
        this.choices = choices == null || choices.isEmpty()
                ? Collections.<String>emptyList()
                : Collections.unmodifiableList(new ArrayList<String>(choices));
        this.min = min;
        this.max = max;
    }

    public String getName() {
        return this.name;
    }

    public ArgType getType() {
        return this.type;
    }

    /** Niemodyfikowalna. Pusta dla wszystkiego poza {@link ArgType#CHOICE}. */
    public List<String> getChoices() {
        return this.choices;
    }

    /** {@code null} = brak dolnego ograniczenia. */
    public Double getMin() {
        return this.min;
    }

    /** {@code null} = brak gornego ograniczenia. */
    public Double getMax() {
        return this.max;
    }
}
