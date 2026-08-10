package com.spege.commandsuggest.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.function.Predicate;

/**
 * Wszystkie komendy widoczne dla jednego gracza.
 *
 * <p>🚨 Spec mowil {@code CommandTree.prune(...)}, ale {@code CommandTree} to JEDNA komenda,
 * a przycinanie po uprawnieniach usuwa CALE komendy — dlatego metoda jest tutaj. Predykat
 * dostaje nazwe kanoniczna; {@code TreeBuilder} opakowuje w niego
 * {@code ICommand.checkPermission(server, sender)}, dzieki czemu sam mechanizm przycinania jest
 * testowalny bez ani jednej klasy Minecrafta.
 */
public final class CommandIndex {

    public static final CommandIndex EMPTY = new CommandIndex(Collections.<CommandTree>emptyList());

    private final List<CommandTree> commands;
    private final Map<String, CommandTree> byName;

    public CommandIndex(List<CommandTree> commands) {
        this.commands = commands == null || commands.isEmpty()
                ? Collections.<CommandTree>emptyList()
                : Collections.unmodifiableList(new ArrayList<CommandTree>(commands));
        Map<String, CommandTree> m = new HashMap<String, CommandTree>();
        for (CommandTree t : this.commands) {
            m.put(t.getName(), t);
        }
        for (CommandTree t : this.commands) {
            for (String alias : t.getAliases()) {
                // nazwa kanoniczna zawsze wygrywa z cudzym aliasem: pierwsza petla wypelnia
                // WSZYSTKIE nazwy kanoniczne, zanim druga tknie ktorykolwiek alias, wiec kolejnosc
                // komend na liscie nie ma znaczenia dla tego przypadku. Kolizja alias-alias miedzy
                // dwiema komendami rozstrzyga sie kolejnoscia na liscie (kto pierwszy, ten lepszy)
                // — deterministyczne wzgledem wejscia, ale arbitralne tresciowo.
                if (!m.containsKey(alias)) {
                    m.put(alias, t);
                }
            }
        }
        this.byName = m;
    }

    public List<CommandTree> getCommands() {
        return this.commands;
    }

    /** Po nazwie kanonicznej albo po aliasie. {@code null} gdy nie ma. */
    public CommandTree byName(String name) {
        return name == null ? null : this.byName.get(name);
    }

    /**
     * Nazwy i aliasy, posortowane. Wanilia tez podpowiada aliasy — {@code CommandHandler} trzyma
     * je jako osobne klucze tej samej mapy — wiec robimy tak samo.
     */
    public List<String> allNames() {
        return new ArrayList<String>(new TreeSet<String>(this.byName.keySet()));
    }

    /** Nowy indeks bez komend, ktorych nazwa kanoniczna nie przechodzi predykatu. */
    public CommandIndex prune(Predicate<String> allowed) {
        List<CommandTree> kept = new ArrayList<CommandTree>();
        for (CommandTree t : this.commands) {
            if (allowed.test(t.getName())) {
                kept.add(t);
            }
        }
        return new CommandIndex(kept);
    }
}
