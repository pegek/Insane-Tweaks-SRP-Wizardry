package com.spege.insanetweaks.mixins;

import zone.rong.mixinbooter.IEarlyMixinLoader;
import java.util.Collections;
import java.util.List;

@SuppressWarnings("deprecation")
@zone.rong.mixinbooter.MixinLoader
public class EarlyMixinBooter implements IEarlyMixinLoader {
    @Override
    public List<String> getMixinConfigs() {
        return Collections.singletonList("mixins.insanetweaks.early.json");
    }
}
