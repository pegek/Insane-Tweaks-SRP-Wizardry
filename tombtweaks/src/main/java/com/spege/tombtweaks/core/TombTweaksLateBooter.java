package com.spege.tombtweaks.core;

import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import zone.rong.mixinbooter.ILateMixinLoader;

/**
 * Queues this mod's two mixin configs, each behind the mod it targets.
 *
 * <p>Lives in {@code core} rather than {@code mixins} on purpose: Mixin forbids
 * {@code Class.forName()} of a non-mixin class from inside a {@code *.mixins.*} package.
 *
 * <p>Both configs are late (mod-class targets). Neither declares an {@code IMixinConfigPlugin}, so
 * every listed mixin applies whenever its mod is present — the config flags are early returns
 * inside the handlers, not application gates.
 */
@SuppressWarnings("deprecation")
@zone.rong.mixinbooter.MixinLoader
public class TombTweaksLateBooter implements ILateMixinLoader {

    private static final Logger LOGGER = LogManager.getLogger("tombtweaks-mixins");

    @Override
    public List<String> getMixinConfigs() {
        List<String> configs = new java.util.ArrayList<>();
        if (net.minecraftforge.fml.common.Loader.isModLoaded("tombstone")) {
            configs.add("mixins.tombtweaks.tombstone.json");
        }
        // "Relief for the Damned" perk: softens the Ring of the Seven Curses penalties at the
        // instruction where Enigmatic Legacy reads its own constants. Gated on EL, NOT on
        // Tombstone — the mixins go transparent without Tombstone because TombstonePerkHelper
        // reports level 0, and that is also why they must never touch a Tombstone class.
        if (net.minecraftforge.fml.common.Loader.isModLoaded("enigmaticlegacy")) {
            configs.add("mixins.tombtweaks.enigmatic.json");
        }
        return configs;
    }

    @Override
    public void onMixinConfigQueued(String mixinConfig) {
        LOGGER.info("[TombstoneTweaks] Queued late mixin config: {}", mixinConfig);
    }
}
