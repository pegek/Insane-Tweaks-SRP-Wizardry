package com.spege.insanetweaks.config;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.spege.insanetweaks.InsaneTweaksMod;

import net.minecraftforge.fml.common.Loader;

/**
 * Detects a pre-rework insanetweaks.cfg (categories named "[ 1 ] ...") and moves it aside so
 * Forge regenerates a clean file. MUST run from the InsaneTweaksMod constructor: FML performs the
 * first ConfigManager.sync inside FMLModContainer.constructMod immediately AFTER the mod instance
 * is created, and Forge's Configuration.save would otherwise preserve the old junk sections.
 */
public final class OldConfigBackup {

    private static final Logger LOGGER = LogManager.getLogger(InsaneTweaksMod.MODID);
    private static final String OLD_STRUCTURE_MARKER = "\"[ 1 ]";
    private static final String BACKUP_NAME = "insanetweaks.cfg.pre-rework";

    /** Set when a backup happened this launch; read by ConfigResetNoticeHandler. */
    private static boolean migrated;

    private OldConfigBackup() {
    }

    public static boolean didMigrate() {
        return migrated;
    }

    public static void backupOldConfigIfPresent() {
        try {
            File configDir = Loader.instance().getConfigDir();
            if (configDir == null) {
                return;
            }
            File cfg = new File(configDir, "insanetweaks.cfg");
            if (!cfg.isFile()) {
                return;
            }

            String content = new String(Files.readAllBytes(cfg.toPath()), StandardCharsets.UTF_8);
            if (!content.contains(OLD_STRUCTURE_MARKER)) {
                return;
            }

            File backup = new File(configDir, BACKUP_NAME);
            Files.copy(cfg.toPath(), backup.toPath(), StandardCopyOption.REPLACE_EXISTING);
            Files.delete(cfg.toPath());
            migrated = true;
            LOGGER.info("[InsaneTweaks] Config structure changed in {}; old settings were backed up to {} - re-apply any customizations.",
                    InsaneTweaksMod.VERSION, BACKUP_NAME);
        } catch (IOException e) {
            // Never fatal: worst case the old sections linger in the file as ignored junk.
            LOGGER.warn("[InsaneTweaks] Could not back up old-format config: {}", e.toString());
        }
    }
}
