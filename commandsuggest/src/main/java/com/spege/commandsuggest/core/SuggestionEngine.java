package com.spege.commandsuggest.core;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Spacer po drzewie: z podzielonej linii robi liste podpowiedzi.
 *
 * <p>Rozwiazuje wylacznie to, co siedzi w danych — nazwy komend, literaly {@code sub},
 * {@code choice} i {@code bool}. Dla wszystkiego innego oddaje TYP argumentu i zostawia
 * rozwiniecie warstwie klienta (rejestry) albo serwerowi (waniliowy tab-complete).
 *
 * <p>🚨 Nieznany literal NIE pyta serwera. Skoro mamy opis tej komendy, to wiemy, ze nie ma tam
 * czego podpowiadac — strzal w serwer dolozylby tylko obcinanie listy przez VintageFix i linijke
 * smiecia na czacie. Serwera pytamy tylko wtedy, gdy naprawde nie wiemy: komenda spoza drzewa
 * albo argument typu {@code word}/{@code greedy}/{@code unknown}.
 *
 * <p>Petla "idz po literalach" (patrz {@code while (true)} nizej) trzyma niezmiennik
 * {@code 1 <= i <= editIndex}: kazdy obrot, ktory nie zwraca, koncowo zwieksza {@code i} przynajmniej
 * o jeden (przez {@code i++} po dopasowaniu dziecka), a wiec {@code i} scisle rosnie w strone
 * skonczonej i niemalejacej wartosci {@code editIndex} — stad petla zawsze sie konczy, w co
 * najwyzej {@code editIndex} obrotach, bez wzgledu na ksztalt drzewa. Cykl w drzewie (model go
 * nie zabrania, tylko {@code JsonTreeReader} go nie produkuje) nie zapetli spaceru: kazdy powrot
 * do tego samego wezla i tak kosztuje co najmniej jeden token linii, ktorych jest tyle samo co
 * zawsze.
 */
public final class SuggestionEngine {

    private static final List<String> BOOLEANY =
            Collections.unmodifiableList(Arrays.asList("true", "false"));

    private SuggestionEngine() {
    }

    public static Suggestions suggest(CommandIndex index, ParsedInput in) {
        if (index == null || in == null || !in.isCommand()) {
            return Suggestions.EMPTY;
        }
        String[] tokens = in.getTokens();
        String prefix = in.getPrefix();
        int start = in.getPrefixStart();
        int editIndex = in.getEditIndex();

        if (editIndex == 0) {
            List<Suggestion> items = new ArrayList<Suggestion>();
            for (String name : index.allNames()) {
                if (matches(name, prefix)) {
                    items.add(new Suggestion(name, null));
                }
            }
            return new Suggestions(items, null, prefix, start, null);
        }

        CommandTree tree = index.byName(tokens[0]);
        if (tree == null) {
            // komendy nie znamy - jedyne, co zostaje, to zapytac serwer
            return new Suggestions(null, ArgType.UNKNOWN, prefix, start, null);
        }

        StringBuilder path = new StringBuilder("/").append(tree.getName());
        CmdNode node = tree.getRoot();
        int i = 1;

        while (true) {
            for (CmdArg arg : node.getArgs()) {
                // GREEDY sprawdzane PRZED i == editIndex celowo: zjada reszte linii, wiec zwraca
                // sie na pierwszy kontakt, bez wzgledu na to, ktory token akurat edytujemy.
                // Zalozenie: greedy jest ostatnim argumentem wezla (tak jak w prawdziwej gramatyce
                // komend - nic nie moze isc po "reszcie linii"). Model tego nie wymusza; jesli opis
                // umiesci argument PO greedy, ten argument jest martwy kod — nigdy nie zostanie
                // zwrocony, bo petla konczy sie tutaj przy kazdym przejsciu przez wezel.
                if (arg.getType() == ArgType.GREEDY || i == editIndex) {
                    return forArg(arg, prefix, start, usageOf(node, path));
                }
                i++;
            }
            if (node.getSub().isEmpty()) {
                return new Suggestions(null, null, prefix, start, usageOf(node, path));
            }
            if (i == editIndex) {
                List<Suggestion> items = new ArrayList<Suggestion>();
                for (CmdNode s : node.getSub()) {
                    if (matches(s.getLiteral(), prefix)) {
                        items.add(new Suggestion(s.getLiteral(), null));
                    }
                }
                return new Suggestions(items, null, prefix, start, usageOf(node, path));
            }
            CmdNode child = findChild(node.getSub(), tokens[i]);
            if (child == null) {
                return new Suggestions(null, null, prefix, start, usageOf(node, path));
            }
            path.append(' ').append(child.getLiteral());
            node = child;
            i++;
        }
    }

    private static Suggestions forArg(CmdArg arg, String prefix, int start, String usage) {
        List<String> zrodlo = null;
        if (arg.getType() == ArgType.CHOICE) {
            zrodlo = arg.getChoices();
        } else if (arg.getType() == ArgType.BOOL) {
            zrodlo = BOOLEANY;
        }
        if (zrodlo == null) {
            return new Suggestions(null, arg.getType(), prefix, start, usage);
        }
        List<Suggestion> items = new ArrayList<Suggestion>();
        for (String v : zrodlo) {
            if (matches(v, prefix)) {
                items.add(new Suggestion(v, arg.getType()));
            }
        }
        return new Suggestions(items, arg.getType(), prefix, start, usage);
    }

    private static CmdNode findChild(List<CmdNode> sub, String literal) {
        for (CmdNode s : sub) {
            if (s.getLiteral() != null && s.getLiteral().equalsIgnoreCase(literal)) {
                return s;
            }
        }
        return null;
    }

    /** Gotowy usage z opisu, a jak go nie ma — sklejony ze sciezki literalow i nazw argumentow. */
    private static String usageOf(CmdNode node, StringBuilder path) {
        if (node.getUsage() != null) {
            return node.getUsage();
        }
        if (node.getArgs().isEmpty()) {
            return path.toString();
        }
        StringBuilder sb = new StringBuilder(path);
        for (CmdArg a : node.getArgs()) {
            sb.append(" <").append(a.getName()).append('>');
        }
        return sb.toString();
    }

    private static boolean matches(String candidate, String prefix) {
        if (candidate == null) {
            return false;
        }
        if (prefix.isEmpty()) {
            return true;
        }
        return candidate.regionMatches(true, 0, prefix, 0, prefix.length());
    }
}
