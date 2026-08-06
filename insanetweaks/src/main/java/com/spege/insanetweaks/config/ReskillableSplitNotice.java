package com.spege.insanetweaks.config;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.spege.insanetweaks.InsaneTweaksMod;

import net.minecraftforge.fml.common.Loader;

/**
 * Detects a world that used the Reskillable trait module while it still lived inside this mod, and
 * decides whether the player has to be warned that the module moved out into Reskill Tweaks.
 *
 * <p><b>Why this exists, and why it is worse than the Tombstone case.</b> Up to 1.12.1 this mod
 * registered twenty traits into Reskillable's {@code UNLOCKABLES} registry under the
 * {@code compatskills:} domain. From 1.13.0 they live in {@code reskilltweaks}, which registers them
 * under the same domain — but only if it is installed.
 *
 * <p>Without it, nothing announces the loss. Verified against Reskillable 1.13.1's bytecode:
 * {@code PlayerSkillInfo.loadFromNBT} reads each saved unlock id and does
 * <pre>
 *   Optional.ofNullable(UNLOCKABLES.getValue(new ResourceLocation(key.replace(".", ":"))))
 *           .ifPresent(unlockables::add)
 * </pre>
 * An id that no longer resolves is simply not added — no exception, and no Forge
 * missing-registry-entry screen either, because these unlocks live in the player's own data rather
 * than in a registry saved to {@code level.dat}. The next save writes the shortened list back, and
 * the unlock is gone. {@code skillPoints} is an independent int that {@code unlock()} already
 * decremented when the trait was bought, so the points are not returned. The whole thing is silent
 * and irreversible, per player, at the moment their character data loads.
 *
 * <p>That is why this is a main-menu screen and not a chat message: the last useful moment is before
 * a world is loaded at all.
 *
 * <p><b>How the trace is found.</b> The raw config file is read as text, before Forge parses it. A
 * root-level {@code traits { ... }} or {@code scarredflesh { ... }} category can only be there
 * because an earlier version of this mod wrote it — the current schema declares neither, so a clean
 * install never has one.
 *
 * <p>🚨 Do not assume Forge prunes those categories once the schema stops declaring them. It does
 * not — measured 2026-08-06, see {@link TombstoneSplitNotice}. The extracted copy below is therefore
 * a convenience for the player, not the only surviving witness.
 *
 * <p>Three things must be true before anyone is bothered:
 * <ol>
 *   <li>the trace is present — this pack ran a version that had the module;</li>
 *   <li>Reskillable is loaded — without it nothing was ever registered and nothing is at risk;</li>
 *   <li>Reskill Tweaks is <em>not</em> loaded — with it, the traits are registered and the unlocks
 *       are safe.</li>
 * </ol>
 *
 * <p>🚨 The scan has to run from the {@code @Mod} constructor, same as {@link OldConfigBackup} and
 * {@link TombstoneSplitNotice}, so that it sees the file before FML's first
 * {@code ConfigManager.sync}.
 */
public final class ReskillableSplitNotice {

    private static final Logger LOGGER = LogManager.getLogger(InsaneTweaksMod.MODID);

    /** Reskillable's mod id. */
    private static final String RESKILLABLE_MODID = "reskillable";

    /** The mod the module moved into. Must match Reskill Tweaks' {@code @Mod} modid. */
    private static final String RESKILLTWEAKS_MODID = "reskilltweaks";

    /**
     * The public project page. Deliberately NOT the {@code /preview} form of this address: that one
     * is the author's view of a project still awaiting approval and it 404s for everybody else, so
     * shipping it would put a dead link in front of exactly the players this screen exists for. Once
     * the project is approved this address is the one that works.
     */
    public static final String DOWNLOAD_URL =
            "https://www.curseforge.com/minecraft/mc-mods/reskillable-tweaks";

    /** Categories this mod used to write into insanetweaks.cfg; absent from the current schema. */
    private static final String CATEGORY_TRAITS = "traits";
    private static final String CATEGORY_SCARRED_FLESH = "scarredflesh";

    /** The module switch. Its category still exists, so only the key itself is a witness. */
    private static final String CATEGORY_MODULES = "modules";
    private static final String KEY_ENABLE = "Enable Skills Module";

    /**
     * Options the pre-2026-07-28 per-slot Scarred Flesh design used, deleted from
     * {@code ScarredFleshCategory} when it became a level budget. Forge never pruned them, so a
     * config that has been around since July still carries them — and carrying them forward would
     * hand the player four knobs that look tunable and do nothing at all. Stripped on the way out.
     */
    private static final String[] DEAD_SCARRED_FLESH_KEYS = {
            "Amplifier Caps", "Duration Multipliers", "Free Debuff Slots", "Max Debuff Slots"
    };

    /** Where the extracted old settings are parked, ready to become reskilltweaks.cfg. */
    private static final String EXTRACT_NAME = "insanetweaks-reskillable-old-settings.cfg";

    /** Written by the "don't show again" button. Presence silences the screen for good. */
    private static final String ACK_NAME = "insanetweaks-reskillable-notice.ack";

    private static boolean traceFound;
    private static boolean acknowledged;
    private static File configDir;
    /** Absolute path of the extracted settings file, or null if nothing was extracted. */
    private static String extractedPath;

    private ReskillableSplitNotice() {
    }

    // ---------------------------------------------------------------------
    // Phase 1 - file scan, from the @Mod constructor
    // ---------------------------------------------------------------------

    /**
     * Reads the raw config and records whether the trait module ever ran here. File IO only —
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

                String traits = CfgCategoryExtractor.extractCategory(content, CATEGORY_TRAITS);
                String scarredFlesh =
                        CfgCategoryExtractor.extractCategory(content, CATEGORY_SCARRED_FLESH);
                if (traits == null && scarredFlesh == null) {
                    continue;
                }

                traceFound = true;
                String enableLine =
                        CfgCategoryExtractor.extractKeyLine(content, CATEGORY_MODULES, KEY_ENABLE);
                extractOldSettings(traits, scarredFlesh, enableLine, cfg.getName());
                return;
            }
        } catch (Exception e) {
            // Never fatal: the worst case is that a player who could have been warned is not.
            LOGGER.warn("[InsaneTweaks] Could not scan the config for the old Reskillable module: {}",
                    e.toString());
        }
    }

    /**
     * Parks the old settings in their own file, laid out as a complete {@code reskilltweaks.cfg} —
     * the three categories there are exactly the three things being lifted out here, and every
     * category and option name is unchanged, so this is a copy rather than a translation.
     *
     * <p>Deliberately does not touch {@code insanetweaks.cfg}, and deliberately does not write
     * {@code reskilltweaks.cfg}: another mod's config file is not ours to create.
     */
    private static void extractOldSettings(String traits, String scarredFlesh, String enableLine,
            String sourceName) {
        try {
            File out = new File(configDir, EXTRACT_NAME);
            if (out.isFile()) {
                extractedPath = out.getAbsolutePath();
                return;
            }

            StringBuilder sb = new StringBuilder();
            sb.append("# Your old Reskillable-module settings, lifted out of ").append(sourceName)
                    .append(".\n");
            sb.append("#\n");
            sb.append("# This module now lives in a separate mod, Reskill Tweaks:\n");
            sb.append("#   ").append(DOWNLOAD_URL).append("\n");
            sb.append("#\n");
            sb.append("# What follows is a complete reskilltweaks.cfg. If config/reskilltweaks.cfg\n");
            sb.append("# does not exist yet, copy this file over it - the comment lines are ignored.\n");
            sb.append("# If it does exist, replace its categories with the ones below. Category and\n");
            sb.append("# option names are unchanged, so nothing has to be translated.\n");
            sb.append("#\n");
            sb.append("# One difference worth knowing: 'Enable Skills Module' defaults to ON in\n");
            sb.append("# Reskill Tweaks, because there it IS the mod, whereas here it was one\n");
            sb.append("# optional module among a dozen and defaulted to OFF. The value below is the\n");
            sb.append("# one you actually had.\n");
            sb.append("#\n");
            sb.append("# This file is only a copy. Nothing reads it, and deleting it is safe.\n\n");

            sb.append("modules {\n");
            sb.append("    ").append(enableLine != null ? enableLine : "B:\"Enable Skills Module\"=true")
                    .append("\n");
            sb.append("}\n\n\n");

            if (scarredFlesh != null) {
                sb.append(stripDeadKeys(scarredFlesh)).append("\n\n");
            }
            if (traits != null) {
                sb.append(traits);
            }

            Files.write(out.toPath(), sb.toString().getBytes(StandardCharsets.UTF_8));
            extractedPath = out.getAbsolutePath();
            LOGGER.info("[InsaneTweaks] Saved your old Reskillable settings to config/{}", EXTRACT_NAME);
        } catch (Exception e) {
            LOGGER.warn("[InsaneTweaks] Could not save the old Reskillable settings: {}", e.toString());
        }
    }

    /**
     * Drops the four retired Scarred Flesh options. Two are scalars and two are list blocks, so this
     * works on the {@code TYPE:"Name"} prefix and, for a list, keeps skipping until the closing
     * {@code >}. Anything it does not recognise is copied through untouched.
     */
    private static String stripDeadKeys(String block) {
        String[] lines = block.split("\n", -1);
        StringBuilder out = new StringBuilder();
        boolean skippingList = false;

        for (String line : lines) {
            String trimmed = line.trim();

            if (skippingList) {
                if (trimmed.equals(">")) {
                    skippingList = false;
                }
                continue;
            }

            String dead = matchDeadKey(trimmed);
            if (dead != null) {
                // A list opens with '<' on the same line and runs until a lone '>'.
                skippingList = trimmed.endsWith("<");
                continue;
            }

            out.append(line).append('\n');
        }

        // split("\n", -1) leaves a trailing empty element; the loop already appended its newline.
        int len = out.length();
        return len > 0 && out.charAt(len - 1) == '\n' ? out.substring(0, len - 1) : out.toString();
    }

    private static String matchDeadKey(String trimmed) {
        int colon = trimmed.indexOf(':');
        if (colon != 1) {
            return null;
        }
        for (String key : DEAD_SCARRED_FLESH_KEYS) {
            if (trimmed.startsWith("\"" + key + "\"", colon + 1)) {
                return key;
            }
        }
        return null;
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
                && Loader.isModLoaded(RESKILLABLE_MODID)
                && !Loader.isModLoaded(RESKILLTWEAKS_MODID);
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
                    ("Delete this file to see the Reskill Tweaks notice again.\n")
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
        LOGGER.warn("  [InsaneTweaks] The Reskillable trait integration is no longer in this mod.");
        LOGGER.warn("  It moved to Reskill Tweaks as of {}:", InsaneTweaksMod.VERSION);
        LOGGER.warn("    {}", DOWNLOAD_URL);
        LOGGER.warn("");
        LOGGER.warn("  Reskillable is installed and Reskill Tweaks is not, and this pack ran a");
        LOGGER.warn("  version that registered twenty traits under the compatskills: domain.");
        LOGGER.warn("  Nothing registers them now.");
        LOGGER.warn("");
        LOGGER.warn("  There will be NO warning screen for this. Reskillable resolves each saved");
        LOGGER.warn("  unlock through Optional.ofNullable(...).ifPresent(...), so an id that no");
        LOGGER.warn("  longer exists is dropped in silence, per player, as their data loads. The");
        LOGGER.warn("  next save writes the shortened list back and the unlock is gone for good.");
        LOGGER.warn("  The skill points spent on it are NOT refunded.");
        LOGGER.warn("");
        LOGGER.warn("  Install Reskill Tweaks before loading the world and nothing is lost.");
        if (extractedPath != null) {
            LOGGER.warn("  Your old settings were copied to:");
            LOGGER.warn("    {}", extractedPath);
        }
        LOGGER.warn("========================================================================");
    }
}
