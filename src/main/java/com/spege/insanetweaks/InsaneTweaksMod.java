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
import net.minecraftforge.fml.common.registry.EntityRegistry;
import net.minecraft.util.ResourceLocation;
import com.spege.insanetweaks.entities.EntityItemIndestructible;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.maven.artifact.versioning.DefaultArtifactVersion;

import java.util.ArrayList;
import java.util.List;

@Mod(modid = InsaneTweaksMod.MODID, name = InsaneTweaksMod.NAME, version = InsaneTweaksMod.VERSION, dependencies = "required-after:forge@[14.23.5.2860,);after:somanyenchantments;required-after:ebwizardry;required-after:spartanweaponry;required-after:ancientspellcraft;required-after:swparasites;required-after:srparasites;"
        +
        "after:srpextra;after:baubles;after:potioncore;before:compatskills;before:reskillable")
public class InsaneTweaksMod {
    public static final String MODID = "insanetweaks";
    public static final String NAME = "Insane Tweaks";
    public static final String VERSION = "1.0.5";

    public static final Logger LOGGER = LogManager.getLogger(MODID);

    // Set in preInit after version check; consumed in init.
    private static boolean warnSrparasitesOldVersion = false;
    private static boolean wantsBaubleFruitsWarning = false;
    private static boolean wantsSkillsModuleWarning = false;
    private static boolean notifySkillsModuleOff = false;

    // -----------------------------------------------------------------------
    // CurseForge URLs — update if any link changes
    // -----------------------------------------------------------------------
    private static final String URL_BAUBLES_EX = "https://www.curseforge.com/minecraft/mc-mods/baublesex";
    private static final String URL_SRPEXTRA = "https://www.curseforge.com/minecraft/mc-mods/scape-and-run-parasites-extra";
    private static final String URL_RESKILLABLE = "https://www.curseforge.com/minecraft/mc-mods/reskillable-fork";
    private static final String URL_COMPATSKILLS = "https://www.curseforge.com/minecraft/mc-mods/compatskills-fork";
    private static final String URL_SOME_ENCHANTMENTS = "https://www.curseforge.com/minecraft/mc-mods/so-many-enchantments";
    private static final String URL_POTIONCORE = "https://www.curseforge.com/minecraft/mc-mods/potion-core";

    // -------------------------------------------------------------------------
    // preInit
    // -------------------------------------------------------------------------

    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        // Print compatibility report to log and set version flags.
        logCompatibilityReport();

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
        if (!wantsSkillsModule) {
            notifySkillsModuleOff = true;
        }

        // Auto-detect Reskillable & CompatSkills; disable Skills Module if either is absent.
        if (!Loader.isModLoaded("reskillable") || !Loader.isModLoaded("compatskills")) {
            if (wantsSkillsModule) {
                LOGGER.info(
                        "[InsaneTweaks] Reskillable or CompatSkills missing. Automatically disabling Skills Module.");
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
        EntityRegistry.registerModEntity(new ResourceLocation(MODID, "indestructible_item"),
                EntityItemIndestructible.class, "indestructible_item", 99, this, 64, 20, true);

        // Fire/lava immunity for all Living and Sentient item drops — registered
        // unconditionally.
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
            MinecraftForge.EVENT_BUS.register(new com.spege.insanetweaks.events.SpellbladeTooltipHandler());
            MinecraftForge.EVENT_BUS.register(new com.spege.insanetweaks.events.ArmorEventHandler());
            MinecraftForge.EVENT_BUS.register(new com.spege.insanetweaks.events.ArmorTooltipHandler());
            MinecraftForge.EVENT_BUS.register(new com.spege.insanetweaks.events.AegisEventHandler());
            MinecraftForge.EVENT_BUS.register(new com.spege.insanetweaks.events.AegisTooltipHandler());
            if (event.getSide() == net.minecraftforge.fml.relauncher.Side.CLIENT) {
                MinecraftForge.EVENT_BUS.register(new com.spege.insanetweaks.events.SpellbladeSoundHandler());
            }
        }

        // GoldenBook is independent of the SRP/EBWizardry bridge — register
        // unconditionally.
        MinecraftForge.EVENT_BUS.register(new com.spege.insanetweaks.events.GoldenBookEventHandler());

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
        boolean hasCompatSkills = Loader.isModLoaded("compatskills");
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
            if (!hasCompatSkills) {
                recommendations.add(new String[] {
                        "CompatSkills", "Required alongside Reskillable for custom traits.", URL_COMPATSKILLS
                });
            }
        }

        // Register login handler only if there is something to report.
        if (!recommendations.isEmpty() || notifySkillsModuleOff) {
            final List<String[]> finalRecs = recommendations;
            boolean finalNotifySkills = notifySkillsModuleOff;
            MinecraftForge.EVENT_BUS.register(new RecommendationsLoginHandler(finalRecs, finalNotifySkills));
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
            LOGGER.info("[InsaneTweaks] Reskillable traits module enabled.");
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
        private final boolean notifySkillsModuleOff;
        private boolean sent = false;

        RecommendationsLoginHandler(List<String[]> recs, boolean notifySkillsModuleOff) {
            this.recs = recs;
            this.notifySkillsModuleOff = notifySkillsModuleOff;
        }

        @SubscribeEvent
        public void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
            if (sent)
                return;
            sent = true;

            if (notifySkillsModuleOff) {
                event.player.sendMessage(new TextComponentString(
                        TextFormatting.RED + "[InsaneTweaks] " + TextFormatting.YELLOW + "Custom Skills module is off! enable this via config"));
            }

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
        if (!hasReskillable || !hasCompatSkills)
            LOGGER.info("   -> Skills Module disabled (requires reskillable + compatskills).");
        LOGGER.info("================================================");
    }

    private static String status(boolean loaded) {
        return loaded ? "FOUND  " : "MISSING";
    }
}
