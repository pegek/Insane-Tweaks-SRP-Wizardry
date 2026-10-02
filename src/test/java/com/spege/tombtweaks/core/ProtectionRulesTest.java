package com.spege.tombtweaks.core;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProtectionRulesTest {

    /** Atrapa stacka - core nie zna ItemStacka i nie ma o czym wiedziec. */
    private static final class FakeStack implements StackView {
        private final String id;
        private final Set<String> enchants;
        private final Map<String, String> nbt;

        FakeStack(String id) {
            this(id, Collections.<String>emptySet(), Collections.<String, String>emptyMap());
        }

        FakeStack(String id, Set<String> enchants, Map<String, String> nbt) {
            this.id = id;
            this.enchants = enchants;
            this.nbt = nbt;
        }

        public String itemId() { return id; }
        public Set<String> enchantmentIds() { return enchants; }
        public String nbtString(String key) { return nbt.get(key); }
    }

    private static List<String> none() {
        return new ArrayList<String>();
    }

    @Test
    void pustaKonfiguracjaNieChroniNiczego() {
        ProtectionRules rules = ProtectionRules.of(none(), none(), none(), none());
        assertFalse(rules.isProtected(new FakeStack("minecraft:diamond_sword")));
    }

    @Test
    void pelnaNazwaChroniTylkoSiebie() {
        ProtectionRules rules = ProtectionRules.of(
                Arrays.asList("minecraft:diamond_sword"), none(), none(), none());

        assertTrue(rules.isProtected(new FakeStack("minecraft:diamond_sword")));
        assertFalse(rules.isProtected(new FakeStack("minecraft:diamond_shovel")));
    }

    @Test
    void prefiksLapieCalaRodzine() {
        ProtectionRules rules = ProtectionRules.of(
                none(), Arrays.asList("mymod:relic_"), none(), none());

        assertTrue(rules.isProtected(new FakeStack("mymod:relic_blade")));
        assertTrue(rules.isProtected(new FakeStack("mymod:relic_crown")));
        assertFalse(rules.isProtected(new FakeStack("mymod:common_blade")));
    }

    @Test
    void enchantChroniNiezaleznieOdItemu() {
        ProtectionRules rules = ProtectionRules.of(
                none(), none(), Arrays.asList("mymod:soulbound"), none());

        Set<String> enchants = new HashSet<String>(Arrays.asList("mymod:soulbound"));
        assertTrue(rules.isProtected(
                new FakeStack("minecraft:stick", enchants, Collections.<String, String>emptyMap())));
        assertFalse(rules.isProtected(new FakeStack("minecraft:stick")));
    }

    @Test
    void matcherNbtPorownujeWartosc() {
        ProtectionRules rules = ProtectionRules.of(
                none(), none(), none(), Arrays.asList("properties=ashen_legacy"));

        Map<String, String> matching = new HashMap<String, String>();
        matching.put("properties", "ashen_legacy");
        Map<String, String> different = new HashMap<String, String>();
        different.put("properties", "something_else");

        assertTrue(rules.isProtected(
                new FakeStack("minecraft:stick", Collections.<String>emptySet(), matching)));
        assertFalse(rules.isProtected(
                new FakeStack("minecraft:stick", Collections.<String>emptySet(), different)));
        assertFalse(rules.isProtected(new FakeStack("minecraft:stick")));
    }

    @Test
    void popsutyWpisNbtJestIgnorowanyANiefatalny() {
        ProtectionRules rules = ProtectionRules.of(
                none(), none(), none(),
                Arrays.asList("bez_rownosci", "=bez_klucza", "klucz=", "dobry=wpis"));

        Map<String, String> matching = new HashMap<String, String>();
        matching.put("dobry", "wpis");

        assertTrue(rules.isProtected(
                new FakeStack("minecraft:stick", Collections.<String>emptySet(), matching)));
        assertFalse(rules.isProtected(new FakeStack("minecraft:stick")));
    }

    @Test
    void bialeZnakiWKonfiguracjiSaWybaczane() {
        ProtectionRules rules = ProtectionRules.of(
                Arrays.asList("  minecraft:stick  ", "", "   "), none(), none(), none());

        assertTrue(rules.isProtected(new FakeStack("minecraft:stick")));
    }
}
