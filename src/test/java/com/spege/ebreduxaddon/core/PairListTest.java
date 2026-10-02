package com.spege.ebreduxaddon.core;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PairListTest {

    @Test
    void parsesSporeFormat() {
        PairList list = PairList.parse(List.of("spore:infested_dirt|minecraft:dirt", " minecraft:mycelium | minecraft:grass_block "));
        assertEquals("minecraft:dirt", list.pairs().get("spore:infested_dirt"));
        assertEquals("minecraft:grass_block", list.pairs().get("minecraft:mycelium"));
        assertTrue(list.rejected().isEmpty());
    }

    @Test
    void rejectsMalformedEntries() {
        PairList list = PairList.parse(Arrays.asList(
                "no_pipe", "a:b|c:d|e:f", "|minecraft:dirt", "spore:x|", "dirt|minecraft:dirt",
                "a:b:c|minecraft:dirt", null));
        assertTrue(list.pairs().isEmpty());
        assertEquals(6, list.rejected().size());
    }

    @Test
    void firstDuplicateWins() {
        PairList list = PairList.parse(List.of("a:x|b:one", "a:x|b:two"));
        assertEquals("b:one", list.pairs().get("a:x"));
        assertEquals(List.of("a:x|b:two"), list.rejected());
    }

    @Test
    void keepsConfigOrder() {
        PairList list = PairList.parse(List.of("z:z|a:a", "a:a|z:z"));
        assertEquals(List.of("z:z", "a:a"), List.copyOf(list.pairs().keySet()));
    }

    @Test
    void nullListIsEmpty() {
        assertTrue(PairList.parse(null).pairs().isEmpty());
    }
}
