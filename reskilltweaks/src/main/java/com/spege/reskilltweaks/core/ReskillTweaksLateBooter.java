package com.spege.reskilltweaks.core;

import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import zone.rong.mixinbooter.ILateMixinLoader;

/**
 * Queues this mod's single mixin config behind Reskillable's presence.
 *
 * <p>Lives in {@code core} rather than {@code mixins} on purpose: Mixin forbids
 * {@code Class.forName()} of a non-mixin class from inside a {@code *.mixins.*} package.
 *
 * <p>Both listed mixins target Reskillable classes, so the config is late and there is no early
 * route to worry about — which is also why this mod's jar manifest carries no {@code MixinConfigs}
 * attribute and therefore needs no coremod declaration. The lang half of {@code MixinLocale}, which
 * rewrites the two native trait descriptions in the vanilla {@code Locale} dictionary, deliberately
 * stayed in Insane Tweaks: it touches no Reskillable type at all (it only copies string values
 * between keys), so moving it here would have bought an early mixin config, a {@code FMLCorePlugin}
 * declaration and a {@code DuplicateModsFoundException} risk on clean Forge for nothing.
 *
 * <p>The config declares no {@code IMixinConfigPlugin}, so both mixins apply whenever Reskillable is
 * present — the config flags are early returns inside the handlers, not application gates.
 */
@SuppressWarnings("deprecation")
@zone.rong.mixinbooter.MixinLoader
public class ReskillTweaksLateBooter implements ILateMixinLoader {

    private static final Logger LOGGER = LogManager.getLogger("reskilltweaks-mixins");

    @Override
    public List<String> getMixinConfigs() {
        List<String> configs = new java.util.ArrayList<>();
        if (net.minecraftforge.fml.common.Loader.isModLoaded("reskillable")) {
            configs.add("mixins.reskilltweaks.reskillable.json");
        }
        return configs;
    }

    @Override
    public void onMixinConfigQueued(String mixinConfig) {
        LOGGER.info("[ReskillTweaks] Queued late mixin config: {}", mixinConfig);
    }
}
