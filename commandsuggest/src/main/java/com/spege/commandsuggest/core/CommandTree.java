package com.spege.commandsuggest.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Jedna komenda: nazwa kanoniczna, aliasy i korzen. Niemutowalne.
 *
 * <p>🚨 {@code name} musi byc nie-{@code null}, w odroznieniu od {@code root} (ktory dostaje
 * ciche zastepstwo). Powod: {@code name} jest kluczem w mapie {@link CommandIndex} i trafia do
 * {@code TreeSet} w {@link CommandIndex#allNames()} — {@code TreeSet} porownuje elementy przez
 * naturalny porzadek i rzuca {@code NullPointerException} przy probie wstawienia {@code null},
 * czyli crash spadlby dopiero przy renderowaniu popupu, daleko od miejsca, gdzie dane byly zle.
 * Lepiej zawalic sie tutaj, w konstruktorze, z jasnym komunikatem.
 */
public final class CommandTree {

    private final String name;
    private final List<String> aliases;
    private final CmdNode root;

    public CommandTree(String name, List<String> aliases, CmdNode root) {
        if (name == null) {
            throw new IllegalArgumentException("CommandTree.name nie moze byc null");
        }
        this.name = name;
        this.aliases = aliases == null || aliases.isEmpty()
                ? Collections.<String>emptyList()
                : Collections.unmodifiableList(new ArrayList<String>(aliases));
        this.root = root != null ? root : new CmdNode(null, null, null, true, null);
    }

    public String getName() {
        return this.name;
    }

    public List<String> getAliases() {
        return this.aliases;
    }

    public CmdNode getRoot() {
        return this.root;
    }
}
