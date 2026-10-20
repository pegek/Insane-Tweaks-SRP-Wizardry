package com.spege.srpwizcore.effectpanel;

import static com.spege.srpwizcore.effectpanel.DescriptionChain.resolve;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.junit.Test;

import com.spege.srpwizcore.effectpanel.DescriptionChain.LangView;
import com.spege.srpwizcore.effectpanel.DescriptionChain.Result;
import com.spege.srpwizcore.effectpanel.DescriptionChain.Rule;

public class DescriptionChainTest {

    private static Map<String, String> map(String... kv) {
        Map<String, String> m = new HashMap<>();
        for (int i = 0; i < kv.length; i += 2) {
            m.put(kv[i], kv[i + 1]);
        }
        return m;
    }

    private static Map<String, String> empty() {
        return Collections.emptyMap();
    }

    private static LangView lang(String... kv) {
        final Map<String, String> m = map(kv);
        return new LangView() {
            @Override
            public boolean has(String key) {
                return m.containsKey(key);
            }

            @Override
            public String format(String key) {
                return m.get(key);
            }
        };
    }

    @Test
    public void fileWinsOverEverything() {
        Result r = resolve("minecraft:speed", "effect.moveSpeed", 0, map("minecraft:speed", "File text"),
                lang("effect.moveSpeed.4.desc", "jee", "description.effect.moveSpeed", "pd"));
        assertEquals(Rule.FILE, r.rule);
        assertEquals("File text", r.text);
    }

    @Test
    public void jeeWalksFromAmplifierUpToFour() {
        LangView l = lang("effect.x.1.desc", "one", "effect.x.4.desc", "any");
        assertEquals("one", resolve("m:x", "effect.x", 0, empty(), l).text);
        assertEquals("one", resolve("m:x", "effect.x", 1, empty(), l).text);
        assertEquals("any", resolve("m:x", "effect.x", 2, empty(), l).text);
        assertEquals("any", resolve("m:x", "effect.x", 7, empty(), l).text);   // start clamps to 4
        assertEquals("one", resolve("m:x", "effect.x", -1, empty(), l).text);  // byte amplifier 255 -> -1, start clamps to 0
        assertEquals(Rule.JEE, resolve("m:x", "effect.x", 0, empty(), l).rule);
    }

    @Test
    public void jeeBeatsPotionDescriptions() {
        assertEquals(Rule.JEE, resolve("minecraft:speed", "effect.moveSpeed", 0, empty(),
                lang("effect.moveSpeed.4.desc", "jee", "description.effect.moveSpeed", "pd")).rule);
    }

    @Test
    public void potionDescriptionsFormat() {
        Result r = resolve("minecraft:speed", "effect.moveSpeed", 0, empty(),
                lang("description.effect.moveSpeed", "pd"));
        assertEquals(Rule.POTION_DESCRIPTIONS, r.rule);
        assertEquals("pd", r.text);
    }

    @Test
    public void srpBestiaryOnlyForSrparasitesNamespace() {
        LangView l = lang("bestiary.effect.bleed.desc", "B");
        Result r = resolve("srparasites:bleed", "mob_effect.srparasites:bleed", 0, empty(), l);
        assertEquals(Rule.SRP_BESTIARY, r.rule);
        assertEquals("B", r.text);
        assertEquals(Rule.NONE, resolve("othermod:bleed", "potion.othermod.bleed", 0, empty(), l).rule);
    }

    @Test
    public void noneWhenNothingMatches() {
        Result r = resolve("m:x", "effect.x", 0, empty(), lang());
        assertEquals(Rule.NONE, r.rule);
        assertNull(r.text);
    }

    @Test
    public void fileLineIsTheExactLineToAdd() {
        assertEquals("minecraft:speed=", DescriptionChain.fileLine("minecraft:speed"));
    }
}
