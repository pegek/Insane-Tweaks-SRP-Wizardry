package com.spege.insanetweaks.config;

import com.spege.insanetweaks.InsaneTweaksMod;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextFormatting;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.PlayerEvent;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;

/**
 * Deploys the bundled optimized reskillable.cfg during preInit.
 *
 * Smart merge: the player's existing "Skill Locks" entries are extracted from the old
 * file and re-injected into our template, so custom item locks are never lost.
 * Only insanetweaks:* entries are filtered out (our template already contains them).
 *
 * Version-aware flag: the sentinel file (insanetweaks_reskillable_managed.flag) stores
 * the mod version string that was active when the config was last deployed. If the mod
 * is updated to a new version, the mismatch triggers a fresh deploy (with lock merge),
 * ensuring new trait costs and entries from reskillable_optimized.cfg are applied.
 * Within the same mod version, the player can freely edit reskillable.cfg — it will
 * not be touched on subsequent launches.
 *
 * To force a manual re-deploy: delete the flag file.
 */
public class ReskillableConfigSwapper {

    /** Sentinel file storing the mod version that last deployed the config. */
    private static final String FLAG_FILE_NAME = "insanetweaks_reskillable_managed.flag";

    // Set to true when a swap occurs this session; cleared after the first login warning.
    private static boolean wasSwappedThisSession = false;
    // Tracks whether the re-deploy was triggered by a version change (vs. first install).
    private static boolean wasVersionUpdate = false;

    // -------------------------------------------------------------------------
    // Entry point
    // -------------------------------------------------------------------------

    public static void processConfig(FMLPreInitializationEvent event) {
        File configDir       = event.getModConfigurationDirectory();
        File reskillableConfig = new File(configDir, "reskillable.cfg");
        File flagFile          = new File(configDir, FLAG_FILE_NAME);
        String currentVersion  = InsaneTweaksMod.VERSION;

        // Check if already deployed for the CURRENT mod version.
        if (flagFile.exists() && reskillableConfig.exists()) {
            String deployedVersion = readFlagVersion(flagFile);
            if (currentVersion.equals(deployedVersion)) {
                // Same version, player may have made custom edits — do NOT touch the file.
                return;
            }
            // Version mismatch → mod was updated. Re-deploy to pick up new trait costs.
            System.out.println("[InsaneTweaks] Reskillable config version mismatch " +
                    "(deployed=" + deployedVersion + ", current=" + currentVersion + "). Re-deploying...");
            wasVersionUpdate = true;
        }

        // --- Step 1: Preserve the player's custom item locks before touching the file ---
        List<String> customLocks = new ArrayList<>();
        if (reskillableConfig.exists()) {
            customLocks = extractCustomLocks(reskillableConfig);

            // On first install (no flag): create a one-time backup.
            // On version update: existing backup is kept; don't overwrite it.
            if (!wasVersionUpdate) {
                File backupConfig = new File(configDir, "reskillable(Old_backup).cfg");
                if (!backupConfig.exists()) {
                    try {
                        Files.copy(reskillableConfig.toPath(), backupConfig.toPath(), StandardCopyOption.REPLACE_EXISTING);
                        System.out.println("[InsaneTweaks] Backed up existing reskillable.cfg -> reskillable(Old_backup).cfg");
                    } catch (Exception e) {
                        System.err.println("[InsaneTweaks] Failed to back up reskillable.cfg before overwrite!");
                        e.printStackTrace();
                    }
                }
            }
        }

        // --- Step 2: Read the bundled template, merge player locks, write result ---
        try (InputStream in = ReskillableConfigSwapper.class.getResourceAsStream(
                "/assets/insanetweaks/reskillable_optimized.cfg")) {
            if (in != null) {
                String template = readStream(in);
                String merged   = mergeLocks(template, customLocks);
                Files.write(reskillableConfig.toPath(), merged.getBytes(StandardCharsets.UTF_8));

                // Write the current mod version into the flag file.
                // Next launch reads this to decide whether a re-deploy is needed.
                Files.write(flagFile.toPath(), currentVersion.getBytes(StandardCharsets.UTF_8));

                wasSwappedThisSession = true;
                MinecraftForge.EVENT_BUS.register(new LoginWarningHandler());

                System.out.println("[InsaneTweaks] Successfully deployed optimized Reskillable config v" +
                        currentVersion + (customLocks.isEmpty() ? "." :
                        " (preserved " + customLocks.size() + " custom item lock(s))."));
            } else {
                System.err.println("[InsaneTweaks] FATAL: Could not find bundled reskillable_optimized.cfg in jar resources!");
            }
        } catch (Exception e) {
            System.err.println("[InsaneTweaks] Exception while deploying reskillable_optimized.cfg!");
            e.printStackTrace();
        }
    }

    // -------------------------------------------------------------------------
    // Lock extraction
    // -------------------------------------------------------------------------

    /**
     * Parses the "Skill Locks" multi-line block from an existing reskillable.cfg.
     *
     * Returns trimmed lock lines only, skipping:
     *   - empty lines
     *   - comment lines (starting with #)
     *   - insanetweaks:* entries (already present in our template)
     *
     * Compatible with Java 8 (Files.readAllLines strips CR from CRLF automatically).
     */
    private static List<String> extractCustomLocks(File configFile) {
        List<String> result = new ArrayList<>();
        try {
            List<String> lines = Files.readAllLines(configFile.toPath(), StandardCharsets.UTF_8);
            boolean inLocksBlock = false;

            for (String line : lines) {
                String trimmed = line.trim();

                if (!inLocksBlock) {
                    // Detect the opening of the Skill Locks <multi-line> block.
                    if (trimmed.startsWith("S:\"Skill Locks\"") && trimmed.contains("<")) {
                        inLocksBlock = true;
                    }
                    continue;
                }

                // Closing marker (Forge writes it as "     >" — trims to just ">").
                if (trimmed.equals(">")) {
                    break;
                }

                // Skip empty lines, comment lines, and our own insanetweaks managed entries.
                if (trimmed.isEmpty() || trimmed.startsWith("#") || trimmed.startsWith("insanetweaks:")) {
                    continue;
                }

                result.add(trimmed);
            }

            if (!result.isEmpty()) {
                System.out.println("[InsaneTweaks] Found " + result.size() +
                        " custom item lock(s) in original reskillable.cfg — they will be preserved.");
            }
        } catch (Exception e) {
            System.err.println("[InsaneTweaks] Could not parse custom locks from original reskillable.cfg — skipping merge.");
            e.printStackTrace();
        }
        return result;
    }

    // -------------------------------------------------------------------------
    // Lock injection
    // -------------------------------------------------------------------------

    /**
     * Injects customLocks into the template's "Skill Locks" block, just before its
     * closing marker. The template is normalized to LF line endings for processing,
     * then written as UTF-8 (Reskillable reads it fine either way).
     */
    private static String mergeLocks(String template, List<String> customLocks) {
        if (customLocks.isEmpty()) {
            return template;
        }

        // Normalize CRLF to LF for consistent line-by-line processing.
        String normalized = template.replace("\r\n", "\n").replace("\r", "\n");
        // Use limit=-1 so trailing empty strings from split are preserved.
        String[] lines = normalized.split("\n", -1);

        StringBuilder result   = new StringBuilder();
        boolean inLocksBlock   = false;
        boolean inserted       = false;

        for (String line : lines) {
            String trimmed = line.trim();

            if (!inLocksBlock) {
                result.append(line).append("\n");
                if (trimmed.startsWith("S:\"Skill Locks\"") && trimmed.contains("<")) {
                    inLocksBlock = true;
                }
                continue;
            }

            // Inject custom locks just before the closing >.
            if (!inserted && trimmed.equals(">")) {
                for (String lock : customLocks) {
                    result.append("        ").append(lock).append("\n");
                }
                inserted     = true;
                inLocksBlock = false;
            }

            result.append(line).append("\n");
        }

        return result.toString();
    }

    // -------------------------------------------------------------------------
    // Utility
    // -------------------------------------------------------------------------

    /**
     * Reads the mod version stored inside the flag file.
     * Returns an empty string if the file is missing or unreadable,
     * which guarantees a version mismatch and triggers a re-deploy.
     */
    private static String readFlagVersion(File flagFile) {
        try {
            List<String> lines = Files.readAllLines(flagFile.toPath(), StandardCharsets.UTF_8);
            return lines.isEmpty() ? "" : lines.get(0).trim();
        } catch (Exception e) {
            return ""; // Treat as unknown version → force re-deploy.
        }
    }

    /**
     * Reads an InputStream fully into a String using UTF-8 encoding.
     * Java 8 compatible — does not use InputStream.readAllBytes() (Java 9+).
     */
    private static String readStream(InputStream in) throws IOException {
        byte[] buffer = new byte[4096];
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        int n;
        while ((n = in.read(buffer)) != -1) {
            bos.write(buffer, 0, n);
        }
        return bos.toString("UTF-8");
    }

    // -------------------------------------------------------------------------
    // Login warning
    // -------------------------------------------------------------------------

    /**
     * Registered on the event bus only when a config swap has occurred this session.
     * Sends a one-time warning to the first player who logs in.
     */
    public static class LoginWarningHandler {

        @SubscribeEvent
        public void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
            if (!wasSwappedThisSession) return;

            event.player.sendMessage(new TextComponentString(TextFormatting.DARK_RED + "========================================"));
            event.player.sendMessage(new TextComponentString(TextFormatting.RED + "[Insane Tweaks] " + TextFormatting.YELLOW + "Reskillable skill tree has been optimized!"));
            event.player.sendMessage(new TextComponentString(TextFormatting.GRAY + "Old config was backed up as: " + TextFormatting.WHITE + "config/reskillable(Old_backup).cfg"));
            event.player.sendMessage(new TextComponentString(TextFormatting.GRAY + "Custom item locks from your world have been preserved."));
            event.player.sendMessage(new TextComponentString(TextFormatting.GREEN + "New costs are already active — no restart required!"));
            event.player.sendMessage(new TextComponentString(TextFormatting.DARK_RED + "========================================"));

            // Clear the flag so this message is sent only once per session.
            wasSwappedThisSession = false;
        }
    }
}
