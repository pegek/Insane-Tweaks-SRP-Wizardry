package com.spege.srpwizcore.effectpanel;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.Test;

public class DescriptionFileTest {

    @Test
    public void parsesEntriesCommentsAndBlanks() {
        DescriptionFile.Parsed p = DescriptionFile.parse(Arrays.asList(
                "# comment", "", "minecraft:speed=Walk faster.", "srparasites:bleed=",
                "broken line", "=no key", "a:b=x=y", " minecraft:speed = Faster still. "));
        assertEquals("Faster still.", p.entries.get("minecraft:speed"));   // last occurrence wins, trimmed
        assertEquals("x=y", p.entries.get("a:b"));                         // split on the FIRST '='
        assertFalse(p.entries.containsKey("srparasites:bleed"));           // empty text = missing
        assertTrue(p.presentKeys.containsAll(Arrays.asList("minecraft:speed", "srparasites:bleed", "a:b")));
        assertEquals(Arrays.asList(5, 6), p.malformedLines);
    }

    @Test
    public void stripsUtf8Bom() {
        assertEquals("Lucky.", DescriptionFile.parse(
                Collections.singletonList("﻿minecraft:luck=Lucky.")).entries.get("minecraft:luck"));
    }

    @Test
    public void headerIsOnlyComments() {
        for (String line : DescriptionFile.header()) {
            assertTrue(line, line.startsWith("#"));
        }
        assertTrue(DescriptionFile.parse(DescriptionFile.header()).presentKeys.isEmpty());
    }

    @Test
    public void appendsOnlyKeysWithNoLineYet() {
        Set<String> present = new HashSet<>(Arrays.asList("minecraft:speed", "srparasites:bleed"));
        List<String> out = DescriptionFile.linesToAppend(present, Arrays.asList(
                new DescriptionFile.Missing("srparasites:bleed", "Bleeding", "Scape and Run: Parasites"),
                new DescriptionFile.Missing("minecraft:luck", "Luck", "Minecraft")), "L");
        assertEquals(Arrays.asList("", "# --- L ---", "# Luck — Minecraft", "minecraft:luck="), out);
    }

    @Test
    public void appendsNothingWhenNothingIsNew() {
        assertTrue(DescriptionFile.linesToAppend(Collections.singleton("minecraft:luck"),
                Collections.singletonList(new DescriptionFile.Missing("minecraft:luck", "Luck", "Minecraft")),
                "L").isEmpty());
    }
}
