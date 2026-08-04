package com.spege.enchanteraser.core;

import java.util.ArrayList;
import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import net.minecraftforge.fml.common.Loader;
import zone.rong.mixinbooter.ILateMixinLoader;

/**
 * Queues the one mod-gated mixin config. The four vanilla-target mixins do NOT come through here —
 * they are declared in the jar manifest's {@code MixinConfigs} attribute, which is the only early
 * route that works (CleanMix silently ignores {@code IEarlyMixinLoader}).
 *
 * <p>Lives in {@code core} rather than {@code mixins} on purpose: Mixin forbids
 * {@code Class.forName()} of a non-mixin class from inside a {@code *.mixins.*} package.
 */
@SuppressWarnings("deprecation")
@zone.rong.mixinbooter.MixinLoader
public class EnchantEraserLateBooter implements ILateMixinLoader {

    private static final Logger LOGGER = LogManager.getLogger("enchanteraser-mixins");

    @Override
    public List<String> getMixinConfigs() {
        List<String> configs = new ArrayList<String>();
        // 🚨 lowercase. Infernal Mobs' mcmod.info says "InfernalMobs", but the @Mod annotation — which
        // is what Loader registers — says "infernalmobs". The capitalised form silently never queues.
        if (Loader.isModLoaded("infernalmobs")) {
            configs.add("mixins.enchanteraser.infernalmobs.json");
        }
        return configs;
    }

    @Override
    public void onMixinConfigQueued(String mixinConfig) {
        LOGGER.info("[EnchantEraser] Queued late mixin config: {}", mixinConfig);
    }
}
