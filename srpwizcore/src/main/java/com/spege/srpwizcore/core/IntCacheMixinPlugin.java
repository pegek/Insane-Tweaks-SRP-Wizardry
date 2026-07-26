package com.spege.srpwizcore.core;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.util.List;
import java.util.Set;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

/**
 * Real application gate for {@code MixinIntCache}.
 *
 * <p>A config flag on its own is NOT a mixin gate. Without a {@code plugin} entry in the mixin
 * config, every listed mixin is applied unconditionally and the flag is only an early-return
 * inside the injected handler — so anything that can go wrong at <em>application</em> time
 * (a {@code VerifyError}, a missing injection point) happens whether the flag is on or off, and
 * "requires MC restart" in the config comment is a fiction. {@code shouldApplyMixin} is what
 * makes the toggle real.
 *
 * <p>The flag is read from the config <em>file</em>, not from
 * {@code SrpWizCoreConfig.threadingCompat}. Mixin plugins run inside the transformer, where
 * touching the Forge config classes would drag them (and our category classes) through the
 * classloader at the wrong time, and where {@code ConfigManager} has not necessarily populated
 * anything yet. Parsing one line out of {@code config/srpwizcore.cfg} has no such ordering
 * hazard. The {@code @Config} field still owns the key: Forge is what writes it, with its
 * comment, into that file.
 *
 * <p>The read is lazy — {@code shouldApplyMixin} first fires when {@code IntCache} is
 * classloaded, i.e. at worldgen, long after Forge has written and synced the file. A missing
 * file or missing key means the mod's default: ON.
 */
public class IntCacheMixinPlugin implements IMixinConfigPlugin {

    /** Must match {@code @Config.Name} on {@code ThreadingCompatCategory.fixIntCacheThreadLocal}. */
    private static final String FLAG_NAME = "Fix: IntCache Thread Local";

    private static final String CONFIG_PATH = "config/srpwizcore.cfg";

    private final Logger logger = LogManager.getLogger("srpwizcore");

    private Boolean enabled;

    @Override
    public void onLoad(String mixinPackage) {
        // Deliberately empty: the config file may not exist yet this early.
    }

    @Override
    public String getRefMapperConfig() {
        // No refmaps in this project (-proc:none); matching is by explicit SRG + remap = false.
        return null;
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        if (this.enabled == null) {
            this.enabled = Boolean.valueOf(readFlag());
            this.logger.info("[srpwizcore] IntCache thread-local fix {} (config key \"{}\")",
                    this.enabled.booleanValue() ? "ENABLED" : "DISABLED", FLAG_NAME);
        }
        return this.enabled.booleanValue();
    }

    @Override
    public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {
        // no-op
    }

    @Override
    public List<String> getMixins() {
        // null = use the "mixins" list from the JSON as-is.
        return null;
    }

    @Override
    public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName,
            IMixinInfo mixinInfo) {
        // no-op
    }

    @Override
    public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName,
            IMixinInfo mixinInfo) {
        // no-op
    }

    /**
     * Minimal reader for the one boolean we need. Scans for the Forge property line
     * {@code B:"Fix: IntCache Thread Local"=true} anywhere in the file — the key is unique, so
     * there is no need to track category nesting. Any failure falls back to the default (ON):
     * this fix must not be lost to an unreadable config.
     */
    private boolean readFlag() {
        File config = locateConfig();
        if (config == null || !config.isFile()) {
            return true;
        }
        BufferedReader reader = null;
        try {
            reader = new BufferedReader(new InputStreamReader(new FileInputStream(config),
                    Charset.forName("UTF-8")));
            String line;
            while ((line = reader.readLine()) != null) {
                String trimmed = line.trim();
                if (!trimmed.startsWith("B:")) {
                    continue;
                }
                int equals = trimmed.indexOf('=');
                if (equals < 0) {
                    continue;
                }
                String name = trimmed.substring(2, equals).trim();
                if (name.length() >= 2 && name.charAt(0) == '"'
                        && name.charAt(name.length() - 1) == '"') {
                    name = name.substring(1, name.length() - 1);
                }
                if (FLAG_NAME.equals(name)) {
                    return Boolean.parseBoolean(trimmed.substring(equals + 1).trim());
                }
            }
        } catch (Throwable unreadable) {
            this.logger.warn("[srpwizcore] could not read {} for the IntCache gate; "
                    + "defaulting to ON", CONFIG_PATH, unreadable);
        } finally {
            if (reader != null) {
                try {
                    reader.close();
                } catch (Throwable ignored) {
                    // nothing useful to do here
                }
            }
        }
        return true;
    }

    /** Working directory first (that is the game dir under every launcher used here). */
    private static File locateConfig() {
        File direct = new File(CONFIG_PATH);
        if (direct.isFile()) {
            return direct;
        }
        try {
            Class<?> launch = Class.forName("net.minecraft.launchwrapper.Launch");
            Object home = launch.getField("minecraftHome").get(null);
            if (home instanceof File) {
                File viaLaunch = new File((File) home, CONFIG_PATH);
                if (viaLaunch.isFile()) {
                    return viaLaunch;
                }
            }
        } catch (Throwable noLaunchWrapper) {
            // Cleanroom may not expose LaunchWrapper; the working directory is the fallback.
        }
        return direct;
    }
}
