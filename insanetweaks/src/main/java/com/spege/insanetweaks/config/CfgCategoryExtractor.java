package com.spege.insanetweaks.config;

/**
 * Lifts a whole root-level category out of a raw Forge {@code .cfg} file, as text.
 *
 * <p>Used by the split notices ({@link TombstoneSplitNotice}, {@link ReskillableSplitNotice}) to
 * find the fingerprint an older version of this mod left behind and to hand the player their old
 * settings back in a form they can paste into the mod that took the module over.
 *
 * <p>Text, not {@code Configuration}: this runs from the {@code @Mod} constructor, before Forge has
 * parsed anything, and the categories being looked for are precisely the ones the current schema no
 * longer declares — so there is nothing to ask a parsed config about.
 */
final class CfgCategoryExtractor {

    private CfgCategoryExtractor() {
    }

    /**
     * Returns the whole {@code <name> { ... }} block including its braces, or {@code null} if the
     * file has no such root-level category.
     *
     * <p>Matching is done on brace depth rather than with a regex so that nested sub-categories
     * cannot end the block early — Tombstone's {@code effectpools} and ten {@code Perk: ...} blocks,
     * and Reskillable's twenty per-trait blocks, are all nested. Only depth 0 is considered, so a
     * category of the same name nested inside another one is ignored.
     */
    static String extractCategory(String content, String category) {
        String[] lines = content.split("\r\n|\r|\n", -1);
        int depth = 0;
        for (int i = 0; i < lines.length; i++) {
            String trimmed = lines[i].trim();
            if (depth == 0 && isCategoryOpen(trimmed, category)) {
                StringBuilder block = new StringBuilder();
                int innerDepth = 0;
                for (int j = i; j < lines.length; j++) {
                    block.append(lines[j]).append('\n');
                    innerDepth += countUnquoted(lines[j], '{') - countUnquoted(lines[j], '}');
                    if (innerDepth <= 0) {
                        return block.toString();
                    }
                }
                // Truncated file: still a valid witness, just hand back what there is.
                return block.toString();
            }
            depth += countUnquoted(lines[i], '{') - countUnquoted(lines[i], '}');
            if (depth < 0) {
                depth = 0;
            }
        }
        return null;
    }

    /**
     * Finds one {@code key = value} line inside a root-level category and returns it verbatim,
     * trimmed. Used where the category itself still exists in the current schema — so its presence
     * proves nothing — but one option inside it has moved out.
     *
     * @param key the quoted option name as Forge writes it, e.g. {@code "Enable Skills Module"}
     */
    static String extractKeyLine(String content, String category, String key) {
        String block = extractCategory(content, category);
        if (block == null) {
            return null;
        }
        String needle = "\"" + key + "\"=";
        for (String line : block.split("\n", -1)) {
            String trimmed = line.trim();
            // Forge writes the type prefix (B:, I:, S:, D:) in front of the quoted name.
            int colon = trimmed.indexOf(':');
            if (colon == 1 && trimmed.startsWith(needle, colon + 1)) {
                return trimmed;
            }
        }
        return null;
    }

    /**
     * True for the two forms Forge writes for a category header: the bare name followed by an
     * opening brace, and the same thing double-quoted.
     */
    private static boolean isCategoryOpen(String trimmed, String category) {
        return trimmed.equals(category + " {") || trimmed.equals("\"" + category + "\" {");
    }

    /**
     * Counts a brace character outside of double quotes. Forge quotes any key containing a space,
     * and many of these keys do ({@code B:"Enable Skills Module"}), so a naive {@code indexOf} would
     * be wrong the moment someone puts a brace in a string list entry.
     */
    private static int countUnquoted(String line, char target) {
        int count = 0;
        boolean inQuotes = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '"') {
                inQuotes = !inQuotes;
            } else if (!inQuotes && c == target) {
                count++;
            }
        }
        return count;
    }
}
