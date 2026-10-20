package com.spege.srpwizcore.effectpanel;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The pack's own effect descriptions: {@code config/srpwizcore/effect_descriptions.lang}, one
 * {@code <registry name>=<text>} per line.
 *
 * <p>Deliberately free of Minecraft types so it runs under plain JUnit. Reading the file from disk
 * and reacting to F3+T is {@link EffectDescriptions}' job; this class only turns lines into entries
 * and decides what {@code /effectdesc missing} appends.
 *
 * <p>The file is hand-edited, so parsing never throws: a line it cannot use is reported by number
 * and skipped, and the same key appearing twice (a paste) is resolved by the last occurrence.
 */
public final class DescriptionFile {

    private DescriptionFile() {
    }

    /** What a file contained. */
    public static final class Parsed {
        /** Registry name to text, for non-empty texts only, in file order. */
        public final Map<String, String> entries;
        /** Every key that has a line, including the ones whose text is still empty. */
        public final Set<String> presentKeys;
        /** 1-based numbers of the lines that were neither a comment, blank, nor {@code key=text}. */
        public final List<Integer> malformedLines;

        Parsed(Map<String, String> entries, Set<String> presentKeys, List<Integer> malformedLines) {
            this.entries = Collections.unmodifiableMap(entries);
            this.presentKeys = Collections.unmodifiableSet(presentKeys);
            this.malformedLines = Collections.unmodifiableList(malformedLines);
        }
    }

    /** An effect nothing describes yet, as the dump command writes it. */
    public static final class Missing {
        public final String key;
        public final String displayName;
        public final String modName;

        public Missing(String key, String displayName, String modName) {
            this.key = key;
            this.displayName = displayName;
            this.modName = modName;
        }
    }

    private static final List<String> HEADER = Collections.unmodifiableList(Arrays.asList(
            "# Effect descriptions for the srpwizcore effect panel.",
            "# One line per effect:  <registry name>=<description>   e.g.  minecraft:speed=Makes you walk faster.",
            "# Lines starting with # are comments. A line with nothing after '=' counts as not described yet.",
            "# The text is used exactly as written: a % is a plain %, and § formatting codes work.",
            "# Authoring loop: run /effectdesc missing in game, fill in the blank lines it appends here, press F3+T.",
            "# This file wins over every other source of effect descriptions (JEE, potiondescriptions, SRP bestiary)."));

    public static Parsed parse(List<String> lines) {
        Map<String, String> entries = new LinkedHashMap<>();
        Set<String> present = new LinkedHashSet<>();
        List<Integer> malformed = new ArrayList<>();
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            if (i == 0 && line.startsWith("\uFEFF")) {
                line = line.substring(1); // Notepad writes a BOM
            }
            String trimmed = line.trim();
            if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                continue;
            }
            int eq = trimmed.indexOf('=');
            String key = eq < 0 ? "" : trimmed.substring(0, eq).trim();
            if (key.isEmpty()) {
                malformed.add(i + 1);
                continue;
            }
            String text = trimmed.substring(eq + 1).trim();
            present.add(key);
            // A blank means "not described yet", never "delete my description": /effectdesc missing
            // run before F3+T could append "key=" below a text the user had just typed, and letting
            // that blank win would erase it without a word. A non-empty duplicate still wins, and
            // moves the entry to its later position.
            if (!text.isEmpty()) {
                entries.remove(key);
                entries.put(key, text);
            }
        }
        return new Parsed(entries, present, malformed);
    }

    /** The comment block a new file starts with. */
    public static List<String> header() {
        return HEADER;
    }

    /**
     * Lines to append for the effects in {@code missing} that have no line in the file yet. An
     * effect already present - even with an empty text - is not written again, so the dump can be
     * run any number of times. Nothing new means nothing at all, not even the section comment.
     */
    public static List<String> linesToAppend(Set<String> presentKeys, List<Missing> missing, String sectionLabel) {
        List<String> out = new ArrayList<>();
        Set<String> written = new HashSet<>(presentKeys);
        for (Missing m : missing) {
            if (!written.add(m.key)) {
                continue;
            }
            if (out.isEmpty()) {
                out.add("");
                out.add("# --- " + sectionLabel + " ---");
            }
            out.add("# " + m.displayName + " — " + m.modName);
            out.add(m.key + "=");
        }
        return out;
    }
}
