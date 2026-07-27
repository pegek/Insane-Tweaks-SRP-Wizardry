package com.spege.insanetweaks.core;


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
        if (net.minecraftforge.fml.common.Loader.isModLoaded("player_mana")) {
            configs.add("mixins.insanetweaks.playermana.json");
        }
        if (net.minecraftforge.fml.common.Loader.isModLoaded("tombstone")) {
            configs.add("mixins.insanetweaks.tombstone.json");
        }
        if (net.minecraftforge.fml.common.Loader.isModLoaded("srparasites")) {
            configs.add("mixins.insanetweaks.srpcontent.json");
        }
        // "Relief for the Damned" perk: softens the Ring of the Seven Curses penalties at the
        // instruction where Enigmatic Legacy reads its own constants. Gated on EL, NOT on
        // Tombstone — the mixins go transparent without Tombstone because TombstonePerkHelper
        // reports level 0, and that is also why they must never touch a Tombstone class.
        if (net.minecraftforge.fml.common.Loader.isModLoaded("enigmaticlegacy")) {
            configs.add("mixins.insanetweaks.enigmatic.json");
        }
        // Enchant quest-gate, third-party sources: mods that roll an enchantment straight out of
        // Enchantment.REGISTRY instead of going through any vanilla choke point, so the three early
        // mixins never see them. One config per mod so an absent mod costs nothing at prepare time.
        if (net.minecraftforge.fml.common.Loader.isModLoaded("gottschcore")) {
            configs.add("mixins.insanetweaks.treasure2.json");
        }
        // NOTE: lowercase. Infernal Mobs' mcmod.info says "InfernalMobs", but the @Mod annotation —
        // which is what Loader registers — says "infernalmobs". Same trap as SRP's srparasites.
        // Verified in the 1.4.13 log: the capitalised form silently never queued this config.
        if (net.minecraftforge.fml.common.Loader.isModLoaded("infernalmobs")) {
            configs.add("mixins.insanetweaks.infernalmobs.json");
        }
        if (net.minecraftforge.fml.common.Loader.isModLoaded("chancecubes")) {
            configs.add("mixins.insanetweaks.chancecubes.json");
        }
        return configs;
    }

    @Override
    public void onMixinConfigQueued(String mixinConfig) {
        LOGGER.info("[InsaneTweaks] Queued late mixin config: {}", mixinConfig);
    }
}
