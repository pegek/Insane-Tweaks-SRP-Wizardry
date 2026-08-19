package com.spege.manacore.core;

import java.util.ArrayList;
import java.util.List;

import com.spege.manacore.ManaCoreMod;

import net.minecraftforge.fml.common.Loader;
import zone.rong.mixinbooter.ILateMixinLoader;

/**
 * Late-loading route for the EBW and TaB mixin configs.
 *
 * <p>Both {@code mixins.manacore.ebw.json} and {@code mixins.manacore.tab.json} target classes
 * belonging to optional mods (Electroblob's Wizardry and Trinkets and Baubles respectively), not
 * vanilla classes. A config targeting a mod class cannot use the early jar-manifest route (that
 * route resolves before FML has even decided which mods are present), so it must be queued
 * through {@link ILateMixinLoader} instead, gated on {@link Loader#isModLoaded(String)} for the
 * mod it targets - queuing an EBW mixin config on a server without EBW would throw at mixin
 * apply time, not just no-op.
 *
 * <p>This class deliberately lives in {@code com.spege.manacore.core}, not
 * {@code com.spege.manacore.mixins.*}: Mixin forbids {@code Class.forName()} of non-mixin classes
 * from inside a {@code *.mixins.*} package, and MixinBooter resolves {@link ILateMixinLoader}
 * implementations via {@code Class.forName()}.
 *
 * <p>{@link #shouldMixinConfigQueue(String)} logs on every call, including refusals. This is not
 * incidental - a silently declined config (mod absent) and a config that queued fine but whose
 * target class has not been loaded yet this session look identical in {@code cleanmix.log}
 * otherwise (no "Mixing"/"APPLY" line in either case), and those are two completely different
 * diagnoses. The queue-decision log line disambiguates them.
 */
@SuppressWarnings("deprecation")
@zone.rong.mixinbooter.MixinLoader
public class ManaCoreLateBooter implements ILateMixinLoader {

    private static final String EBW_CONFIG = "mixins.manacore.ebw.json";
    private static final String TAB_CONFIG = "mixins.manacore.tab.json";

    /** Electroblob's Wizardry modid. */
    private static final String EBW_MODID = "ebwizardry";
    /** Trinkets and Baubles modid. */
    private static final String TAB_MODID = "xat";

    @Override
    public List<String> getMixinConfigs() {
        List<String> configs = new ArrayList<String>();
        configs.add(EBW_CONFIG);
        configs.add(TAB_CONFIG);
        return configs;
    }

    @Override
    public boolean shouldMixinConfigQueue(String mixinConfig) {
        if (EBW_CONFIG.equals(mixinConfig)) {
            boolean present = Loader.isModLoaded(EBW_MODID);
            ManaCoreMod.LOGGER.info("[ManaCore] EBW mixins queue = {}", Boolean.valueOf(present));
            return present;
        }
        if (TAB_CONFIG.equals(mixinConfig)) {
            boolean present = Loader.isModLoaded(TAB_MODID);
            ManaCoreMod.LOGGER.info("[ManaCore] TaB mixins queue = {}", Boolean.valueOf(present));
            return present;
        }
        return false;
    }
}
