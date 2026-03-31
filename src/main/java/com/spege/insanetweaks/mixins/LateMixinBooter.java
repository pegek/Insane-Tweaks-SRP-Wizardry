package com.spege.insanetweaks.mixins;

import java.util.Collections;
import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import zone.rong.mixinbooter.ILateMixinLoader;

@zone.rong.mixinbooter.MixinLoader
public class LateMixinBooter implements ILateMixinLoader {

    private static final Logger LOGGER = LogManager.getLogger("insanetweaks-mixins");

    @Override
    public List<String> getMixinConfigs() {
        return Collections.singletonList("mixins.insanetweaks.late.json");
    }

    @Override
    public void onMixinConfigQueued(String mixinConfig) {
        LOGGER.info("[InsaneTweaks] Queued late mixin config: {}", mixinConfig);
    }
}
