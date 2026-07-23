package com.spege.insanetweaks.config;

import java.io.File;
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
    // WARNING: this literal must never appear in any @Config.Comment/@Config.Name string of the
    // NEW schema, or every launch would re-detect the "old" format and re-reset the config.
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
            if (backup.isFile()) {
                LOGGER.warn("[InsaneTweaks] Existing backup {} will be overwritten.", BACKUP_NAME);
            }
            // Atomic same-volume rename: no partial-failure window where a copy succeeds but the
            // delete fails and the game then loads a hybrid old+new config forever.
            Files.move(cfg.toPath(), backup.toPath(), StandardCopyOption.REPLACE_EXISTING);
            migrated = true;
            LOGGER.info("[InsaneTweaks] Config structure changed in {}; old settings were backed up to {} - re-apply any customizations.",
                    InsaneTweaksMod.VERSION, BACKUP_NAME);
        } catch (Exception e) {
            // Never fatal: worst case the old sections linger in the file as ignored junk.
            LOGGER.warn("[InsaneTweaks] Could not back up old-format config: {}", e.toString());
        }
    }
}
