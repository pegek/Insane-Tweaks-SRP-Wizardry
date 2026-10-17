package com.spege.srpwizcore.core;

/**
 * Real application gate for {@code MixinIntCache} — see {@link ConfigFlagMixinPlugin} for why a
 * plugin is needed at all and why the flag is read from the file.
 *
 * <p>The first {@code shouldApplyMixin} fires when {@code IntCache} is classloaded, i.e. at
 * worldgen, long after Forge has written and synced the file. A missing file or missing key
 * means the mod's default: ON — this fix must not be lost to an unreadable config.
 */
public class IntCacheMixinPlugin extends ConfigFlagMixinPlugin {

    /** Must match {@code @Config.Name} on {@code ThreadingCompatCategory.fixIntCacheThreadLocal}. */
    private static final String FLAG_NAME = "Fix: IntCache Thread Local";

    public IntCacheMixinPlugin() {
        super(FLAG_NAME, true, "IntCache thread-local fix");
    }
}
