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
        if (Loader.isModLoaded("iceandfire")) {
            // Applied whenever Ice&Fire is present; the master switch lives in the config and is
            // enforced by the override table, which hands back Ice&Fire's own values when off.
            // Gating the queue on a config value would mean reading it before Forge has injected it.
            configs.add("mixins.srpwizcore.iceandfire.json");
        }
        if (Loader.isModLoaded("dldungeonsjbg")) {
            configs.add("mixins.srpwizcore.doomlike.json");
        }
        if (Loader.isModLoaded("cqrepoured")) {
            configs.add("mixins.srpwizcore.cqr.json");
            // Crossbow AI grafts CQR's IRangedWeapon onto Spartan's ItemCrossbow — needs both.
            if (Loader.isModLoaded("spartanweaponry")) {
                configs.add("mixins.srpwizcore.cqrspartan.json");
            }
        }
        if (Loader.isModLoaded("raids")) {
            configs.add("mixins.srpwizcore.raids.json");
        }
        if (Loader.isModLoaded("defiledlands")) {
            configs.add("mixins.srpwizcore.defiledlands.json");
        }
        // SetBonus reloads its server data from ANY mod's PostConfigChangedEvent, on the client
        // thread, while the server thread iterates the same set — CME in ServerBonus.updateBonuses.
        // See MixinSetBonusConfigReload. Client-only inside the config: the event needs a GUI.
        if (Loader.isModLoaded("setbonus")) {
            configs.add("mixins.srpwizcore.setbonus.json");
        }
        // The Non-Euclidean Cube searches for a teleport destination by recursing up to 10 000
        // deep on its own thread pool, where our chunk guard makes every probe miss — so the
        // search always ends in a StackOverflowError that kills the server tick.
        // See MixinElItemTheCube.
        if (Loader.isModLoaded("enigmaticlegacy")) {
            configs.add("mixins.srpwizcore.enigmatic.json");
        }
        return configs;
    }

    @Override
    public void onMixinConfigQueued(String mixinConfig) {
        com.spege.srpwizcore.SrpWizCore.LOGGER.info(
                "[srpwizcore] Queued late mixin config: {}", mixinConfig);
    }
}
