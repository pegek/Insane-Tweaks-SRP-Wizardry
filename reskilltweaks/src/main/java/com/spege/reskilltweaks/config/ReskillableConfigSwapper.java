package com.spege.reskilltweaks.config;

import com.spege.reskilltweaks.ReskillTweaks;
import com.spege.reskilltweaks.config.categories.ModulesCategory;
import net.minecraft.util.text.TextComponentString;
import net.minecraftforge.common.config.Config;
import net.minecraftforge.common.config.ConfigManager;
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
 * Deploys the bundled optimized reskillable.cfg during preInit - if
 * {@link ModulesCategory#tunedReskillableConfig} allows it. The default, {@code ASK}, deploys
 * nothing until someone has chosen: the client prompt ({@code GuiReskillableConfigPrompt}) or the
 * config value. Replacing another mod's config unasked was fine while this only shipped inside one
 * modpack; it is not for a player who installed the mod for its traits and has tuned Reskillable
 * to their own taste.
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
    private static boolean promptPending = false;

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
        promptPending = false;

        if (!resolveMode(flagFile)) {
            return;
        }

        if (flagFile.exists() && reskillableConfig.exists()) {
            String deployedVersion = readFlagVersion(flagFile);
            if (currentVersion.equals(deployedVersion)) {
                return;
            }

            wasVersionUpdate = true;
            ReskillTweaks.LOGGER.info(
                    "[ReskillTweaks] Reskillable config version mismatch (deployed={}, current={}). Re-deploying...",
                    deployedVersion, currentVersion);
        }

        List<String> customLocks = new ArrayList<String>();
        if (reskillableConfig.exists()) {
            LockExtractionResult extraction = extractCustomLocks(reskillableConfig);
            if (!extraction.success) {
                ReskillTweaks.LOGGER.error(
                        "[ReskillTweaks] Aborting Reskillable config swap because custom lock extraction failed. Existing reskillable.cfg was left untouched.");
                return;
            }

            customLocks = extraction.locks;
            preservedCustomLocksCount = customLocks.size();

            try {
                lastBackupFileName = createBackup(configDir, reskillableConfig, currentVersion);
                ReskillTweaks.LOGGER.info("[ReskillTweaks] Backed up existing reskillable.cfg -> {}",
                        lastBackupFileName);
            } catch (IOException e) {
                ReskillTweaks.LOGGER.error(
                        "[ReskillTweaks] Failed to create a safety backup for reskillable.cfg. Aborting swap to avoid destructive overwrite.",
                        e);
                return;
            }
        }

        try (InputStream in = ReskillableConfigSwapper.class.getResourceAsStream(
                "/assets/reskilltweaks/reskillable_optimized.cfg")) {
            if (in == null) {
                ReskillTweaks.LOGGER.error(
                        "[ReskillTweaks] FATAL: Could not find bundled reskillable_optimized.cfg in jar resources.");
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

            ReskillTweaks.LOGGER.info("[ReskillTweaks] Successfully deployed optimized Reskillable config v{}{}",
                    currentVersion,
                    customLocks.isEmpty() ? "." : " (preserved " + customLocks.size() + " custom item lock(s)).");
        } catch (Exception e) {
            ReskillTweaks.LOGGER.error("[ReskillTweaks] Exception while deploying reskillable_optimized.cfg!", e);
        }
    }

    /**
     * Settles {@link TunedConfigMode} for this launch and answers whether the swap may go ahead.
     *
     * <p>{@code ASK} with the flag file already present means the tuned config was installed
     * before - by a version that had no choice to offer, or after an earlier {@code APPLY}. Asking
     * that install whether it wants what it already has would be noise, so the answer is written
     * down as {@code APPLY} and the swap proceeds (which still re-deploys on a version change,
     * exactly as it always did).
     */
    private static boolean resolveMode(File flagFile) {
        TunedConfigMode mode = ReskillTweaksConfig.modules.tunedReskillableConfig;
        if (mode == null) {
            mode = TunedConfigMode.ASK;
        }

        switch (mode) {
            case KEEP:
                ReskillTweaks.LOGGER.info(
                        "[ReskillTweaks] Tuned Reskillable Config = KEEP - reskillable.cfg left untouched.");
                return false;
            case APPLY:
                return true;
            case ASK:
            default:
                if (flagFile.exists()) {
                    ReskillTweaks.LOGGER.info(
                            "[ReskillTweaks] Tuned reskillable.cfg was installed before - recording "
                                    + "Tuned Reskillable Config = APPLY instead of asking.");
                    setMode(TunedConfigMode.APPLY);
                    return true;
                }
                promptPending = true;
                ReskillTweaks.LOGGER.info("[ReskillTweaks] ================================================");
                ReskillTweaks.LOGGER.info("[ReskillTweaks] reskillable.cfg was NOT replaced with this mod's tuned version:");
                ReskillTweaks.LOGGER.info("[ReskillTweaks] nobody has chosen yet. A client asks on the main menu. On a");
                ReskillTweaks.LOGGER.info("[ReskillTweaks] dedicated server, set modules > \"Tuned Reskillable Config\" in");
                ReskillTweaks.LOGGER.info("[ReskillTweaks] config/reskilltweaks.cfg to APPLY (or KEEP to stop this message)");
                ReskillTweaks.LOGGER.info("[ReskillTweaks] and restart.");
                ReskillTweaks.LOGGER.info("[ReskillTweaks] ================================================");
                return false;
        }
    }

    /** True when this launch skipped the swap because nobody has chosen yet - the prompt's cue. */
    public static boolean isPromptPending() {
        return promptPending;
    }

    /**
     * Records the player's answer to the prompt. Only writes the choice down: the file itself is
     * swapped at the next launch's preInit, because Reskillable has already read reskillable.cfg by
     * the time any screen can ask, and has no way to re-read its unlockable costs.
     */
    public static void choose(TunedConfigMode mode) {
        promptPending = false;
        setMode(mode);
        ReskillTweaks.LOGGER.info("[ReskillTweaks] Tuned Reskillable Config set to {} from the main menu{}.",
                mode, mode == TunedConfigMode.APPLY ? " - reskillable.cfg is replaced on the next launch" : "");
    }

    /** Sets the field and saves reskilltweaks.cfg - {@code ConfigManager.sync} writes fields to disk. */
    private static void setMode(TunedConfigMode mode) {
        ReskillTweaksConfig.modules.tunedReskillableConfig = mode;
        ConfigManager.sync(ReskillTweaks.MODID, Config.Type.INSTANCE);
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
                ReskillTweaks.LOGGER.error(
                        "[ReskillTweaks] Existing reskillable.cfg is missing the Skill Locks block; refusing to overwrite it.");
                return LockExtractionResult.failure();
            }

            if (!foundLocksTerminator) {
                ReskillTweaks.LOGGER.error(
                        "[ReskillTweaks] Existing reskillable.cfg has an unterminated Skill Locks block; refusing to overwrite it.");
                return LockExtractionResult.failure();
            }

            if (!result.isEmpty()) {
                ReskillTweaks.LOGGER.info(
                        "[ReskillTweaks] Found {} custom item lock(s) in original reskillable.cfg; they will be preserved.",
                        result.size());
            }

            return LockExtractionResult.success(new ArrayList<String>(result));
        } catch (Exception e) {
            ReskillTweaks.LOGGER.error(
                    "[ReskillTweaks] Could not parse custom locks from original reskillable.cfg.", e);
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
        return ReskillTweaks.VERSION;
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
        Path temp = Files.createTempFile(parent, "reskilltweaks-reskillable-", ".tmp");

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
                    TextFormatting.RED + "[ReskillTweaks] " + TextFormatting.YELLOW
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
