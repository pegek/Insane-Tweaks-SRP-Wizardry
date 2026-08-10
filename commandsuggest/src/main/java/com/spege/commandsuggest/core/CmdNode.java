package com.spege.commandsuggest.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Wezel drzewa: jeden literal, po nim uporzadkowana sekwencja argumentow, a po niej rozgalezienie
 * na kolejne literaly. Niemutowalny. Korzen ma {@code literal == null} i jest jedynym takim
 * wezlem w drzewie.
 */
public final class CmdNode {

    private final String literal;
    private final List<CmdArg> args;
    private final List<CmdNode> sub;
    private final boolean executable;
    private final String usage;

    public CmdNode(String literal, List<CmdArg> args, List<CmdNode> sub, boolean executable, String usage) {
        this.literal = literal;
        this.args = args == null || args.isEmpty()
                ? Collections.<CmdArg>emptyList()
                : Collections.unmodifiableList(new ArrayList<CmdArg>(args));
        this.sub = sub == null || sub.isEmpty()
                ? Collections.<CmdNode>emptyList()
                : Collections.unmodifiableList(new ArrayList<CmdNode>(sub));
        this.executable = executable;
        this.usage = usage;
    }

    /** {@code null} tylko w korzeniu. */
    public String getLiteral() {
        return this.literal;
    }

    public List<CmdArg> getArgs() {
        return this.args;
    }

    public List<CmdNode> getSub() {
        return this.sub;
    }

    /** Czy sciezka moze sie tu skonczyc. Dzis informacyjne; v2 uzyje tego do walidacji skladni. */
    public boolean isExecutable() {
        return this.executable;
    }

    /** Gotowa linia usage albo {@code null} — wtedy {@code SuggestionEngine} ja syntezuje. */
    public String getUsage() {
        return this.usage;
    }
}
