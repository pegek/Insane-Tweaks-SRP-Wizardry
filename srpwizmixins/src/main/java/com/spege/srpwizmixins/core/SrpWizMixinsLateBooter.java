package com.spege.srpwizmixins.core;

import java.util.ArrayList;
import java.util.List;

import net.minecraftforge.fml.common.Loader;
import zone.rong.mixinbooter.ILateMixinLoader;

@SuppressWarnings("deprecation")
@zone.rong.mixinbooter.MixinLoader
public class SrpWizMixinsLateBooter implements ILateMixinLoader {

    @Override
    public List<String> getMixinConfigs() {
        List<String> configs = new ArrayList<>();
        if (Loader.isModLoaded("srparasites")) {
            configs.add("mixins.srpwizmixins.json");
        }
        return configs;
    }

    @Override
    public void onMixinConfigQueued(String mixinConfig) {
        com.spege.srpwizmixins.SrpWizMixins.LOGGER.info(
                "[SRP&Wiz mixins] Queued late mixin config: {}", mixinConfig);
    }
}
