package com.spege.srpwizcore.util;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import com.spege.srpwizcore.SrpWizCore;
import com.spege.srpwizcore.config.SrpWizCoreConfig;

/**
 * Per-dimension override table for Ice&amp;Fire worldgen, parsed from
 * {@code SrpWizCoreConfig.iandfWorldgen}.
 *
 * <p><b>Universality is the point.</b> No dimension id is hardcoded anywhere in this mod — the
 * whole split lives in the config file. A dimension that has no entry for a given structure key
 * returns {@code null} from both getters, which the mixin translates into "use Ice&amp;Fire's own
 * value". An empty config therefore means the mod changes nothing at all.
 *
 * <p>Entry syntax (one {@code String[]} per structure key, one entry per line):
 * <pre>
 *     &lt;dimId&gt;=&lt;enabled&gt;[:&lt;chance&gt;]
 *     0=false
 *     150=true:90
 * </pre>
 * {@code chance} keeps Ice&amp;Fire's "1 in N" semantics for structures that have a native chance
 * field. For the ore keys (which are boolean-only in Ice&amp;Fire) it is <i>our</i> per-chunk veto
 * divider — see {@code MixinIandfStructureGenerator}.
 *
 * <p>Garbage input is never fatal: a malformed entry is logged at WARN and skipped, so a typo in
 * the config can never prevent the game from starting.
 *
 * <p>This class lives in {@code util/}, not in {@code mixins/} — mixin code may only reference
 * helper types from outside the mixin package (project rule, see CLAUDE.md).
 */
public final class IandfWorldgenOverrides {

    // ---- structure keys (also the config field names, kept in sync with IandfWorldgenCategory) ----
    public static final String DRAGON_DENS = "dragonDens";
    public static final String DRAGON_ROOSTS = "dragonRoosts";
    public static final String DRAGON_SKELETONS = "dragonSkeletons";
    public static final String MAUSOLEUMS = "mausoleums";
    public static final String GORGON_TEMPLE = "gorgonTemple";
    public static final String CYCLOPS_CAVES = "cyclopsCaves";
    public static final String WANDERING_CYCLOPS = "wanderingCyclops";
    public static final String MYRMEX_COLONIES = "myrmexColonies";
    public static final String PIXIE_VILLAGES = "pixieVillages";
    public static final String SIREN_ISLANDS = "sirenIslands";
    public static final String HYDRA_CAVES = "hydraCaves";
    public static final String SNOW_VILLAGES = "snowVillages";
    public static final String ORE_COPPER = "oreCopper";
    public static final String ORE_SILVER = "oreSilver";
    public static final String ORE_AMETHYST = "oreAmethyst";
    public static final String ORE_RUBY = "oreRuby";
    public static final String ORE_SAPPHIRE = "oreSapphire";

    /** One parsed config entry. {@code chance} is {@code null} when the entry omits it. */
    private static final class DimEntry {
        final boolean enabled;
        final Integer chance;

        DimEntry(boolean enabled, Integer chance) {
            this.enabled = enabled;
            this.chance = chance;
        }
    }

    /** structureKey -> (dimId -> entry). Replaced wholesale by {@link #rebuild()}. */
    private static volatile Map<String, Map<Integer, DimEntry>> table = Collections.emptyMap();

    private IandfWorldgenOverrides() {
    }

    /**
     * Re-parses the whole config category. Called once at mod init and again from the
     * {@code OnConfigChangedEvent} handler, so edits take effect without a restart (the mixin
     * itself still needs a restart to be applied at all — that is the master flag).
     */
    public static void rebuild() {
        Map<String, Map<Integer, DimEntry>> built = new HashMap<String, Map<Integer, DimEntry>>();
        if (!SrpWizCoreConfig.iandfWorldgen.enableIandfWorldgenControl) {
            table = built;
            SrpWizCore.LOGGER.info("[srpwizcore] I&F worldgen control disabled; no overrides active.");
            return;
        }
        int entries = 0;
        entries += parseInto(built, DRAGON_DENS, SrpWizCoreConfig.iandfWorldgen.dragonDens);
        entries += parseInto(built, DRAGON_ROOSTS, SrpWizCoreConfig.iandfWorldgen.dragonRoosts);
        entries += parseInto(built, DRAGON_SKELETONS, SrpWizCoreConfig.iandfWorldgen.dragonSkeletons);
        entries += parseInto(built, MAUSOLEUMS, SrpWizCoreConfig.iandfWorldgen.mausoleums);
        entries += parseInto(built, GORGON_TEMPLE, SrpWizCoreConfig.iandfWorldgen.gorgonTemple);
        entries += parseInto(built, CYCLOPS_CAVES, SrpWizCoreConfig.iandfWorldgen.cyclopsCaves);
        entries += parseInto(built, WANDERING_CYCLOPS, SrpWizCoreConfig.iandfWorldgen.wanderingCyclops);
        entries += parseInto(built, MYRMEX_COLONIES, SrpWizCoreConfig.iandfWorldgen.myrmexColonies);
        entries += parseInto(built, PIXIE_VILLAGES, SrpWizCoreConfig.iandfWorldgen.pixieVillages);
        entries += parseInto(built, SIREN_ISLANDS, SrpWizCoreConfig.iandfWorldgen.sirenIslands);
        entries += parseInto(built, HYDRA_CAVES, SrpWizCoreConfig.iandfWorldgen.hydraCaves);
        entries += parseInto(built, SNOW_VILLAGES, SrpWizCoreConfig.iandfWorldgen.snowVillages);
        entries += parseInto(built, ORE_COPPER, SrpWizCoreConfig.iandfWorldgen.oreCopper);
        entries += parseInto(built, ORE_SILVER, SrpWizCoreConfig.iandfWorldgen.oreSilver);
        entries += parseInto(built, ORE_AMETHYST, SrpWizCoreConfig.iandfWorldgen.oreAmethyst);
        entries += parseInto(built, ORE_RUBY, SrpWizCoreConfig.iandfWorldgen.oreRuby);
        entries += parseInto(built, ORE_SAPPHIRE, SrpWizCoreConfig.iandfWorldgen.oreSapphire);
        table = built;
        SrpWizCore.LOGGER.info("[srpwizcore] I&F worldgen overrides loaded: {} entries across {} keys.",
                Integer.valueOf(entries), Integer.valueOf(built.size()));
    }

    /** Parses one config array; returns how many entries were accepted. */
    private static int parseInto(Map<String, Map<Integer, DimEntry>> target, String key, String[] lines) {
        if (lines == null || lines.length == 0) {
            return 0;
        }
        Map<Integer, DimEntry> perDim = new HashMap<Integer, DimEntry>();
        for (int i = 0; i < lines.length; i++) {
            String raw = lines[i];
            if (raw == null) {
                continue;
            }
            String line = raw.trim();
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }
            int eq = line.indexOf('=');
            if (eq <= 0 || eq == line.length() - 1) {
                warn(key, raw, "expected <dimId>=<enabled>[:<chance>]");
                continue;
            }
            int dim;
            try {
                dim = Integer.parseInt(line.substring(0, eq).trim());
            } catch (NumberFormatException e) {
                warn(key, raw, "dimension id is not an integer");
                continue;
            }
            String value = line.substring(eq + 1).trim();
            String enabledPart = value;
            Integer chance = null;
            int colon = value.indexOf(':');
            if (colon >= 0) {
                enabledPart = value.substring(0, colon).trim();
                String chancePart = value.substring(colon + 1).trim();
                try {
                    int parsed = Integer.parseInt(chancePart);
                    if (parsed < 1) {
                        warn(key, raw, "chance must be >= 1 (Ice&Fire uses '1 in N')");
                        continue;
                    }
                    chance = Integer.valueOf(parsed);
                } catch (NumberFormatException e) {
                    warn(key, raw, "chance is not an integer");
                    continue;
                }
            }
            Boolean enabled = parseBoolean(enabledPart);
            if (enabled == null) {
                warn(key, raw, "enabled must be true or false");
                continue;
            }
            perDim.put(Integer.valueOf(dim), new DimEntry(enabled.booleanValue(), chance));
        }
        if (perDim.isEmpty()) {
            return 0;
        }
        target.put(key, perDim);
        return perDim.size();
    }

    private static Boolean parseBoolean(String s) {
        if ("true".equalsIgnoreCase(s)) {
            return Boolean.TRUE;
        }
        if ("false".equalsIgnoreCase(s)) {
            return Boolean.FALSE;
        }
        return null;
    }

    private static void warn(String key, String line, String why) {
        SrpWizCore.LOGGER.warn("[srpwizcore] Ignoring malformed iandfWorldgen entry in '{}': \"{}\" ({}).",
                key, line, why);
    }

    /**
     * @return {@code TRUE}/{@code FALSE} when the config has an entry for this structure in this
     *         dimension, {@code null} when it does not (caller must then use Ice&amp;Fire's own value).
     */
    public static Boolean enabledFor(String key, int dim) {
        DimEntry entry = lookup(key, dim);
        return entry == null ? null : Boolean.valueOf(entry.enabled);
    }

    /**
     * @return the configured chance ("1 in N") for this structure in this dimension, or
     *         {@code null} when there is no entry or the entry omits the chance part.
     */
    public static Integer chanceFor(String key, int dim) {
        DimEntry entry = lookup(key, dim);
        return entry == null ? null : entry.chance;
    }

    private static DimEntry lookup(String key, int dim) {
        Map<Integer, DimEntry> perDim = table.get(key);
        if (perDim == null) {
            return null;
        }
        return perDim.get(Integer.valueOf(dim));
    }
}
