package com.spege.insanetweaks.integration.patchouli;

import java.util.regex.Pattern;

/**
 * Text clean-up for the Codex. Free of Minecraft and Patchouli types so it can be unit-tested.
 */
public final class CodexText {

    /** A section sign and the one character after it: every vanilla colour and style code. */
    private static final Pattern FORMATTING = Pattern.compile("§.");

    private CodexText() {
    }

    /**
     * Strips formatting codes. Our item names carry them for tooltips ({@code §eSentient Wand}); on a
     * Codex page they would print light yellow on the parchment background and become unreadable,
     * so the page uses the book's own text colour instead.
     */
    public static String plain(String text) {
        return text == null ? "" : FORMATTING.matcher(text).replaceAll("");
    }
}
