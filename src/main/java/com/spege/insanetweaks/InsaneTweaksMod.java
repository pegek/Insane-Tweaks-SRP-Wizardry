package com.spege.insanetweaks;

import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.ModContainer;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.common.event.FMLServerStartingEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraft.util.text.Style;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.util.text.event.ClickEvent;
import net.minecraftforge.fml.common.gameevent.PlayerEvent;
import com.spege.insanetweaks.events.LivingDeathEventHandler;
import com.spege.insanetweaks.commands.CommandBackupCursed;
import net.minecraftforge.fml.client.registry.RenderingRegistry;
import net.minecraftforge.fml.client.registry.IRenderFactory;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.Render;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.client.renderer.entity.RenderSnowball;
import net.minecraft.init.Items;
import net.minecraftforge.fml.common.registry.EntityRegistry;
import net.minecraft.util.ResourceLocation;
import com.spege.insanetweaks.entities.EntityFerCowMinion;
import com.spege.insanetweaks.entities.EntityRupterMinion;
import com.spege.insanetweaks.entities.EntitySummonerVomitCloud;
import com.spege.insanetweaks.entities.EntityPrimitiveSummonerMinion;
import com.spege.insanetweaks.entities.EntityPrimitiveYelloweyeMinion;
import com.spege.insanetweaks.entities.EntityPurifyingWave;
import com.spege.insanetweaks.entities.projectile.EntityYelloweyeNade;
import com.spege.insanetweaks.entities.projectile.EntityYelloweyeNadeProjectile;
import com.spege.insanetweaks.entities.projectile.EntityYelloweyeSpineball;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.maven.artifact.versioning.DefaultArtifactVersion;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Mod(modid = InsaneTweaksMod.MODID, name = InsaneTweaksMod.NAME, version = InsaneTweaksMod.VERSION, dependencies = "required-after:forge@[14.23.5.2860,);after:somanyenchantments;required-after:ebwizardry;required-after:spartanweaponry;required-after:ancientspellcraft;required-after:swparasites;required-after:srparasites;"
        +
        "after:srpextra;after:baubles;after:potioncore;before:reskillable")
public class InsaneTweaksMod {
    public static final String MODID = "insanetweaks";
    public static final String NAME = "Insane Tweaks";
    public static final String VERSION = "1.0.15";

    public static final Logger LOGGER = LogManager.getLogger(MODID);

    // Set in preInit after version check; consumed in init.
    private static boolean warnSrparasitesOldVersion = false;
    private static boolean wantsBaubleFruitsWarning = false;
    private static boolean wantsSkillsModuleWarning = false;

    // -----------------------------------------------------------------------
    // CurseForge URLs — update if any link changes
    // -----------------------------------------------------------------------
    private static final String URL_BAUBLES_EX = "https://www.curseforge.com/minecraft/mc-mods/baublesex";
    private static final String URL_SRPEXTRA = "https://www.curseforge.com/minecraft/mc-mods/scape-and-run-parasites-extra";
    private static final String URL_RESKILLABLE = "https://www.curseforge.com/minecraft/mc-mods/reskillable-fork";
    private static final String URL_SOME_ENCHANTMENTS = "https://www.curseforge.com/minecraft/mc-mods/so-many-enchantments";
    private static final String URL_POTIONCORE = "https://www.curseforge.com/minecraft/mc-mods/potion-core";

    // -------------------------------------------------------------------------
    // preInit
    // -------------------------------------------------------------------------

    @Mod.EventHandler
    @SuppressWarnings("null")
    public void preInit(FMLPreInitializationEvent event) {
        
        // Print compatibility report to log and set version flags.
        logCompatibilityReport();

        if (event.getSide() == net.minecraftforge.fml.relauncher.Side.CLIENT) {
            RenderingRegistry.registerEntityRenderingHandler(EntityFerCowMinion.class,
                    new IRenderFactory<EntityFerCowMinion>() {
                        @Override
                        public Render<? super EntityFerCowMinion> createRenderFor(RenderManager manager) {
                            return new RenderFerCowMinion(manager);
                        }
                    });
            RenderingRegistry.registerEntityRenderingHandler(EntityPrimitiveYelloweyeMinion.class,
                    new IRenderFactory<EntityPrimitiveYelloweyeMinion>() {
                        @Override
                        public Render<? super EntityPrimitiveYelloweyeMinion> createRenderFor(RenderManager manager) {
                            return new RenderPrimitiveYelloweyeMinion(manager);
                        }
                    });
            RenderingRegistry.registerEntityRenderingHandler(EntityPrimitiveSummonerMinion.class,
                    new IRenderFactory<EntityPrimitiveSummonerMinion>() {
                        @Override
                        public Render<? super EntityPrimitiveSummonerMinion> createRenderFor(RenderManager manager) {
                            return new RenderPrimitiveSummonerMinion(manager);
                        }
                    });
            RenderingRegistry.registerEntityRenderingHandler(EntityRupterMinion.class,
                    new IRenderFactory<EntityRupterMinion>() {
                        @Override
                        public Render<? super EntityRupterMinion> createRenderFor(RenderManager manager) {
                            return new RenderRupterMinion(manager);
                        }
                    });
            RenderingRegistry.registerEntityRenderingHandler(EntityYelloweyeSpineball.class,
                    new IRenderFactory<EntityYelloweyeSpineball>() {
                        @Override
                        public Render<? super EntityYelloweyeSpineball> createRenderFor(RenderManager manager) {
                            return new RenderSnowball<EntityYelloweyeSpineball>(manager,
                                    Objects.requireNonNull(Items.SLIME_BALL),
                                    Minecraft.getMinecraft().getRenderItem());
                        }
                    });
            RenderingRegistry.registerEntityRenderingHandler(EntityYelloweyeNadeProjectile.class,
                    new IRenderFactory<EntityYelloweyeNadeProjectile>() {
                        @Override
                        public Render<? super EntityYelloweyeNadeProjectile> createRenderFor(RenderManager manager) {
                            return new RenderYelloweyeNadeProjectile(manager);
                        }
                    });
            RenderingRegistry.registerEntityRenderingHandler(EntityYelloweyeNade.class,
                    new IRenderFactory<EntityYelloweyeNade>() {
                        @Override
                        public Render<? super EntityYelloweyeNade> createRenderFor(RenderManager manager) {
                            return new RenderYelloweyeNade(manager);
                        }
                    });
        }

        // Auto-detect Baubles; disable Bauble Fruits only if totally missing.
        boolean hasBaubles = Loader.isModLoaded("baubles");
        boolean isBaublesEx = hasBaubles && com.spege.insanetweaks.init.ModItems.isBaublesExPresent();
        if (com.spege.insanetweaks.config.ModConfig.modules.enableBaubleFruits) {
            if (!hasBaubles) {
                LOGGER.info("[InsaneTweaks] Baubles missing entirely. Automatically disabling Bauble Fruits.");
                wantsBaubleFruitsWarning = true;
                com.spege.insanetweaks.config.ModConfig.modules.enableBaubleFruits = false;
            } else if (!isBaublesEx) {
                wantsBaubleFruitsWarning = true;
            }
        }

        boolean wantsSkillsModule = com.spege.insanetweaks.config.ModConfig.modules.enableSkillsModule;

        // Auto-detect Reskillable. CompatSkills itself is optional here; we only keep
        // its domain string for save/config compatibility.
        if (!Loader.isModLoaded("reskillable")) {
            if (wantsSkillsModule) {
                LOGGER.info(
                        "[InsaneTweaks] Reskillable missing. Automatically disabling Skills Module.");
                wantsSkillsModuleWarning = true;
                com.spege.insanetweaks.config.ModConfig.modules.enableSkillsModule = false;
            }
        } else {
            // ONLY execute config swap if the module is manually enabled
            if (wantsSkillsModule) {
                com.spege.insanetweaks.config.ReskillableConfigSwapper.processConfig(event);
            }
        }
    }

    // -------------------------------------------------------------------------
    // init
    // -------------------------------------------------------------------------

    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {
        // Register Internal Entities
        // Other entities if any...
        EntityRegistry.registerModEntity(new ResourceLocation(MODID, "fer_cow_minion"),
                EntityFerCowMinion.class, "fer_cow_minion", 100, this, 64, 3, true);
        EntityRegistry.registerModEntity(new ResourceLocation(MODID, "primitive_yelloweye_minion"),
                EntityPrimitiveYelloweyeMinion.class, "primitive_yelloweye_minion", 101, this, 64, 3, true);
        EntityRegistry.registerModEntity(new ResourceLocation(MODID, "primitive_summoner_minion"),
                EntityPrimitiveSummonerMinion.class, "primitive_summoner_minion", 105, this, 64, 3, true);
        EntityRegistry.registerModEntity(new ResourceLocation(MODID, "rupter_minion"),
                EntityRupterMinion.class, "rupter_minion", 106, this, 64, 3, true);
        EntityRegistry.registerModEntity(new ResourceLocation(MODID, "summoner_vomit_cloud"),
                EntitySummonerVomitCloud.class, "summoner_vomit_cloud", 107, this, 64, 10, true);
        EntityRegistry.registerModEntity(new ResourceLocation(MODID, "purifying_wave"),
                EntityPurifyingWave.class, "purifying_wave", 108, this, 64, 10, true);
        EntityRegistry.registerModEntity(new ResourceLocation(MODID, "legendary_item"),
                com.spege.insanetweaks.entities.EntityItemIndestructible.class, "legendary_item", 109, this, 64, 20, true);
        EntityRegistry.registerModEntity(new ResourceLocation(MODID, "yelloweye_spineball"),
                EntityYelloweyeSpineball.class, "yelloweye_spineball", 102, this, 64, 10, true);
        EntityRegistry.registerModEntity(new ResourceLocation(MODID, "yelloweye_nade_projectile"),
                EntityYelloweyeNadeProjectile.class, "yelloweye_nade_projectile", 103, this, 64, 10, true);
        EntityRegistry.registerModEntity(new ResourceLocation(MODID, "yelloweye_nade"),
                EntityYelloweyeNade.class, "yelloweye_nade", 104, this, 64, 10, true);


        // Immediately grant fire/explosion immunity to all Living and Sentient item drops
        // on the tick they join the world, before any explosion can hit them.
        MinecraftForge.EVENT_BUS.register(new com.spege.insanetweaks.events.IndestructibleDropHandler());

        if (com.spege.insanetweaks.config.ModConfig.tweaks.enableCurseOfPossessionPatch) {
            MinecraftForge.EVENT_BUS.register(new LivingDeathEventHandler());
        }

        if (com.spege.insanetweaks.config.ModConfig.modules.enableCustomCores) {
            MinecraftForge.EVENT_BUS.register(new com.spege.insanetweaks.events.CustomCoresEventHandler());
            MinecraftForge.EVENT_BUS.register(new com.spege.insanetweaks.events.CoreTooltipHandler());
        }

        if (com.spege.insanetweaks.config.ModConfig.modules.enableSrpEbWizardryBridge) {
            MinecraftForge.EVENT_BUS.register(new com.spege.insanetweaks.events.SpellbladeHitHandler());
            MinecraftForge.EVENT_BUS.register(new com.spege.insanetweaks.events.WandEventHandler());
            MinecraftForge.EVENT_BUS.register(new com.spege.insanetweaks.events.SpellbladeTooltipHandler());
            MinecraftForge.EVENT_BUS.register(new com.spege.insanetweaks.events.AttackSpeedDebugHandler());
            MinecraftForge.EVENT_BUS.register(new com.spege.insanetweaks.events.ArmorEventHandler());
            MinecraftForge.EVENT_BUS.register(new com.spege.insanetweaks.events.ArmorTooltipHandler());
            MinecraftForge.EVENT_BUS.register(new com.spege.insanetweaks.events.AegisEventHandler());
            MinecraftForge.EVENT_BUS.register(new com.spege.insanetweaks.events.AegisTooltipHandler());
            MinecraftForge.EVENT_BUS.register(new com.spege.insanetweaks.events.WandTooltipHandler());
            MinecraftForge.EVENT_BUS.register(com.spege.insanetweaks.baubles.ItemInfernalCrownArtefact.class);
            if (event.getSide() == net.minecraftforge.fml.relauncher.Side.CLIENT) {
                MinecraftForge.EVENT_BUS.register(new com.spege.insanetweaks.events.SpellbladeSoundHandler());
            }
        }

        // GoldenBook is independent of the SRP/EBWizardry bridge — register
        // unconditionally.
        MinecraftForge.EVENT_BUS.register(new com.spege.insanetweaks.events.GoldenBookEventHandler());
        if (event.getSide() == net.minecraftforge.fml.relauncher.Side.CLIENT) {
            MinecraftForge.EVENT_BUS.register(new com.spege.insanetweaks.events.SpellItemTooltipHandler());
            MinecraftForge.EVENT_BUS.register(new com.spege.insanetweaks.events.SpellBookGuiHandler());
        }

        // --- Build the list of recommended mods that are missing / need updating ---
        boolean hasBaubles = Loader.isModLoaded("baubles");
        boolean isBaublesEx = hasBaubles && com.spege.insanetweaks.init.ModItems.isBaublesExPresent();
        boolean hasSrpExtra = Loader.isModLoaded("srpextra");
        boolean isSrpExtraValid = true;
        if (hasSrpExtra && warnSrparasitesOldVersion) {
            ModContainer srpExtraMod = Loader.instance().getIndexedModList().get("srpextra");
            if (srpExtraMod != null && !srpExtraMod.getVersion().contains("0.7.4")) {
                isSrpExtraValid = false;
            }
        }

        boolean hasSomeEnch = Loader.isModLoaded("somanyenchantments");
        boolean hasReskillable = Loader.isModLoaded("reskillable");
        boolean hasPotionCore = Loader.isModLoaded("potioncore");

        // Each entry: { display name, reason, url }
        List<String[]> recommendations = new ArrayList<>();

        if (!hasSomeEnch) {
            recommendations.add(new String[] {
                    "So Many Enchantments", "Adds extra enchantments used by the mod.", URL_SOME_ENCHANTMENTS
            });
        }
        
        if (!hasSrpExtra) {
            if (warnSrparasitesOldVersion) {
                recommendations.add(new String[] {
                        "SRPextra v0.7.4", "Required for recipes. Since SRParasites < 1.10, you MUST use v0.7.4.", URL_SRPEXTRA
                });
            } else {
                recommendations.add(new String[] {
                        "SRPextra", "Required for full crafting recipes. Fallback recipes are now active.", URL_SRPEXTRA
                });
            }
        } else if (!isSrpExtraValid) {
            recommendations.add(new String[] {
                    "SRPextra v0.7.4", "Incompatible SRPextra detected! Since SRParasites < 1.10, downgrade to v0.7.4.", URL_SRPEXTRA
            });
        }
        if (wantsBaubleFruitsWarning) {
            String reason = !hasBaubles
                    ? "Not installed. Bauble Fruits system is fully disabled."
                    : "Legacy Baubles detected. Fruits grant +Luck only — install BaublesEX for real slot expansion.";
            recommendations.add(new String[] { "BaublesEX", reason, URL_BAUBLES_EX });
        }
        if (!hasPotionCore) {
            recommendations.add(new String[] {
                    "PotionCore", "Enables magic damage attribute scaling. Recommended for spell builds.",
                    URL_POTIONCORE
            });
        }
        
        if (wantsSkillsModuleWarning) {
            if (!hasReskillable) {
                recommendations.add(new String[] {
                        "Reskillable", "Required for the skill tree and trait system.", URL_RESKILLABLE
                });
            }
        }

        // Register login handler only if there is something to report.
        if (!recommendations.isEmpty()) {
            final List<String[]> finalRecs = recommendations;
            MinecraftForge.EVENT_BUS.register(new RecommendationsLoginHandler(finalRecs));
        }

        // Baubles legacy persistence handler
        if (com.spege.insanetweaks.config.ModConfig.modules.enableBaubleFruits && hasBaubles) {
            MinecraftForge.EVENT_BUS.register(new com.spege.insanetweaks.events.BaubleFruitEventHandler());

            if (!isBaublesEx) {
                ModContainer baubles = Loader.instance().getIndexedModList().get("baubles");
                String ver = baubles != null ? baubles.getVersion() : "unknown";
                LOGGER.info("[InsaneTweaks] Bauble Fruits: Original Baubles v{} detected (not BaublesEX). " +
                        "Running in LEGACY MODE.", ver);
            }
        }

        if (hasReskillable && com.spege.insanetweaks.config.ModConfig.modules.enableSkillsModule) {
            MinecraftForge.EVENT_BUS.register(new com.spege.insanetweaks.skills.EventHandlerSkills());
            MinecraftForge.EVENT_BUS.register(new com.spege.insanetweaks.skills.AdaptedVegetationSkill());
            MinecraftForge.EVENT_BUS.register(new com.spege.insanetweaks.events.ParasiteXPFixHandler());
            LOGGER.info("[InsaneTweaks] Reskillable traits module enabled.");
        }

        if (hasReskillable && event.getSide() == net.minecraftforge.fml.relauncher.Side.CLIENT) {
            MinecraftForge.EVENT_BUS.register(new com.spege.insanetweaks.events.ReskillableGuiHandler());
        }
    }

    // -------------------------------------------------------------------------
    // serverStarting
    // -------------------------------------------------------------------------

    @Mod.EventHandler
    public void serverStarting(FMLServerStartingEvent event) {
        event.registerServerCommand(new CommandBackupCursed());
    }

    // -------------------------------------------------------------------------
    // Login warning handler
    // -------------------------------------------------------------------------

    /**
     * Sends a compact, clickable recommendation message to the first player who
     * logs in.
     * Fires once per session, then unregisters itself by clearing the flag.
     */
    private static class RecommendationsLoginHandler {
        private final List<String[]> recs;
        private boolean sent = false;

        RecommendationsLoginHandler(List<String[]> recs) {
            this.recs = recs;
        }

        @SubscribeEvent
        @SuppressWarnings("null")
        public void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
            if (sent)
                return;
            sent = true;

            if (!recs.isEmpty()) {
                event.player.sendMessage(new TextComponentString(
                        TextFormatting.DARK_AQUA + "--- [InsaneTweaks] Recommended mods ---"));

                for (String[] rec : recs) {
                    String name = rec[0];
                    String reason = rec[1];
                    String url = rec[2];

                    // Line 1: mod name + short reason
                    event.player.sendMessage(new TextComponentString(
                            TextFormatting.YELLOW + "\u25BA " + TextFormatting.WHITE + name +
                                    TextFormatting.GRAY + " — " + reason));

                    // Line 2: clickable CurseForge link
                    TextComponentString linkPrefix = new TextComponentString(
                            TextFormatting.GRAY + "  Download: ");
                    TextComponentString linkText = new TextComponentString(
                            TextFormatting.AQUA + "" + TextFormatting.UNDERLINE + url);
                    linkText.setStyle(new Style()
                            .setClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, url)));
                    linkPrefix.appendSibling(linkText);
                    event.player.sendMessage(linkPrefix);
                }

                event.player.sendMessage(new TextComponentString(
                        TextFormatting.DARK_AQUA + "---------------------------------------"));
            }
        }
    }

    // -------------------------------------------------------------------------
    // Compatibility report (console log)
    // -------------------------------------------------------------------------

    private static void logCompatibilityReport() {
        boolean hasSomeEnchants = Loader.isModLoaded("somanyenchantments");
        boolean hasSrpExtra = Loader.isModLoaded("srpextra");
        boolean hasBaubles = Loader.isModLoaded("baubles");
        boolean hasPotionCore = Loader.isModLoaded("potioncore");
        boolean hasReskillable = Loader.isModLoaded("reskillable");
        boolean hasCompatSkills = Loader.isModLoaded("compatskills");
        boolean isBaublesEx = hasBaubles && com.spege.insanetweaks.init.ModItems.isBaublesExPresent();

        // Check srparasites version
        boolean oldSrparasites = false;
        ModContainer srparasitesMod = Loader.instance().getIndexedModList().get("srparasites");
        if (srparasitesMod != null) {
            try {
                DefaultArtifactVersion current = new DefaultArtifactVersion(srparasitesMod.getVersion());
                DefaultArtifactVersion min = new DefaultArtifactVersion("1.10");
                oldSrparasites = current.compareTo(min) < 0;
            } catch (Exception e) {
                LOGGER.warn("[InsaneTweaks] Could not parse srparasites version: {}", srparasitesMod.getVersion());
            }
        }
        warnSrparasitesOldVersion = oldSrparasites;

        LOGGER.info("================================================");
        LOGGER.info("  InsaneTweaks - Optional Mod Compatibility");
        LOGGER.info("================================================");
        LOGGER.info("  somanyenchantments  ... {}", status(hasSomeEnchants));
        LOGGER.info("  srpextra            ... {}", status(hasSrpExtra));
        if (!hasSrpExtra)
            LOGGER.info("   -> Fallback recipes active.");
            
        if (oldSrparasites && srparasitesMod != null) {
            ModContainer srpEx = Loader.instance().getIndexedModList().get("srpextra");
            if (srpEx == null || !srpEx.getVersion().contains("0.7.4")) {
                LOGGER.warn("  [!] SRParasites v{} < 1.10! Recommended: SRPextra v0.7.4", srparasitesMod.getVersion());
            }
        }
        
        LOGGER.info("  baubles             ... {} ({})",
                status(hasBaubles), hasBaubles ? (isBaublesEx ? "BaublesEX" : "Legacy") : "n/a");
        if (hasBaubles && !isBaublesEx)
            LOGGER.info("   -> Recommend BaublesEX fork for full slot expansion.");
        LOGGER.info("  potioncore          ... {}", status(hasPotionCore));
        if (hasPotionCore)
            LOGGER.info("   -> If crashing: set 'Fix Saturation = false' in potioncore.cfg");
        LOGGER.info("  reskillable         ... {}", status(hasReskillable));
        LOGGER.info("  compatskills        ... {}", status(hasCompatSkills));
        if (!hasReskillable) {
            LOGGER.info("   -> Skills Module disabled (requires reskillable).");
        } else if (!hasCompatSkills) {
            LOGGER.info("   -> CompatSkills not installed; custom traits still use its domain for save compatibility.");
        }
        LOGGER.info("================================================");
    }

    private static String status(boolean loaded) {
        return loaded ? "FOUND  " : "MISSING";
    }
}
