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
        // Split out of late.json on purpose: late.json is required:false, so a binding failure there
        // (e.g. EBW renaming getImbuementResult) silently drops the mixin with a log line instead of
        // throwing. That was tolerable for every other entry, but this guard is the one mixin whose
        // silent absence is itself a crash - the imbuement altar casting AIR to IManaStoringItem, both
        // on the server and, since 09223a2 removed the JEI redirect that used to keep Abomination out
        // of generateArmourRecipes, on every client during JEI recipe registration too. required:true
        // turns a future rename into a loud mixin-apply error naming this mod, not a
        // ClassCastException surfacing somewhere else. Content declares required-after:ebwizardry, so
        // no Loader.isModLoaded gate is needed here.
        configs.add("mixins.insanetweaks.altarguard.json");
        if (net.minecraftforge.fml.common.Loader.isModLoaded("player_mana")) {
            configs.add("mixins.insanetweaks.playermana.json");
        }
        if (net.minecraftforge.fml.common.Loader.isModLoaded("srparasites")) {
            configs.add("mixins.insanetweaks.srpcontent.json");
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
        // EBW's JEI integration builds its ingredient stacks from Element.values() directly, so the
        // getSubItems redirects never reach it. Targets exist only with JEI installed, hence a config
        // of its own instead of the unconditional late one. The id is "jei" for BOTH JEI 4.16 and
        // HadEnoughItems 4.34 (the pack's fork) - verified on the @Mod annotation in each jar, not on
        // mcmod.info.
        if (net.minecraftforge.fml.common.Loader.isModLoaded("jei")) {
            configs.add("mixins.insanetweaks.jei.json");
        }
        return configs;
    }

    @Override
    public void onMixinConfigQueued(String mixinConfig) {
        LOGGER.info("[InsaneTweaks] Queued late mixin config: {}", mixinConfig);
    }
}
