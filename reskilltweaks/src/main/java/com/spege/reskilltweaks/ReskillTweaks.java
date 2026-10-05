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
 * <h3>Everything but Reskillable is optional (1.2.0)</h3>
 * Electroblob's Wizardry, Scape and Run: Parasites and Insane Tweaks are all {@code after:}, never
 * {@code required-after:}. Each one's traits are simply left out of the skill tree when it is
 * absent ({@link com.spege.reskilltweaks.skills.SkillsModule}), and each one's code sits in a class
 * nothing loads unless the mod is there: {@link com.spege.reskilltweaks.skills.WizardrySkillsHandler},
 * {@link com.spege.reskilltweaks.skills.ScarredFleshHandler} and
 * {@link com.spege.reskilltweaks.skills.InsaneTweaksBridge}. Before 1.2.0 Insane Tweaks was
 * required, and since that mod itself requires Wizardry, Spartan Weaponry, Ancient Spellcraft and
 * SRP, wanting the traits meant installing the whole stack.
 *
 * <p>The Insane Tweaks link is still this repo's one deliberate exception to "extracted mods never
 * depend on content" - now an optional one. See CLAUDE.md and the comment in {@code build.gradle}.
 */
@Mod(modid = ReskillTweaks.MODID,
        name = ReskillTweaks.NAME,
        version = ReskillTweaks.VERSION,
        dependencies = "required-before:reskillable;after:insanetweaks;after:ebwizardry;after:srparasites",
        acceptableRemoteVersions = "*")
public class ReskillTweaks {

    public static final String MODID = "reskilltweaks";
    public static final String NAME = "Reskill Tweaks";
    public static final String VERSION = "1.2.0";

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

        // Each 'new X()' below goes straight into register(Object), so the verifier never loads X
        // while linking this class - the Loader check in front of it is enough on its own.
        if (Loader.isModLoaded(com.spege.reskilltweaks.skills.SkillsModule.WIZARDRY)) {
            MinecraftForge.EVENT_BUS.register(new com.spege.reskilltweaks.skills.WizardrySkillsHandler());
        }

        if (Loader.isModLoaded(com.spege.reskilltweaks.skills.SkillsModule.PARASITES)) {
            MinecraftForge.EVENT_BUS.register(new com.spege.reskilltweaks.skills.ScarredFleshHandler());
        }

        // Hand Insane Tweaks its answers for Coiled Spring and Assimilated Warfare. Until this
        // runs, TraitGate answers false for everything, which is what makes the core safe to ship
        // without this mod. 🚨 Through InsaneTweaksBridge, never inline - see that class for why an
        // inlined call breaks this class on a pack without Insane Tweaks despite the check.
        if (Loader.isModLoaded(com.spege.reskilltweaks.skills.SkillsModule.INSANE_TWEAKS)) {
            com.spege.reskilltweaks.skills.InsaneTweaksBridge.arm();
            LOGGER.info("[ReskillTweaks] Trait gate armed for Insane Tweaks (charge jump, parasite XP fallback).");
        }

        // 🚨 ReskillableGuiHandler carries a CLASS-level @SideOnly(Side.CLIENT), which makes
        // 'new ReskillableGuiHandler()' fatal on a dedicated server regardless of what the class
        // contains — Forge's SideTransformer throws at load. The side check is load-bearing.
        if (event.getSide() == Side.CLIENT
                && com.spege.reskilltweaks.config.ReskillTweaksConfig.modules.enableGuiRefundFallback) {
            MinecraftForge.EVENT_BUS.register(new com.spege.reskilltweaks.client.ReskillableGuiHandler());
        }

        // Same class-level @SideOnly(Side.CLIENT) as above, same load-bearing side check. Registered
        // unconditionally on the client: it does nothing unless preInit skipped the swap for want
        // of a choice.
        if (event.getSide() == Side.CLIENT) {
            MinecraftForge.EVENT_BUS.register(new com.spege.reskilltweaks.client.ReskillableConfigPromptHandler());
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
