package com.spege.insanetweaks.integration.patchouli;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class CodexTextTest {

    @Test
    public void colourCodeIsRemoved() {
        assertEquals("Sentient Wand", CodexText.plain("§eSentient Wand"));
    }

    @Test
    public void everyCodeIsRemoved_includingStylesAndUpperCase() {
        assertEquals("Magic Nucleus", CodexText.plain("§d§lMagic §RNucleus§r"));
    }

    @Test
    public void plainTextIsUnchanged() {
        assertEquals("Golden Book", CodexText.plain("Golden Book"));
    }

    @Test
    public void nullBecomesEmpty() {
        assertEquals("", CodexText.plain(null));
    }
}
