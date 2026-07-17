package com.spege.insanetweaks;

import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.ModContainer;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.common.event.FMLServerStartingEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.network.NetworkRegistry;
import net.minecraftforge.fml.common.network.IGuiHandler;
import net.minecraft.util.text.Style;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.util.text.event.ClickEvent;
import net.minecraftforge.fml.common.gameevent.PlayerEvent;
import com.spege.insanetweaks.events.LivingDeathEventHandler;


import net.minecraftforge.fml.client.registry.RenderingRegistry;
import net.minecraftforge.fml.client.registry.IRenderFactory;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.Render;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.client.renderer.entity.RenderSnowball;
import net.minecraft.init.Items;
import net.minecraftforge.fml.common.registry.EntityRegistry;
import net.minecraft.util.ResourceLocation;
import com.spege.insanetweaks.entities.EntityBeckonSivMinion;
import com.spege.insanetweaks.client.renderer.entity.RenderBeckonSivMinion;
import com.spege.insanetweaks.client.renderer.entity.RenderSimWizard;
import com.spege.insanetweaks.entities.EntityFerCowMinion;
import com.spege.insanetweaks.entities.EntitySentinel;
import com.spege.insanetweaks.entities.EntitySimWizard;
import com.spege.insanetweaks.entities.EntityThrallMinion;
import com.spege.insanetweaks.client.renderer.entity.RenderThrallMinion;
import com.spege.insanetweaks.entities.EntityWizardMinion;
import com.spege.insanetweaks.entities.EntityRupterMinion;
import com.spege.insanetweaks.entities.EntitySummonerVomitCloud;
import com.spege.insanetweaks.entities.EntityPrimitiveSummonerMinion;
import com.spege.insanetweaks.entities.EntityPrimitiveYelloweyeMinion;
import com.spege.insanetweaks.entities.EntityPurifyingWave;
import com.spege.insanetweaks.entities.projectile.EntityYelloweyeGlandProjectile;
import com.spege.insanetweaks.entities.projectile.EntityYelloweyeNade;
import com.spege.insanetweaks.entities.projectile.EntityYelloweyeNadeProjectile;
import com.spege.insanetweaks.entities.projectile.EntityYelloweyeSpineball;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.maven.artifact.versioning.DefaultArtifactVersion;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Mod(modid = InsaneTweaksMod.MODID, name = InsaneTweaksMod.NAME, version = InsaneTweaksMod.VERSION,
        guiFactory = "com.spege.insanetweaks.client.gui.config.InsaneTweaksGuiFactory",
        dependencies = "required-after:forge@[14.23.5.2860,);after:somanyenchantments;after:player_mana;required-after:ebwizardry;required-after:spartanweaponry;required-after:ancientspellcraft;after:swparasites;required-after:srparasites;"
        +
        "after:srpextra;after:baubles;after:potioncore;before:reskillable")
public class InsaneTweaksMod implements IGuiHandler {
    public static final String MODID = "insanetweaks";
    /**
     * Scape and Run Parasites mod id. NOTE: the modid is "srparasites" (see SRP's @Mod
     * annotation / mcmod.info) even though its Java package is com.dhanantry.scapeandrunparasites.
     * Using the package name here silently disables every SRP-gated feature — always use this.
     */
    public static final String SRP_MODID = "srparasites";
    public static final String NAME  = "Insane Tweaks";
    public static final String VERSION = "1.1.1";

    /** GUI ID for the Thrall inventory screen (used with NetworkRegistry / player.openGui). */
    public static final int GUI_ID_THRALL_INV = 1;

    /** GUI ID for the combined Sentinel control + loot screen. */
    public static final int GUI_ID_SENTINEL = 2;

    @Mod.Instance
    public static InsaneTweaksMod INSTANCE;

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
    // Constructor
    // -------------------------------------------------------------------------

    public InsaneTweaksMod() {
        // Must run before FML's first ConfigManager.sync (which fires later inside
        // FMLModContainer.constructMod) - see OldConfigBackup.
        com.spege.insanetweaks.config.OldConfigBackup.backupOldConfigIfPresent();
    }

    // -------------------------------------------------------------------------
    // preInit
    // -------------------------------------------------------------------------

    @Mod.EventHandler
    @SuppressWarnings("null")
    public void preInit(FMLPreInitializationEvent event) {
        
        // Print compatibility report to log and set version flags.
        logCompatibilityReport();
        com.spege.insanetweaks.network.InsaneTweaksNetwork.init();

        if (event.getSide() == net.minecraftforge.fml.relauncher.Side.CLIENT) {
            RenderingRegistry.registerEntityRenderingHandler(EntitySentinel.class,
                    new IRenderFactory<EntitySentinel>() {
                        @Override
                        public Render<? super EntitySentinel> createRenderFor(RenderManager manager) {
                            return new RenderSentinel(manager);
                        }
                    });
            RenderingRegistry.registerEntityRenderingHandler(EntityWizardMinion.class,
                    new IRenderFactory<EntityWizardMinion>() {
                        @Override
                        public Render<? super EntityWizardMinion> createRenderFor(RenderManager manager) {
                            return new RenderWizardMinion(manager);
                        }
                    });
            RenderingRegistry.registerEntityRenderingHandler(EntitySimWizard.class,
                    new IRenderFactory<EntitySimWizard>() {
                        @Override
                        public Render<? super EntitySimWizard> createRenderFor(RenderManager manager) {
                            return new RenderSimWizard(manager);
                        }
                    });
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
            RenderingRegistry.registerEntityRenderingHandler(EntityYelloweyeGlandProjectile.class,
                    new IRenderFactory<EntityYelloweyeGlandProjectile>() {
                        @Override
                        public Render<? super EntityYelloweyeGlandProjectile> createRenderFor(RenderManager manager) {
                            return new RenderYelloweyeGlandProjectile(manager);
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
            RenderingRegistry.registerEntityRenderingHandler(EntityBeckonSivMinion.class,
                    new IRenderFactory<EntityBeckonSivMinion>() {
                        @Override
                        public Render<? super EntityBeckonSivMinion> createRenderFor(RenderManager manager) {
                            return new RenderBeckonSivMinion(manager);
                        }
                    });
            RenderingRegistry.registerEntityRenderingHandler(EntityThrallMinion.class,
                    new IRenderFactory<EntityThrallMinion>() {
                        @Override
                        public Render<? super EntityThrallMinion> createRenderFor(RenderManager manager) {
                            return new RenderThrallMinion(manager);
                        }
                    });
            net.minecraftforge.fml.client.registry.RenderingRegistry.registerEntityRenderingHandler(
                    com.spege.insanetweaks.entities.EntityCorruptedSapling.class,
                    new net.minecraftforge.fml.client.registry.IRenderFactory<com.spege.insanetweaks.entities.EntityCorruptedSapling>() {
                        @Override
                        public net.minecraft.client.renderer.entity.Render<com.spege.insanetweaks.entities.EntityCorruptedSapling> createRenderFor(
                                net.minecraft.client.renderer.entity.RenderManager manager) {
                            return new com.spege.insanetweaks.client.renderer.entity.RenderCorruptedSapling(manager);
                        }
                    });

            // Zhonya rework: golden player tint during Gilded Stasis.
            MinecraftForge.EVENT_BUS.register(new com.spege.insanetweaks.events.ZhonyaClientHandler());
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
        EntityRegistry.registerModEntity(new ResourceLocation(MODID, "wizard_minion"),
                EntityWizardMinion.class, "wizard_minion", 112, this, 64, 3, true);
        EntityRegistry.registerModEntity(new ResourceLocation(MODID, "sentinel"),
                EntitySentinel.class, "sentinel", 113, this, 64, 3, true, 0x8F0C12, 0x2D2D2D);
        EntityRegistry.registerModEntity(new ResourceLocation(MODID, "sim_wizard"),
                EntitySimWizard.class, "sim_wizard", 115, this, 64, 3, true, 0x5A6C72, 0x20353E);
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
        EntityRegistry.registerModEntity(new ResourceLocation(MODID, "yelloweye_gland_projectile"),
                EntityYelloweyeGlandProjectile.class, "yelloweye_gland_projectile", 111, this, 64, 10, true);
        EntityRegistry.registerModEntity(new ResourceLocation(MODID, "yelloweye_nade_projectile"),
                EntityYelloweyeNadeProjectile.class, "yelloweye_nade_projectile", 103, this, 64, 10, true);
        EntityRegistry.registerModEntity(new ResourceLocation(MODID, "yelloweye_nade"),
                EntityYelloweyeNade.class, "yelloweye_nade", 104, this, 64, 10, true);
        EntityRegistry.registerModEntity(new ResourceLocation(MODID, "beckon_siv_minion"),
                EntityBeckonSivMinion.class, "beckon_siv_minion", 110, this, 64, 3, true);
        EntityRegistry.registerModEntity(new ResourceLocation(MODID, "thrall_minion"),
                EntityThrallMinion.class, "thrall_minion", 114, this, 64, 3, true);
        // ID 116 — next free (100-115 used). Never reuse/reorder network-stable IDs.
        EntityRegistry.registerModEntity(new ResourceLocation(MODID, "corrupted_sapling"),
                com.spege.insanetweaks.entities.EntityCorruptedSapling.class, "corrupted_sapling", 116, this, 64, 3, false);


        // Immediately grant fire/explosion immunity to all Living and Sentient item drops
        // on the tick they join the world, before any explosion can hit them.
        MinecraftForge.EVENT_BUS.register(new com.spege.insanetweaks.events.IndestructibleDropHandler());

        if (!com.spege.insanetweaks.config.ModConfig.tweaks.enableZhonya) {
            // Defensive future-proofing only: EB consults the enabled flag in
            // isArtefactActive, so any future caller checking Zhonya that way is covered.
            // The primary gate is the enableZhonya checks in the item itself.
            ((electroblob.wizardry.item.ItemArtefact) com.spege.insanetweaks.init.ModItems.ZHONYAS_HOURGLASS)
                    .setEnabled(false);
            LOGGER.info("[InsaneTweaks] Zhonya's Hourglass is disabled via config (tweaks.enableZhonya=false).");
        }

        // Zhonya rework: Gilded Stasis enforcement (immortality, root, aggro loss).
        MinecraftForge.EVENT_BUS.register(new com.spege.insanetweaks.events.ZhonyaStasisHandler());

        if (com.spege.insanetweaks.config.ModConfig.tombstone.enableTombstoneTweaks) {
            if (com.spege.insanetweaks.config.ModConfig.tombstone.enableCurseOfPossessionPatch) {
                MinecraftForge.EVENT_BUS.register(new LivingDeathEventHandler());
            }
            MinecraftForge.EVENT_BUS.register(new com.spege.insanetweaks.events.TombstoneDropEventHandler());
            MinecraftForge.EVENT_BUS.register(new com.spege.insanetweaks.events.TombstoneBooksHandler());
            MinecraftForge.EVENT_BUS.register(new com.spege.insanetweaks.events.GraveDecayHandler());
        }

        if (com.spege.insanetweaks.config.ModConfig.modules.enableCustomCores) {
            MinecraftForge.EVENT_BUS.register(new com.spege.insanetweaks.events.CustomCoresEventHandler());
            MinecraftForge.EVENT_BUS.register(new com.spege.insanetweaks.events.CoreTooltipHandler());
        }

        if (com.spege.insanetweaks.config.ModConfig.modules.enableSrpEbWizardryBridge) {
            electroblob.wizardry.util.WandHelper.registerSpecialUpgrade(
                    com.spege.insanetweaks.init.ModItems.ADAPTATION_UPGRADE, "adaptation");
            MinecraftForge.EVENT_BUS.register(new com.spege.insanetweaks.events.SpellbladeHitHandler());
            MinecraftForge.EVENT_BUS.register(new com.spege.insanetweaks.events.FleshboundEventHandler());
            MinecraftForge.EVENT_BUS.register(new com.spege.insanetweaks.events.WandEventHandler());
            MinecraftForge.EVENT_BUS.register(new com.spege.insanetweaks.events.ArcaneBridgeEventHandler());
            MinecraftForge.EVENT_BUS.register(new com.spege.insanetweaks.events.SpellbladeTooltipHandler());
            MinecraftForge.EVENT_BUS.register(new com.spege.insanetweaks.events.AttackSpeedDebugHandler());
            MinecraftForge.EVENT_BUS.register(new com.spege.insanetweaks.events.ArmorEventHandler());
            MinecraftForge.EVENT_BUS.register(new com.spege.insanetweaks.events.ArmorTooltipHandler());
            MinecraftForge.EVENT_BUS.register(new com.spege.insanetweaks.events.BattlemageAdaptationHandler());
            if (event.getSide() == net.minecraftforge.fml.relauncher.Side.CLIENT) {
                MinecraftForge.EVENT_BUS.register(new com.spege.insanetweaks.events.BattlemageTooltipHandler());
            }
            MinecraftForge.EVENT_BUS.register(new com.spege.insanetweaks.events.AegisEventHandler());
            MinecraftForge.EVENT_BUS.register(new com.spege.insanetweaks.events.AegisTooltipHandler());
            MinecraftForge.EVENT_BUS.register(new com.spege.insanetweaks.events.WandTooltipHandler());
            MinecraftForge.EVENT_BUS.register(com.spege.insanetweaks.baubles.ItemInfernalCrownArtefact.class);
            if (event.getSide() == net.minecraftforge.fml.relauncher.Side.CLIENT) {
                MinecraftForge.EVENT_BUS.register(new com.spege.insanetweaks.events.SpellbladeSoundHandler());
                MinecraftForge.EVENT_BUS.register(new com.spege.insanetweaks.events.GlobalPropertyTooltipHandler());
            }
        }

        // Sim_wizard SRP faction integration: cancels parasite<->sim_wizard friendly fire so
        // AoE spells (spark_bomb chains, force_orb splash) never turn the SRP pack against the
        // wizard. Gated by its own flag (the entity is spawnable via /summon regardless of the
        // bridge conversion being enabled, so this must not hide behind enableSrpEbWizardryBridge).
        if (com.spege.insanetweaks.config.ModConfig.entities.assimilatedWizard.spawning.enabled) {
            MinecraftForge.EVENT_BUS.register(new com.spege.insanetweaks.events.SimWizardFactionHandler());
        }

        // Zhonyas Hourglass snapshot handler: applies NBT snapshots from MixinParasiteEventEntity
        // to newly spawned SRP entities (EntityPInfected, EntityInhooM/S).
        // Gated by SRP presence (required dependency, but kept explicit for clarity).
        // Registered unconditionally of enableSrpEbWizardryBridge — the item can exist
        // without the full bridge being enabled.
        if (Loader.isModLoaded(SRP_MODID)) {
            MinecraftForge.EVENT_BUS.register(new com.spege.insanetweaks.events.ZhonyasEventHandler());
        }

        // Infernal elite kills drop spectral dust — independent of the SRP/EBW bridge.
        if (Loader.isModLoaded("infernalmobs")
                && com.spege.insanetweaks.config.ModConfig.interactions.enableInfernalDustDrops) {
            MinecraftForge.EVENT_BUS.register(new com.spege.insanetweaks.events.InfernalDustDropHandler());
        }

        // GoldenBook is independent of the SRP/EBWizardry bridge — register
        // unconditionally.
        MinecraftForge.EVENT_BUS.register(new com.spege.insanetweaks.events.GoldenBookEventHandler());
        if (com.spege.insanetweaks.config.ModConfig.modules.enableSpells) {
            MinecraftForge.EVENT_BUS.register(new com.spege.insanetweaks.events.SpellRestrictionEventHandler());
            MinecraftForge.EVENT_BUS.register(new com.spege.insanetweaks.events.ParasiteShroudEventHandler());
            MinecraftForge.EVENT_BUS.register(new com.spege.insanetweaks.events.ImmuneBondHandler());
        }
        // Invariant B: make every mob ignore the immortal thrall (see spec 2.1). Registered
        // unconditionally — the thrall entity itself registers unconditionally above, so its
        // protection must not depend on the enableSpells module flag.
        MinecraftForge.EVENT_BUS.register(new com.spege.insanetweaks.events.ThrallTargetProtectionHandler());
        // One-shot config-reset notice; registered unconditionally - it no-ops unless a migration
        // happened this launch, and must not be suppressible by a setting that itself just got reset.
        MinecraftForge.EVENT_BUS.register(new com.spege.insanetweaks.events.ConfigResetNoticeHandler());
        if (event.getSide() == net.minecraftforge.fml.relauncher.Side.CLIENT) {
            MinecraftForge.EVENT_BUS.register(new com.spege.insanetweaks.events.SpellItemTooltipHandler());
            MinecraftForge.EVENT_BUS.register(new com.spege.insanetweaks.events.SpellBookGuiHandler());
            MinecraftForge.EVENT_BUS.register(new com.spege.insanetweaks.events.SentinelClientInteractionHandler());
            MinecraftForge.EVENT_BUS.register(new com.spege.insanetweaks.events.ThrallClientInteractionHandler());
            if (com.spege.insanetweaks.config.ModConfig.modules.enableSpells) {
                MinecraftForge.EVENT_BUS.register(new com.spege.insanetweaks.client.YelloweyeChargeHandler());
            }
        }

        // --- Build the list of recommended mods that are missing / need updating ---
        boolean hasBaubles = Loader.isModLoaded("baubles");
        boolean isBaublesEx = hasBaubles && com.spege.insanetweaks.init.ModItems.isBaublesExPresent();
        boolean hasSrpExtra = Loader.isModLoaded("srpextra");
        // srpextra version check retired -- see logCompatibilityReport() comment for rationale.

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
            recommendations.add(new String[] {
                    "SRPextra", "Required for full crafting recipes. Fallback recipes are now active.", URL_SRPEXTRA
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

        // Ars Magica 2's EBW compat layer gates NPC spellcasting behind AM2 burnout/mana
        // (our sim wizard and sentinel lose everything above the cheapest novice spells)
        // and despawns EBW summons over AM2's own summon cap. Diagnosed 2026-07-17 from
        // the SpellCastEvent.Pre veto logs. The NpcCastVetoArbiter second-opinion check
        // (interactions.npcCastVetoSecondOpinion) now recovers the vetoed casts by re-testing
        // the KNOWN legitimate veto conditions, and SummonVetoGuardHandler revives the culled
        // summons — so AM2 is a soft conflict rather than a hard one when the workaround is on.
        if (Loader.isModLoaded("arsmagica2")) {
            LOGGER.warn("[InsaneTweaks] Ars Magica 2 detected: its EB Wizardry compat blocks NPC "
                    + "spellcasting (sim wizard, sentinel) and culls summons over AM2's cap. The "
                    + "NPC Cast Veto Second Opinion workaround (on by default with AM2 present) "
                    + "recovers these; disable it via config if it misbehaves.");
            recommendations.add(new String[] {
                    "Ars Magica 2 conflict",
                    "AM2 blocks NPC spellcasting (sim wizard/sentinel) and despawns summons over its cap. The 'NPC Cast Veto Second Opinion' workaround is auto-enabled to recover these.",
                    ""
            });
        }

        // AM2 summon-cap workaround: revive our sim wizard / sentinel summons that AM2 deletes
        // at world-join. Gated by the same resolved tri-state as the cast arbiter.
        if (com.spege.insanetweaks.util.NpcCastVetoArbiter.isActive()) {
            MinecraftForge.EVENT_BUS.register(new com.spege.insanetweaks.events.SummonVetoGuardHandler());
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

            if (Loader.isModLoaded(SRP_MODID)) {
                // Corrupted fruit loop (fragment drops + corrupted-eat doom).
                MinecraftForge.EVENT_BUS.register(new com.spege.insanetweaks.events.CorruptedFragmentDropHandler());
                MinecraftForge.EVENT_BUS.register(new com.spege.insanetweaks.events.CorruptedFruitDoomHandler());
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

        // SRP second layer for invariant B: append the thrall's registry name to SRP's public
        // mobattackingBlackList so parasite targeting selectors skip it. checkEntity() does a
        // substring 'contains' match against the entity's registry name, so the exact id works.
        // Guarded by SRP presence; a mid-game SRP config reload can drop this — the
        // ThrallTargetProtectionHandler remains the always-on guarantee.
        if (Loader.isModLoaded(SRP_MODID)) {
            appendThrallToSrpBlacklist();
        }

        // Register IGuiHandler for thrall inventory GUI (syncs slots server->client via Forge)
        NetworkRegistry.INSTANCE.registerGuiHandler(this, this);
    }

    /**
     * Appends "insanetweaks:thrall_minion" to SRPConfig.mobattackingBlackList (a public static
     * String[]) so SRP parasites never target the immortal thrall. Idempotent — skips if already
     * present. Isolated in its own method so the SRP class link only loads when SRP is present.
     */
    private static void appendThrallToSrpBlacklist() {
        try {
            String thrallId = MODID + ":thrall_minion";
            String[] current = com.dhanantry.scapeandrunparasites.util.config.SRPConfig.mobattackingBlackList;
            if (current == null) {
                com.dhanantry.scapeandrunparasites.util.config.SRPConfig.mobattackingBlackList =
                        new String[] { thrallId };
                LOGGER.info("[InsaneTweaks] Initialised SRP mobattackingBlackList with thrall id.");
                return;
            }
            for (String s : current) {
                if (thrallId.equals(s)) {
                    return; // already present
                }
            }
            String[] updated = java.util.Arrays.copyOf(current, current.length + 1);
            updated[current.length] = thrallId;
            com.dhanantry.scapeandrunparasites.util.config.SRPConfig.mobattackingBlackList = updated;
            LOGGER.info("[InsaneTweaks] Added '{}' to SRP mobattackingBlackList (parasites will ignore the thrall).", thrallId);
        } catch (Throwable t) {
            // Never fatal — the LivingSetAttackTargetEvent handler is the primary guarantee.
            LOGGER.warn("[InsaneTweaks] Could not append thrall to SRP mobattackingBlackList: {}", t.toString());
        }
    }

    // -------------------------------------------------------------------------
    // serverStarting
    // -------------------------------------------------------------------------

    @Mod.EventHandler
    public void serverStarting(FMLServerStartingEvent event) {
        event.registerServerCommand(new com.spege.insanetweaks.commands.CommandInsaneTweaks());
        LOGGER.info("Insane Tweaks Server Commands registered.");
    }

    // -------------------------------------------------------------------------
    // IGuiHandler — Thrall Inventory (GUI_ID_THRALL_INV = 1)
    // -------------------------------------------------------------------------

    /**
     * Server-side: creates the Container backed by the thrall entity's real inventory.
     * {@code x} carries the entity ID encoded as the parameter.
     */
    @Override
    @SuppressWarnings("null")
    public Object getServerGuiElement(int id, net.minecraft.entity.player.EntityPlayer player,
            net.minecraft.world.World world, int x, int y, int z) {
        if (id == GUI_ID_THRALL_INV) {
            net.minecraft.entity.Entity entity = world.getEntityByID(x);
            if (entity instanceof com.spege.insanetweaks.entities.EntityThrallMinion) {
                com.spege.insanetweaks.entities.EntityThrallMinion thrall =
                        (com.spege.insanetweaks.entities.EntityThrallMinion) entity;
                return new com.spege.insanetweaks.client.gui.ThrallContainer(
                        player, thrall.getThrallInventory(), thrall.getEntityId());
            }
        }
        if (id == GUI_ID_SENTINEL) {
            net.minecraft.entity.Entity entity = world.getEntityByID(x);
            if (entity instanceof com.spege.insanetweaks.entities.EntitySentinel) {
                com.spege.insanetweaks.entities.EntitySentinel sentinel =
                        (com.spege.insanetweaks.entities.EntitySentinel) entity;
                return new com.spege.insanetweaks.client.gui.SentinelLootContainer(player,
                        new com.spege.insanetweaks.entities.inventory.SentinelLootInventory(sentinel),
                        sentinel.getEntityId());
            }
        }
        return null;
    }

    /**
     * Client-side: creates the GuiContainer that renders the thrall inventory.
     */
    @Override
    @SuppressWarnings("null")
    public Object getClientGuiElement(int id, net.minecraft.entity.player.EntityPlayer player,
            net.minecraft.world.World world, int x, int y, int z) {
        if (id == GUI_ID_THRALL_INV) {
            net.minecraft.entity.Entity entity = world.getEntityByID(x);
            if (entity instanceof com.spege.insanetweaks.entities.EntityThrallMinion) {
                com.spege.insanetweaks.entities.EntityThrallMinion thrall =
                        (com.spege.insanetweaks.entities.EntityThrallMinion) entity;
                return new com.spege.insanetweaks.client.gui.GuiThrallInventory(player, thrall);
            }
        }
        if (id == GUI_ID_SENTINEL) {
            net.minecraft.entity.Entity entity = world.getEntityByID(x);
            if (entity instanceof com.spege.insanetweaks.entities.EntitySentinel) {
                com.spege.insanetweaks.entities.EntitySentinel sentinel =
                        (com.spege.insanetweaks.entities.EntitySentinel) entity;
                return new com.spege.insanetweaks.client.gui.GuiSentinelControl(
                        new com.spege.insanetweaks.client.gui.SentinelLootContainer(player,
                                new com.spege.insanetweaks.entities.inventory.SentinelLootInventory(sentinel),
                                sentinel.getEntityId()),
                        sentinel.getEntityId());
            }
        }
        return null;
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

            // Respect the player's preference to suppress chat noise on startup.
            // Checked BEFORE consuming the one-shot flag: a suppressing first joiner
            // must not swallow the message for everyone else this session.
            if (com.spege.insanetweaks.config.ModConfig.client.suppressStartupWarningsInChat)
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

                    // Line 2: clickable CurseForge link (skipped for pure warnings without one)
                    if (url != null && !url.isEmpty()) {
                        TextComponentString linkPrefix = new TextComponentString(
                                TextFormatting.GRAY + "  Download: ");
                        TextComponentString linkText = new TextComponentString(
                                TextFormatting.AQUA + "" + TextFormatting.UNDERLINE + url);
                        linkText.setStyle(new Style()
                                .setClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, url)));
                        linkPrefix.appendSibling(linkText);
                        event.player.sendMessage(linkPrefix);
                    }
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
        boolean hasPlayerMana = Loader.isModLoaded("player_mana");
        boolean hasReskillable = Loader.isModLoaded("reskillable");
        boolean hasCompatSkills = Loader.isModLoaded("compatskills");
        boolean isBaublesEx = hasBaubles && com.spege.insanetweaks.init.ModItems.isBaublesExPresent();

        // Check srparasites version.
        // SRParasites split its versioning after 1.10: the "old" branch stayed at 1.0.x.x
        // while the maintained branch moved to 1.9.x.x and later 1.10.x.x.
        // A naive compareTo("1.10") incorrectly classifies 1.9.x.x as "old" because
        // 1.9 < 1.10 numerically.  We also accept any version whose major.minor is
        // at least 1.9 as the new scheme.
        boolean oldSrparasites = false;
        ModContainer srparasitesMod = Loader.instance().getIndexedModList().get("srparasites");
        if (srparasitesMod != null) {
            try {
                DefaultArtifactVersion current = new DefaultArtifactVersion(srparasitesMod.getVersion());
                DefaultArtifactVersion minNew  = new DefaultArtifactVersion("1.9");  // new versioning scheme start
                DefaultArtifactVersion minOld  = new DefaultArtifactVersion("1.10"); // old scheme "full" threshold
                // Accept if >= 1.9 (new branch) OR >= 1.10 (old branch threshold).
                oldSrparasites = current.compareTo(minNew) < 0 && current.compareTo(minOld) < 0;
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
            LOGGER.warn("  [!] SRParasites v{} is below the supported range. Fallback recipe mode active.",
                    srparasitesMod.getVersion());
        }
        
        LOGGER.info("  baubles             ... {} ({})",
                status(hasBaubles), hasBaubles ? (isBaublesEx ? "BaublesEX" : "Legacy") : "n/a");
        if (hasBaubles && !isBaublesEx)
            LOGGER.info("   -> Recommend BaublesEX fork for full slot expansion.");
        LOGGER.info("  potioncore          ... {}", status(hasPotionCore));
        if (hasPotionCore)
            LOGGER.info("   -> If crashing: set 'Fix Saturation = false' in potioncore.cfg");
        LOGGER.info("  player_mana         ... {}", status(hasPlayerMana));
        if (hasPlayerMana)
            LOGGER.info("   -> Wand evolution and spellblade mana checks use player_mana compat.");
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
