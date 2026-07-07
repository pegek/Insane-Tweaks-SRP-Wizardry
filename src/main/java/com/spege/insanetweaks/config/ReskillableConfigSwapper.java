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
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Deploys the bundled optimized reskillable.cfg during preInit.
 *
 * Design goals:
 * - keep InsaneTweaks in control of the bundled Reskillable balance
 * - preserve user-defined Skill Locks from the existing config
 * - re-deploy only when the mod version changes
 * - avoid destructive overwrites when parsing or backup steps fail
 */
public class ReskillableConfigSwapper {

    /** Sentinel file storing the mod version that last deployed the config. */
    private static final String FLAG_FILE_NAME = "insanetweaks_reskillable_managed.flag";

    private static boolean wasSwappedThisSession = false;
    private static boolean wasVersionUpdate = false;
    private static String lastBackupFileName = null;
    private static int preservedCustomLocksCount = 0;
    private static boolean loginWarningRegistered = false;

    private static final class LockExtractionResult {
        private final boolean success;
        private final List<String> locks;

        private LockExtractionResult(boolean success, List<String> locks) {
            this.success = success;
            this.locks = locks;
        }

        private static LockExtractionResult success(List<String> locks) {
            return new LockExtractionResult(true, locks);
        }

        private static LockExtractionResult failure() {
            return new LockExtractionResult(false, new ArrayList<String>());
        }
    }

    public static void processConfig(FMLPreInitializationEvent event) {
        File configDir = event.getModConfigurationDirectory();
        File reskillableConfig = new File(configDir, "reskillable.cfg");
        File flagFile = new File(configDir, FLAG_FILE_NAME);
        String currentVersion = resolveCurrentVersion();

        wasVersionUpdate = false;
        lastBackupFileName = null;
        preservedCustomLocksCount = 0;

        if (flagFile.exists() && reskillableConfig.exists()) {
            String deployedVersion = readFlagVersion(flagFile);
            if (currentVersion.equals(deployedVersion)) {
                return;
            }

            wasVersionUpdate = true;
            InsaneTweaksMod.LOGGER.info(
                    "[InsaneTweaks] Reskillable config version mismatch (deployed={}, current={}). Re-deploying...",
                    deployedVersion, currentVersion);
        }

        List<String> customLocks = new ArrayList<String>();
        if (reskillableConfig.exists()) {
            LockExtractionResult extraction = extractCustomLocks(reskillableConfig);
            if (!extraction.success) {
                InsaneTweaksMod.LOGGER.error(
                        "[InsaneTweaks] Aborting Reskillable config swap because custom lock extraction failed. Existing reskillable.cfg was left untouched.");
                return;
            }

            customLocks = extraction.locks;
            preservedCustomLocksCount = customLocks.size();

            try {
                lastBackupFileName = createBackup(configDir, reskillableConfig, currentVersion);
                InsaneTweaksMod.LOGGER.info("[InsaneTweaks] Backed up existing reskillable.cfg -> {}",
                        lastBackupFileName);
            } catch (IOException e) {
                InsaneTweaksMod.LOGGER.error(
                        "[InsaneTweaks] Failed to create a safety backup for reskillable.cfg. Aborting swap to avoid destructive overwrite.",
                        e);
                return;
            }
        }

        try (InputStream in = ReskillableConfigSwapper.class.getResourceAsStream(
                "/assets/insanetweaks/reskillable_optimized.cfg")) {
            if (in == null) {
                InsaneTweaksMod.LOGGER.error(
                        "[InsaneTweaks] FATAL: Could not find bundled reskillable_optimized.cfg in jar resources.");
                return;
            }

            String template = readStream(in);
            String merged = mergeLocks(template, customLocks);
            writeAtomically(reskillableConfig.toPath(), merged.getBytes(StandardCharsets.UTF_8));
            writeAtomically(flagFile.toPath(), currentVersion.getBytes(StandardCharsets.UTF_8));

            wasSwappedThisSession = true;
            if (!loginWarningRegistered) {
                MinecraftForge.EVENT_BUS.register(new LoginWarningHandler());
                loginWarningRegistered = true;
            }

            InsaneTweaksMod.LOGGER.info("[InsaneTweaks] Successfully deployed optimized Reskillable config v{}{}",
                    currentVersion,
                    customLocks.isEmpty() ? "." : " (preserved " + customLocks.size() + " custom item lock(s)).");
        } catch (Exception e) {
            InsaneTweaksMod.LOGGER.error("[InsaneTweaks] Exception while deploying reskillable_optimized.cfg!", e);
        }
    }

    /**
     * Parses the Skill Locks multi-line block from an existing reskillable.cfg.
     *
     * Returns only user-defined non-empty lock lines, while skipping comments and
     * InsaneTweaks-managed item locks already present in the bundled template.
     */
    private static LockExtractionResult extractCustomLocks(File configFile) {
        Set<String> result = new LinkedHashSet<String>();

        try {
            List<String> lines = Files.readAllLines(configFile.toPath(), StandardCharsets.UTF_8);
            boolean inLocksBlock = false;
            boolean foundLocksBlock = false;
            boolean foundLocksTerminator = false;

            for (String line : lines) {
                String trimmed = line.trim();

                if (!inLocksBlock) {
                    if (trimmed.startsWith("S:\"Skill Locks\"") && trimmed.contains("<")) {
                        inLocksBlock = true;
                        foundLocksBlock = true;
                    }
                    continue;
                }

                if (trimmed.equals(">")) {
                    foundLocksTerminator = true;
                    break;
                }

                if (trimmed.isEmpty() || trimmed.startsWith("#") || trimmed.startsWith("insanetweaks:")) {
                    continue;
                }

                result.add(trimmed);
            }

            if (!foundLocksBlock) {
                InsaneTweaksMod.LOGGER.error(
                        "[InsaneTweaks] Existing reskillable.cfg is missing the Skill Locks block; refusing to overwrite it.");
                return LockExtractionResult.failure();
            }

            if (!foundLocksTerminator) {
                InsaneTweaksMod.LOGGER.error(
                        "[InsaneTweaks] Existing reskillable.cfg has an unterminated Skill Locks block; refusing to overwrite it.");
                return LockExtractionResult.failure();
            }

            if (!result.isEmpty()) {
                InsaneTweaksMod.LOGGER.info(
                        "[InsaneTweaks] Found {} custom item lock(s) in original reskillable.cfg; they will be preserved.",
                        result.size());
            }

            return LockExtractionResult.success(new ArrayList<String>(result));
        } catch (Exception e) {
            InsaneTweaksMod.LOGGER.error(
                    "[InsaneTweaks] Could not parse custom locks from original reskillable.cfg.", e);
            return LockExtractionResult.failure();
        }
    }

    /**
     * Injects preserved custom locks into the bundled template's Skill Locks block.
     */
    private static String mergeLocks(String template, List<String> customLocks) {
        if (customLocks.isEmpty()) {
            return template;
        }

        Set<String> uniqueLocks = new LinkedHashSet<String>(customLocks);
        String normalized = template.replace("\r\n", "\n").replace("\r", "\n");
        String[] lines = normalized.split("\n", -1);

        StringBuilder result = new StringBuilder();
        boolean inLocksBlock = false;
        boolean inserted = false;

        for (String line : lines) {
            String trimmed = line.trim();

            if (!inLocksBlock) {
                result.append(line).append("\n");
                if (trimmed.startsWith("S:\"Skill Locks\"") && trimmed.contains("<")) {
                    inLocksBlock = true;
                }
                continue;
            }

            if (!inserted && trimmed.equals(">")) {
                for (String lock : uniqueLocks) {
                    result.append("        ").append(lock).append("\n");
                }
                inserted = true;
                inLocksBlock = false;
            }

            result.append(line).append("\n");
        }

        if (!inserted) {
            throw new IllegalStateException(
                    "Bundled reskillable_optimized.cfg is missing the Skill Locks block terminator.");
        }

        return result.toString();
    }

    private static String readFlagVersion(File flagFile) {
        try {
            List<String> lines = Files.readAllLines(flagFile.toPath(), StandardCharsets.UTF_8);
            return lines.isEmpty() ? "" : lines.get(0).trim();
        } catch (Exception e) {
            return "";
        }
    }

    private static String resolveCurrentVersion() {
        Package pkg = ReskillableConfigSwapper.class.getPackage();
        if (pkg != null) {
            String implementationVersion = pkg.getImplementationVersion();
            if (implementationVersion != null && !implementationVersion.trim().isEmpty()) {
                return implementationVersion.trim();
            }
        }
        return InsaneTweaksMod.VERSION;
    }

    private static String createBackup(File configDir, File sourceConfig, String currentVersion) throws IOException {
        String timestamp = new SimpleDateFormat("yyyyMMdd-HHmmss").format(new Date());
        String versionTag = sanitizeFileComponent(currentVersion);
        String reason = wasVersionUpdate ? "update" : "install";
        String backupName = "reskillable." + reason + ".v" + versionTag + "." + timestamp + ".cfg";
        Files.copy(sourceConfig.toPath(), new File(configDir, backupName).toPath(),
                StandardCopyOption.REPLACE_EXISTING);
        return backupName;
    }

    private static String sanitizeFileComponent(String value) {
        return value.replaceAll("[^A-Za-z0-9._-]", "_");
    }

    private static void writeAtomically(Path target, byte[] bytes) throws IOException {
        Path parent = target.getParent();
        Path temp = Files.createTempFile(parent, "insanetweaks-reskillable-", ".tmp");

        try {
            Files.write(temp, bytes);
            try {
                Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException ignored) {
                Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temp);
        }
    }

    private static String readStream(InputStream in) throws IOException {
        byte[] buffer = new byte[4096];
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        int n;
        while ((n = in.read(buffer)) != -1) {
            bos.write(buffer, 0, n);
        }
        return bos.toString(StandardCharsets.UTF_8.name());
    }

    public static class LoginWarningHandler {

        @SubscribeEvent
        public void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
            if (!wasSwappedThisSession) {
                return;
            }

            event.player.sendMessage(
                    new TextComponentString(TextFormatting.DARK_RED + "========================================"));
            event.player.sendMessage(new TextComponentString(
                    TextFormatting.RED + "[Insane Tweaks] " + TextFormatting.YELLOW
                            + "Reskillable skill tree has been optimized!"));

            if (lastBackupFileName != null) {
                event.player.sendMessage(new TextComponentString(
                        TextFormatting.GRAY + "Backup created: " + TextFormatting.WHITE + "config/"
                                + lastBackupFileName));
            }

            if (preservedCustomLocksCount > 0) {
                event.player.sendMessage(new TextComponentString(
                        TextFormatting.GRAY + "Preserved custom item locks: " + TextFormatting.WHITE
                                + preservedCustomLocksCount));
            }
            event.player.sendMessage(new TextComponentString(
                    TextFormatting.GREEN + "New costs are already active - no restart required!"));
            event.player.sendMessage(
                    new TextComponentString(TextFormatting.DARK_RED + "========================================"));

            wasSwappedThisSession = false;
            preservedCustomLocksCount = 0;
        }
    }
}
