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
 *
 * <p>Elementy {@code null} w {@code choices} sa po cichu odrzucane, a nie tylko tolerowane —
 * gdyby przeszly dalej, {@code TreeCodec.encode} wywalilby sie na {@code DataOutputStream.writeUTF(null)}
 * {@code NullPointerException}-em, a nie zadeklarowanym {@code IllegalStateException}.
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
        this.choices = cleanChoices(choices);
        this.min = min;
        this.max = max;
    }

    private static List<String> cleanChoices(List<String> choices) {
        if (choices == null || choices.isEmpty()) {
            return Collections.<String>emptyList();
        }
        List<String> cleaned = new ArrayList<String>(choices.size());
        for (String c : choices) {
            if (c != null) {
                cleaned.add(c);
            }
        }
        return cleaned.isEmpty() ? Collections.<String>emptyList() : Collections.unmodifiableList(cleaned);
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
