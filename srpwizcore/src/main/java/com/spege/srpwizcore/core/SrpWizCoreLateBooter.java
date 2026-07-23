package com.spege.srpwizcore.core;

import java.util.ArrayList;
import java.util.List;

import net.minecraftforge.fml.common.Loader;
import zone.rong.mixinbooter.ILateMixinLoader;

@SuppressWarnings("deprecation")
@zone.rong.mixinbooter.MixinLoader
public class SrpWizCoreLateBooter implements ILateMixinLoader {

    @Override
    public List<String> getMixinConfigs() {
        List<String> configs = new ArrayList<>();
        if (Loader.isModLoaded("openterraingenerator")) {
            configs.add("mixins.srpwizcore.otg.json");
        }
        if (Loader.isModLoaded("futuremc")) {
            configs.add("mixins.srpwizcore.futuremc.json");
        }
        return configs;
    }

    @Override
    public void onMixinConfigQueued(String mixinConfig) {
        com.spege.srpwizcore.SrpWizCore.LOGGER.info(
                "[srpwizcore] Queued late mixin config: {}", mixinConfig);
    }
}
