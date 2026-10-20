package com.spege.srpwizcore.effectpanel;

import java.util.Map;

/**
 * Finds the description of one effect, trying every convention the pack's mods already use and
 * taking the first that answers.
 *
 * <ol>
 * <li>the pack's descriptions file ({@link DescriptionFile}), keyed by registry name - always wins;</li>
 * <li>Just Enough Effects' {@code <name>.<N>.desc}, N from the amplifier up to 4 - JEE's own lookup,
 *     copied exactly, and shipped by several mods besides JEE;</li>
 * <li>potiondescriptions' {@code description.<name>};</li>
 * <li>for {@code srparasites:*} only, SRP's bestiary entry {@code bestiary.effect.<path>.desc} -
 *     every one of SRP 1.10.9's 33 effects has one.</li>
 * </ol>
 *
 * JEE sits above potiondescriptions on purpose: for vanilla effects both have text, and JEE's gives
 * numbers ("+20% Speed per level.") where potiondescriptions' gives generalities.
 *
 * <p>No Minecraft types: the lang table comes in through {@link LangView}, so this runs under plain
 * JUnit.
 */
public final class DescriptionChain {

    private DescriptionChain() {
    }

    public enum Rule { FILE, JEE, POTION_DESCRIPTIONS, SRP_BESTIARY, NONE }

    /** The client's lang table, as far as this class needs it. */
    public interface LangView {
        boolean has(String key);

        String format(String key);
    }

    public static final class Result {
        public final Rule rule;
        /** The description, or null for {@link Rule#NONE}. */
        public final String text;

        Result(Rule rule, String text) {
            this.rule = rule;
            this.text = text;
        }
    }

    /** JEE never looks past this level; its keys stop at {@code .4.desc}. */
    private static final int JEE_MAX_LEVEL = 4;

    private static final Result NONE = new Result(Rule.NONE, null);

    /**
     * @param registryName    {@code ns:path}; without a colon the namespace is {@code minecraft}
     * @param translationName {@code Potion.getName()}
     * @param amplifier       the effect's amplifier; out-of-range values (a byte 255 arrives as -1)
     *                        are clamped into JEE's 0..4
     */
    public static Result resolve(String registryName, String translationName, int amplifier,
            Map<String, String> file, LangView lang) {
        String fromFile = file.get(registryName);
        if (fromFile != null) {
            return new Result(Rule.FILE, fromFile);
        }

        int start = Math.max(0, Math.min(JEE_MAX_LEVEL, amplifier));
        for (int n = start; n <= JEE_MAX_LEVEL; n++) {
            String key = translationName + "." + n + ".desc";
            if (lang.has(key)) {
                return new Result(Rule.JEE, lang.format(key));
            }
        }

        String pdKey = "description." + translationName;
        if (lang.has(pdKey)) {
            return new Result(Rule.POTION_DESCRIPTIONS, lang.format(pdKey));
        }

        int colon = registryName.indexOf(':');
        String namespace = colon < 0 ? "minecraft" : registryName.substring(0, colon);
        if ("srparasites".equals(namespace)) {
            String bestiaryKey = "bestiary.effect." + registryName.substring(colon + 1) + ".desc";
            if (lang.has(bestiaryKey)) {
                return new Result(Rule.SRP_BESTIARY, lang.format(bestiaryKey));
            }
        }
        return NONE;
    }

    /** The exact line to add to the descriptions file for this effect. */
    public static String fileLine(String registryName) {
        return registryName + "=";
    }
}
