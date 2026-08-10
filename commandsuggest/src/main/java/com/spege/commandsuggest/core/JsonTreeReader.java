package com.spege.commandsuggest.core;

import java.util.ArrayList;
import java.util.List;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;

/**
 * Czyta opis komendy z JSON-a. Format jest w specu, sekcja 5.
 *
 * <p>Czytane recznie z {@link JsonObject}, a nie przez wiazanie refleksyjne Gsona — dzieki temu
 * komunikat bledu mowi, ktorego pola brakuje, a model moze zostac niemutowalny z polami
 * {@code final}, z czym wiazanie refleksyjne sie kloci.
 *
 * <p>Nieznany identyfikator typu daje {@link ArgType#UNKNOWN} zamiast wyjatku: te pliki pisza
 * ludzie recznie, a literowka w jednym argumencie nie ma prawa wywalic calego opisu.
 */
public final class JsonTreeReader {

    private JsonTreeReader() {
    }

    /**
     * Regula formatu: brakujacy WYMAGANY klucz odrzuca caly plik z komunikatem (tak {@code command}
     * przy korzeniu, jak {@code lit} przy kazdym wpisie w {@code sub}); nierozpoznana WARTOSC
     * degraduje sie w milczeniu do defaultu (nieznany {@code type} -> {@link ArgType#UNKNOWN},
     * niepoprawny {@code exec} -> {@code false}). Cicha porazka jest dla pliku edytowanego recznie
     * gorsza niz glosna — patrz uzasadnienie w {@link #readNode}.
     *
     * @throws IllegalArgumentException gdy to nie jest obiekt JSON, brak pola {@code command},
     *         albo ktorykolwiek wpis w {@code sub} nie ma pola {@code lit}
     */
    public static CommandTree read(String json) {
        JsonObject o;
        try {
            JsonElement el = new JsonParser().parse(json);
            if (!el.isJsonObject()) {
                throw new IllegalArgumentException("opis komendy musi byc obiektem JSON");
            }
            o = el.getAsJsonObject();
        } catch (JsonSyntaxException e) {
            throw new IllegalArgumentException("niepoprawny JSON: " + e.getMessage(), e);
        }

        if (!o.has("command") || !o.get("command").isJsonPrimitive()) {
            throw new IllegalArgumentException("opis komendy nie ma pola 'command'");
        }

        // Reszta parsowania siega po Gsonowe getAsString/getAsDouble/getAsJsonObject, ktore przy
        // zlym typie w JSON-ie rzucaja WLASNE niesprawdzane wyjatki (UnsupportedOperationException,
        // IllegalStateException) — nie IllegalArgumentException, ktory jest jedynym udokumentowanym
        // kontraktem tej metody. Lapiemy je tutaj i przepakowujemy, zeby kontrakt sie zgadzal z
        // rzeczywistoscia; per zasade z gory pliku, taka usterka strukturalna (np. element listy
        // 'args' ktory nie jest wcale obiektem) i tak nie ma czego ratowac w calym pliku.
        try {
            String name = o.get("command").getAsString();

            List<String> aliases = new ArrayList<String>();
            if (o.has("aliases") && o.get("aliases").isJsonArray()) {
                for (JsonElement a : o.getAsJsonArray("aliases")) {
                    aliases.add(a.getAsString());
                }
            }
            return new CommandTree(name, aliases, readNode(o, null));
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("zla struktura opisu komendy '" + name(o) + "': " + e, e);
        }
    }

    private static String name(JsonObject o) {
        // W momencie wywolania 'command' juz przeszedl walidacje wyzej, wiec to bezpieczne.
        return o.get("command").getAsString();
    }

    /**
     * {@code literal == null} tylko dla korzenia.
     *
     * <p>Brak {@code lit} we wpisie {@code sub} rzuca, zamiast po cichu pominac wezel albo
     * zmyslic token "?". 'lit' to klucz routingu (to, co gracz faktycznie wpisuje i po czym
     * wezel jest wybierany) — analogicznie do {@code CommandTree.name}, a nie etykieta jak
     * {@code CmdArg.name}. Pominiecie wezla byloby niewidoczne: komenda ladowalaby sie normalnie
     * i po prostu nigdy nie podpowiadalaby tej jednej podkomendy, bez ani jednej linii w zadnym
     * logu — autor musialby zauwazyc roznice miedzy swoim JSON-em a popupem sam. Rzucenie tutaj
     * gubi podpowiedzi tej JEDNEJ komendy na sesje, ale daje {@code DescriptorLoader} (zadanie 10)
     * cos do zalogowania z nazwa pliku, co author moze od razu naprawic.
     */
    private static CmdNode readNode(JsonObject o, String literal) {
        List<CmdArg> args = new ArrayList<CmdArg>();
        if (o.has("args") && o.get("args").isJsonArray()) {
            for (JsonElement e : o.getAsJsonArray("args")) {
                args.add(readArg(e.getAsJsonObject()));
            }
        }
        List<CmdNode> sub = new ArrayList<CmdNode>();
        if (o.has("sub") && o.get("sub").isJsonArray()) {
            for (JsonElement e : o.getAsJsonArray("sub")) {
                JsonObject so = e.getAsJsonObject();
                if (!so.has("lit") || !so.get("lit").isJsonPrimitive()) {
                    throw new IllegalArgumentException("wpis w 'sub' pod '"
                            + (literal != null ? literal : "korzeniem") + "' nie ma pola 'lit'");
                }
                String lit = so.get("lit").getAsString();
                sub.add(readNode(so, lit));
            }
        }
        boolean exec = o.has("exec") && isTrue(o.get("exec"));
        String usage = o.has("usage") ? o.get("usage").getAsString() : null;
        return new CmdNode(literal, args, sub, exec, usage);
    }

    /**
     * {@code JsonPrimitive.getAsBoolean()} na wartosci, ktora nie jest logiczna, wywoluje
     * {@code Boolean.parseBoolean} na jej reprezentacji tekstowej — co dla {@code "yes"} czy
     * {@code "tak"} cicho zwraca {@code false} zamiast sygnalizowac, ze to nie jest to, co autor
     * mial na mysli. {@code exec} to znacznik decyzji jak nieznany typ argumentu, nie klucz —
     * zla wartosc dostaje wiec domyslne {@code false} tak samo jak jej brak, ale bez ukrytej
     * koercji stringow.
     */
    private static boolean isTrue(JsonElement e) {
        return e.isJsonPrimitive() && e.getAsJsonPrimitive().isBoolean() && e.getAsBoolean();
    }

    private static CmdArg readArg(JsonObject o) {
        String name = o.has("name") ? o.get("name").getAsString() : "arg";
        ArgType type = ArgType.byId(o.has("type") ? o.get("type").getAsString() : null);
        List<String> choices = null;
        if (o.has("choices") && o.get("choices").isJsonArray()) {
            choices = new ArrayList<String>();
            JsonArray arr = o.getAsJsonArray("choices");
            for (JsonElement e : arr) {
                choices.add(e.getAsString());
            }
        }
        Double min = o.has("min") ? Double.valueOf(o.get("min").getAsDouble()) : null;
        Double max = o.has("max") ? Double.valueOf(o.get("max").getAsDouble()) : null;
        return new CmdArg(name, type, choices, min, max);
    }
}
