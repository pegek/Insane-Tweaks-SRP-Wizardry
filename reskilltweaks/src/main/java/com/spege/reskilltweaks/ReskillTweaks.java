package com.spege.reskilltweaks;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.relauncher.Side;

/**
 * Reskill Tweaks — the Reskillable integration that used to live inside Insane Tweaks, split out so
 * that mod no longer needs Reskillable to boot (fifth extraction, after {@code srpwizmixins},
 * {@code srpwizcore}, {@code tombtweaks} and {@code enchanteraser}).
 *
 * <h3>Why the split had to be a jar boundary</h3>
 * On a server without Reskillable (2026-08-06) Insane Tweaks did not report a missing dependency —
 * it died a minute into loading with {@code TypeNotPresentException} on
 * {@code codersafterdark.reskillable.api.unlockable.Unlockable}, thrown out of
 * {@code SkillsModule$RegistryHandler.registerUnlockables}. The type stood in that method's
 * <em>signature</em>, and {@code @Mod.EventBusSubscriber} registers the class whatever the body
 * says, so the {@code Loader.isModLoaded("reskillable")} sitting inside it never got a turn. A type
 * in a header cannot be guarded from inside the method; only a jar that isn't installed can.
 *
 * <h3>Load order</h3>
 * {@code required-BEFORE:reskillable}, not {@code after}. {@link
 * com.spege.reskilltweaks.config.ReskillableConfigSwapper} replaces Reskillable's config
 * <em>before Reskillable reads it</em>, so the ordering is part of the contract, not cosmetics —
 * flipping it would break the swap silently, with nothing in the log. Syntax verified against
 * AkashicTome, Quark and unseens-nether-backport, which all use {@code required-before:}.
 *
 * <p>{@code required-after:insanetweaks} because {@link com.spege.reskilltweaks.skills.TraitGateProvider}
 * implements an interface from it. That dependency is this repo's one deliberate exception to
 * "extracted mods never depend on content" — see CLAUDE.md and the comment in {@code build.gradle}.
 */
@Mod(modid = ReskillTweaks.MODID,
        name = ReskillTweaks.NAME,
        version = ReskillTweaks.VERSION,
        dependencies = "required-after:insanetweaks;required-after:ebwizardry;required-before:reskillable;after:srparasites",
        acceptableRemoteVersions = "*")
public class ReskillTweaks {

    public static final String MODID = "reskilltweaks";
    public static final String NAME = "Reskill Tweaks";
    public static final String VERSION = "1.0.0";

    public static final Logger LOGGER = LogManager.getLogger(MODID);

    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        // Must happen in preInit and nowhere later: this rewrites reskillable.cfg, and Reskillable
        // reads it during its own construction. See the class javadoc on load order.
        if (com.spege.reskilltweaks.config.ReskillTweaksConfig.modules.enableSkillsModule) {
            com.spege.reskilltweaks.config.ReskillableConfigSwapper.processConfig(event);
        }
    }

    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {
        if (!com.spege.reskilltweaks.config.ReskillTweaksConfig.modules.enableSkillsModule) {
            LOGGER.info("[ReskillTweaks] Skills module is off — no traits registered, no handlers bound.");
            return;
        }

        // Reskillable's presence is guaranteed by required-before:reskillable in @Mod, so none of
        // these need a Loader check of their own.
        MinecraftForge.EVENT_BUS.register(new com.spege.reskilltweaks.skills.EventHandlerSkills());
        MinecraftForge.EVENT_BUS.register(new com.spege.reskilltweaks.skills.AdaptedVegetationSkill());
        MinecraftForge.EVENT_BUS.register(new com.spege.reskilltweaks.skills.StoneFistsHandler());

        if (Loader.isModLoaded("srparasites")) {
            MinecraftForge.EVENT_BUS.register(new com.spege.reskilltweaks.skills.ScarredFleshHandler());
        }

        // Hand Insane Tweaks its answers for Coiled Spring and Assimilated Warfare. Until this
        // runs, TraitGate answers false for everything, which is what makes the core safe to ship
        // without this mod.
        com.spege.insanetweaks.api.TraitGate.setProvider(new com.spege.reskilltweaks.skills.TraitGateProvider());
        LOGGER.info("[ReskillTweaks] Trait gate armed for Insane Tweaks (charge jump, parasite XP fallback).");

        // 🚨 ReskillableGuiHandler carries a CLASS-level @SideOnly(Side.CLIENT), which makes
        // 'new ReskillableGuiHandler()' fatal on a dedicated server regardless of what the class
        // contains — Forge's SideTransformer throws at load. The side check is load-bearing.
        if (event.getSide() == Side.CLIENT
                && com.spege.reskilltweaks.config.ReskillTweaksConfig.modules.enableGuiRefundFallback) {
            MinecraftForge.EVENT_BUS.register(new com.spege.reskilltweaks.client.ReskillableGuiHandler());
        }

        LOGGER.info("[ReskillTweaks] Reskillable traits module enabled.");
    }

    /**
     * Cross-mod reaching-in goes here, not in init: postInit is the first phase where every other
     * mod has finished its own init, so foreign singletons are fully built. {@code EffectTwistPairs}
     * needs both Reskillable's unlockable registry and every mod's potion registry populated, and
     * it re-checks the module flag itself.
     */
    @Mod.EventHandler
    public void postInit(net.minecraftforge.fml.common.event.FMLPostInitializationEvent event) {
        com.spege.reskilltweaks.skills.EffectTwistPairs.install();
    }
}
