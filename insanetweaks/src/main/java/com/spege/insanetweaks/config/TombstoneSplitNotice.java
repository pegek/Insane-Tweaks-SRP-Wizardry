package com.spege.insanetweaks.config;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.spege.insanetweaks.InsaneTweaksMod;

import net.minecraftforge.fml.common.Loader;

/**
 * Detects a world that used the Tombstone module while it still lived inside this mod, and decides
 * whether the player has to be warned that the module moved out into CTombstone-Tweaks.
 *
 * <p><b>Why this exists.</b> Up to 1.8.0 this mod registered two Corail Tombstone perks,
 * {@code insanetweaks:assimilated_knowledge} and {@code insanetweaks:relief_for_the_damned}.
 * Tombstone persists a player's perk levels in {@code level.dat} keyed by <em>registry name</em>,
 * so those two names must keep existing for the levels to survive. From 1.9.0 the whole module
 * lives in a separate mod, which still registers them under the original {@code insanetweaks:}
 * namespace precisely so nothing is lost — but only if that mod is actually installed.
 *
 * <p>Updating without it means Forge greets the player with the missing-registry-entry screen, and
 * clicking through that screen discards the entries <b>permanently, with no refund</b>. There is no
 * way to repair it afterwards, so the only useful moment to say something is before a world is
 * loaded at all. Hence a main-menu screen rather than a chat message.
 *
 * <p><b>How the trace is found.</b> The raw config file is read as text, before Forge parses it.
 * A root-level {@code tombstone { ... }} category can only be there because an earlier version of
 * this mod wrote it — the current schema does not declare that category, so a clean install never
 * has one.
 *
 * <p>🚨 That trace only survives one launch. Forge drops categories it does not recognise the
 * first time it rewrites the file, which happens seconds after this class reads it. A notice that
 * relied on the raw file alone would therefore appear exactly once and then go quiet, which is no
 * use to a player who closed the game and came back the next day. So the first sighting is made
 * durable: the block is copied to its own file, and from then on that file <em>is</em> the trace.
 * It also happens to be the thing the player needs in order to migrate their settings, so it earns
 * its place twice.
 *
 * <p>Three things must be true before anyone is bothered:
 * <ol>
 *   <li>the trace is present — this pack ran a version that had the module;</li>
 *   <li>Corail Tombstone is loaded — without it nothing was ever registered and nothing is at risk;</li>
 *   <li>CTombstone-Tweaks is <em>not</em> loaded — with it, the names are registered and the levels
 *       are safe.</li>
 * </ol>
 *
 * <p>🚨 The scan has to run from the {@code @Mod} constructor, same as {@link OldConfigBackup} and
 * for the same reason: FML performs its first {@code ConfigManager.sync} immediately afterwards
 * inside {@code FMLModContainer.constructMod}. Reading later is not fatal here (Forge keeps the
 * unknown category) but it would be one behaviour change in Forge away from silently never firing.
 */
public final class TombstoneSplitNotice {

    private static final Logger LOGGER = LogManager.getLogger(InsaneTweaksMod.MODID);

    /** Corail Tombstone's mod id. */
    private static final String TOMBSTONE_MODID = "tombstone";

    /** The mod the module moved into. Must match CTombstone-Tweaks' {@code @Mod} modid. */
    private static final String TOMBTWEAKS_MODID = "tombtweaks";

    public static final String DOWNLOAD_URL =
            "https://www.curseforge.com/minecraft/mc-mods/ctombstone-tweaks";

    /** Category this mod used to write into insanetweaks.cfg; absent from the current schema. */
    private static final String CATEGORY = "tombstone";

    /** Where the extracted old settings are parked, ready to paste into tombtweaks.cfg. */
    private static final String EXTRACT_NAME = "insanetweaks-tombstone-old-settings.cfg";

    /** Written by the "don't show again" button. Presence silences the screen for good. */
    private static final String ACK_NAME = "insanetweaks-tombstone-notice.ack";

    private static boolean traceFound;
    private static boolean acknowledged;
    private static File configDir;
    /** Absolute path of the extracted settings file, or null if nothing was extracted. */
    private static String extractedPath;

    private TombstoneSplitNotice() {
    }

    // ---------------------------------------------------------------------
    // Phase 1 - file scan, from the @Mod constructor
    // ---------------------------------------------------------------------

    /**
     * Reads the raw config and records whether the Tombstone module ever ran here. File IO only —
     * deliberately no {@code Loader.isModLoaded} call, because the mod list is not something to
     * depend on this early. Never throws.
     */
    public static void scan() {
        try {
            configDir = Loader.instance().getConfigDir();
            if (configDir == null) {
                return;
            }

            acknowledged = new File(configDir, ACK_NAME).isFile();

            // The durable trace, written on the launch the raw category was last visible. Checked
            // first because by now the category itself has almost certainly been pruned out of
            // insanetweaks.cfg by Forge - see the class comment.
            File extracted = new File(configDir, EXTRACT_NAME);
            if (extracted.isFile()) {
                traceFound = true;
                extractedPath = extracted.getAbsolutePath();
                return;
            }

            // Both names: OldConfigBackup may have just moved insanetweaks.cfg aside, and the
            // pre-rework file is just as good a witness that the module used to be here.
            File[] candidates = {
                    new File(configDir, "insanetweaks.cfg"),
                    new File(configDir, "insanetweaks.cfg.pre-rework")
            };

            for (File cfg : candidates) {
                if (!cfg.isFile()) {
                    continue;
                }
                String content = new String(Files.readAllBytes(cfg.toPath()), StandardCharsets.UTF_8);
                String block = extractCategory(content);
                if (block == null) {
                    continue;
                }
                traceFound = true;
                extractOldSettings(block, cfg.getName());
                return;
            }
        } catch (Exception e) {
            // Never fatal: the worst case is that a player who could have been warned is not.
            LOGGER.warn("[InsaneTweaks] Could not scan the config for the old Tombstone module: {}",
                    e.toString());
        }
    }

    /**
     * Returns the whole {@code tombstone { ... }} block including its braces, or null if the file
     * has no such root-level category.
     *
     * <p>Matching is done on brace depth rather than with a regex so that nested sub-categories
     * ({@code effectpools}, {@code raideralignment}, the ten {@code Perk: ...} blocks) cannot end
     * the block early. Only depth 0 is considered, so a category of the same name nested inside
     * another one — which cannot happen today, but costs nothing to rule out — is ignored.
     */
    private static String extractCategory(String content) {
        String[] lines = content.split("\r\n|\r|\n", -1);
        int depth = 0;
        for (int i = 0; i < lines.length; i++) {
            String trimmed = lines[i].trim();
            if (depth == 0 && isCategoryOpen(trimmed)) {
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
     * True for the two forms Forge writes for a category header: the bare name followed by an
     * opening brace, and the same thing double-quoted.
     */
    private static boolean isCategoryOpen(String trimmed) {
        return trimmed.equals(CATEGORY + " {") || trimmed.equals("\"" + CATEGORY + "\" {");
    }

    /**
     * Counts a brace character outside of double quotes. Forge quotes any key containing a space,
     * and several of this category's keys do ({@code B:"Enable Tombstone Tweaks"}), so a naive
     * {@code indexOf} would be wrong the moment someone puts a brace in a string list entry.
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

    /**
     * Parks the old block in its own file. This is what makes the notice survive past the launch
     * on which Forge prunes the category, and it is also the thing the player pastes into
     * {@code tombtweaks.cfg} instead of hand-copying ninety values.
     *
     * <p>The caller has already established that the file is absent, so this only guards against
     * it appearing between the two calls; it never overwrites one the player has started editing.
     *
     * <p>Deliberately does not touch {@code insanetweaks.cfg} itself, and deliberately does not
     * write {@code tombtweaks.cfg} — another mod's config file is not ours to create.
     */
    private static void extractOldSettings(String block, String sourceName) {
        try {
            File out = new File(configDir, EXTRACT_NAME);
            if (out.isFile()) {
                extractedPath = out.getAbsolutePath();
                return;
            }
            String header = "# Your old Tombstone-module settings, lifted out of " + sourceName + ".\n"
                    + "#\n"
                    + "# This module now lives in a separate mod, CTombstone-Tweaks:\n"
                    + "#   " + DOWNLOAD_URL + "\n"
                    + "#\n"
                    + "# Paste the block below into config/tombtweaks.cfg, replacing the tombstone\n"
                    + "# category there. The category name and every option name are unchanged, so it\n"
                    + "# is a straight copy - nothing has to be translated.\n"
                    + "#\n"
                    + "# This file is only a copy. Nothing reads it, and deleting it is safe.\n\n";
            Files.write(out.toPath(), (header + block).getBytes(StandardCharsets.UTF_8));
            extractedPath = out.getAbsolutePath();
            LOGGER.info("[InsaneTweaks] Saved your old Tombstone settings to config/{}", EXTRACT_NAME);
        } catch (Exception e) {
            LOGGER.warn("[InsaneTweaks] Could not save the old Tombstone settings: {}", e.toString());
        }
    }

    // ---------------------------------------------------------------------
    // Phase 2 - verdict, from preInit onwards
    // ---------------------------------------------------------------------

    /**
     * Whether this launch has to warn about the split. Safe to call every frame — it only reads
     * fields and the mod list.
     */
    public static boolean shouldWarn() {
        return traceFound
                && !acknowledged
                && Loader.isModLoaded(TOMBSTONE_MODID)
                && !Loader.isModLoaded(TOMBTWEAKS_MODID);
    }

    /** Path of the extracted settings file for display, or null if there is none. */
    public static String getExtractedPath() {
        return extractedPath;
    }

    /** Suppresses the screen from now on by dropping a marker next to the config. */
    public static void acknowledge() {
        acknowledged = true;
        try {
            if (configDir == null) {
                return;
            }
            File ack = new File(configDir, ACK_NAME);
            Files.write(ack.toPath(),
                    ("Delete this file to see the CTombstone-Tweaks notice again.\n")
                            .getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            // Only costs the player one more screen next launch.
            LOGGER.warn("[InsaneTweaks] Could not write the notice marker: {}", e.toString());
        }
    }

    /**
     * Says the same thing in the log, for dedicated servers and for anyone reading a crash report.
     * The GUI is the part players actually read; this is the part that survives being sent to
     * someone else.
     */
    public static void logIfNeeded() {
        if (!shouldWarn()) {
            return;
        }
        LOGGER.warn("========================================================================");
        LOGGER.warn("  [InsaneTweaks] The Corail Tombstone integration is no longer in this mod.");
        LOGGER.warn("  It moved to CTombstone-Tweaks as of 1.9.0:");
        LOGGER.warn("    {}", DOWNLOAD_URL);
        LOGGER.warn("");
        LOGGER.warn("  Corail Tombstone is installed and CTombstone-Tweaks is not, and this pack");
        LOGGER.warn("  ran a version that registered the two perks");
        LOGGER.warn("    insanetweaks:assimilated_knowledge");
        LOGGER.warn("    insanetweaks:relief_for_the_damned");
        LOGGER.warn("  Nothing registers those names now. Forge will report them as missing");
        LOGGER.warn("  registry entries, and confirming that screen discards every level your");
        LOGGER.warn("  players invested in them - permanently, with no refund.");
        LOGGER.warn("");
        LOGGER.warn("  Install CTombstone-Tweaks before loading the world and nothing is lost.");
        if (extractedPath != null) {
            LOGGER.warn("  Your old settings were copied to:");
            LOGGER.warn("    {}", extractedPath);
        }
        LOGGER.warn("========================================================================");
    }
}
