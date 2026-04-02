package com.spege.insanetweaks.mixins;


import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import zone.rong.mixinbooter.ILateMixinLoader;

@SuppressWarnings("deprecation")
@zone.rong.mixinbooter.MixinLoader
public class LateMixinBooter implements ILateMixinLoader {

    private static final Logger LOGGER = LogManager.getLogger("insanetweaks-mixins");

    @Override
    public List<String> getMixinConfigs() {
        List<String> configs = new java.util.ArrayList<>();
        configs.add("mixins.insanetweaks.late.json");
        return configs;
    }

    @Override
    public void onMixinConfigQueued(String mixinConfig) {
        LOGGER.info("[InsaneTweaks] Queued late mixin config: {}", mixinConfig);
    }
}
