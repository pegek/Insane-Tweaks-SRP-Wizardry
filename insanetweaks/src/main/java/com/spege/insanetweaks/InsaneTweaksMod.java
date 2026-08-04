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


import net.minecraftforge.fml.common.registry.EntityRegistry;
import net.minecraft.util.ResourceLocation;
import com.spege.insanetweaks.entities.EntityBeckonSivMinion;
import com.spege.insanetweaks.entities.EntityFerCowMinion;
import com.spege.insanetweaks.entities.EntityLightBomberMinion;
import com.spege.insanetweaks.entities.projectile.EntityBomberBomb;
import com.spege.insanetweaks.entities.EntitySentinel;
import com.spege.insanetweaks.entities.EntitySimWizard;
import com.spege.insanetweaks.entities.EntityThrallMinion;
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

@Mod(modid = InsaneTweaksMod.MODID, name = InsaneTweaksMod.NAME, version = InsaneTweaksMod.VERSION,
        guiFactory = "com.spege.insanetweaks.client.gui.config.InsaneTweaksGuiFactory",
        dependencies = "required-after:forge@[14.23.5.2860,);after:somanyenchantments;after:player_mana;required-after:ebwizardry;required-after:spartanweaponry;required-after:ancientspellcraft;after:swparasites;required-after:srparasites;"
        +
        "after:srpextra;after:baubles;after:potioncore;after:locks;before:reskillable")
public class InsaneTweaksMod implements IGuiHandler {
    public static final String MODID = "insanetweaks";
    /**
     * Scape and Run Parasites mod id. NOTE: the modid is "srparasites" (see SRP's @Mod
     * annotation / mcmod.info) even though its Java package is com.dhanantry.scapeandrunparasites.
     * Using the package name here silently disables every SRP-gated feature — always use this.
     */
    public static final String SRP_MODID = "srparasites";
    public static final String NAME  = "Insane Tweaks";
    public static final String VERSION = "1.9.11";

    /** GUI ID for the Thrall inventory screen (used with NetworkRegistry / player.openGui). */
    public static final int GUI_ID_THRALL_INV = 1;

    /** GUI ID for the combined Sentinel control + loot screen. */
    public static final int GUI_ID_SENTINEL = 2;

    /** GUI ID for the Sanctuary Core screen. */
    public static final int GUI_ID_SANCTUARY = 3;

    /** GUI ID for the Creative Sanctuary radius slider. */
    public static final int GUI_ID_CREATIVE_SANCTUARY = 4;

    @Mod.Instance
    public static InsaneTweaksMod INSTANCE;

    /**
     * Sided proxy. Everything client-only (entity renderers, the Sanctuary TESR) hangs off this —
     * it must never be inlined back into this class, see {@link CommonProxy}.
     */
    @net.minecraftforge.fml.common.SidedProxy(
            clientSide = "com.spege.insanetweaks.client.ClientProxy",
            serverSide = "com.spege.insanetweaks.CommonProxy")
    public static CommonProxy proxy;

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
        // Reads whichever of the two config files survived the call above, looking for the
        // "tombstone" category this mod stopped writing in 1.9.0. Must come second: the pre-rework
        // file is only at its backup name once OldConfigBackup has moved it there.
        com.spege.insanetweaks.config.TombstoneSplitNotice.scan();
    }

    // -------------------------------------------------------------------------
    // preInit
    // -------------------------------------------------------------------------

    @Mod.EventHandler
    @SuppressWarnings("null")
    public void preInit(FMLPreInitializationEvent event) {
        
        // Print compatibility report to log and set version flags.
        logCompatibilityReport();

        // The Tombstone module left in 1.9.0. Says so in the log for dedicated servers and for
        // anyone reading someone else's log; the client also gets a screen before the main menu,
        // registered in ClientProxy. Only fires when there is something at stake - see
        // TombstoneSplitNotice.
        com.spege.insanetweaks.config.TombstoneSplitNotice.logIfNeeded();

        com.spege.insanetweaks.network.InsaneTweaksNetwork.init();

        // 🚨 A loot table that is never registered here silently resolves to EMPTY in 1.12.2 -
        // no error, no warning, just no drops. All three sim_wizard tiers must be registered.
        net.minecraft.world.storage.loot.LootTableList.register(EntitySimWizard.LOOT_NOVICE);
        net.minecraft.world.storage.loot.LootTableList.register(EntitySimWizard.LOOT_ADEPT);
        net.minecraft.world.storage.loot.LootTableList.register(EntitySimWizard.LOOT_MASTER);

        // Sanctuary Dome is an SRP-compat feature end-to-end (blocks, spawn veto, TE logic
        // all key off SRParasites). Defensively disable if SRP isn't present so the module
        // flag can't linger true with a half-registered feature.
        if (com.spege.insanetweaks.config.ModConfig.modules.enableSanctuary
                && !Loader.isModLoaded(SRP_MODID)) {
            com.spege.insanetweaks.config.ModConfig.modules.enableSanctuary = false;
            LOGGER.warn("[InsaneTweaks] Sanctuary module auto-disabled: SRParasites not present.");
        }

        // 🚨 Renderers live in ClientProxy, NOT here. A `if (side == CLIENT)` block inside this
        // method would not help: the verifier resolves IRenderFactory / net.minecraft.client.*
        // while loading this class in FMLModContainer.constructMod, long before the guard runs,
        // and the dedicated server has no such classes. See CommonProxy.
        proxy.preInit(event);

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
                // Egg colours match the violet parasite-mage identity (texture, glow, particles).
                // Safe to change: egg colours are looked up from EntityRegistry at render time,
                // never stored in the save.
                EntitySimWizard.class, "sim_wizard", 115, this, 64, 3, true, 0x2A1033, 0x9B30D9);
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
        EntityRegistry.registerModEntity(new ResourceLocation(MODID, "corrupted_sapling"),
                com.spege.insanetweaks.entities.EntityCorruptedSapling.class, "corrupted_sapling", 116, this, 64, 3, false);
        EntityRegistry.registerModEntity(new ResourceLocation(MODID, "light_bomber_minion"),
                EntityLightBomberMinion.class, "light_bomber_minion", 117, this, 64, 3, true);
        EntityRegistry.registerModEntity(new ResourceLocation(MODID, "bomber_bomb"),
                EntityBomberBomb.class, "bomber_bomb", 118, this, 64, 10, true);
        EntityRegistry.registerModEntity(new ResourceLocation(MODID, "dispatcher_claw"),
                com.spege.insanetweaks.entities.EntityDispatcherClaw.class, "dispatcher_claw", 119, this, 64, 3, true);
        EntityRegistry.registerModEntity(new ResourceLocation(MODID, "sim_battlemage"),
                com.spege.insanetweaks.entities.EntitySimBattlemage.class, "sim_battlemage", 120, this, 64, 3, true,
                0x1A0A2E, 0xC94FD9);
        // IDs 100-120 used; next free 121.
        // 🚨 Tracking IDs are network- and save-stable: APPEND only, never reuse or reorder.
        // registerModEntity must never be gated on a config flag either - a registry object that
        // disappears from an existing world corrupts the save. Gate handlers, not registrations.
        // Never reuse/reorder network-stable IDs.


        // Immediately grant fire/explosion immunity to all Living and Sentient item drops
        // on the tick they join the world, before any explosion can hit them.
        MinecraftForge.EVENT_BUS.register(new com.spege.insanetweaks.events.IndestructibleDropHandler());

        // Quest-gate for the mod's own enchantments: the anvil refuses an unmarked gated book.
        // Registered unconditionally - the veto early-returns on its own live config flag, and this
        // must stay registered so turning the flag on needs no restart. 🚨 Server-side mechanic:
        // never move this into a Side.CLIENT block and never annotate the handler @SideOnly, or the
        // gate stops working on a dedicated server without a word in the log. The matching tooltip
        // is a separate class, EnchantGrantTooltipHandler, registered in the client block below.
        MinecraftForge.EVENT_BUS.register(new com.spege.insanetweaks.events.EnchantGrantAnvilHandler());

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

        if (com.spege.insanetweaks.config.ModConfig.modules.enableCustomCores) {
            MinecraftForge.EVENT_BUS.register(new com.spege.insanetweaks.events.CustomCoresEventHandler());
            MinecraftForge.EVENT_BUS.register(new com.spege.insanetweaks.events.CoreTooltipHandler());
        }

        // Sentient Codex enchantment runtime (boost recompute, owner-binding, anvil lock).
        // The enchantment itself registers on the MOD bus in ModEnchantments under the same flag.
        // Drop protection is conferred via the Ashen Legacy property (LegendaryDropHelper +
        // the always-on IndestructibleDropHandler above). There used to be a dedicated client
        // tooltip handler here to surface that property on enchanted vanilla items, because the
        // generic one was gated on ITweaksPropertyHolder and could not see it; GlobalPropertyTooltip-
        // Handler now goes through AdvPropertyResolver, which covers the enchant case, so the
        // duplicate was deleted.
        if (com.spege.insanetweaks.config.ModConfig.modules.enableSentientCodex) {
            MinecraftForge.EVENT_BUS.register(new com.spege.insanetweaks.enchant.SentientCodexHandler());
        }

        // One-shot per-player migration for the 1.4.21 Ashen Legacy / Sentient Codex split.
        // Registered unconditionally and independently of enableSentientCodex: an existing world
        // can hold Codex items that were lava-proof while the module was on, and turning the module
        // off must not be what decides whether they silently lose that. The handler self-gates on
        // both config flags and on a persistent per-player marker, so it is inert once it has run.
        MinecraftForge.EVENT_BUS.register(
                new com.spege.insanetweaks.events.SentientCodexAshenMigrationHandler());

        // Mmmm enchantment runtime (fill hunger + Nourished on finishing enchanted food).
        // Registered unconditionally: the handler early-returns on modules.enableMmmm, which keeps
        // that flag live-toggleable. The enchantment and the Nourished effect register on the MOD
        // bus unconditionally too, so no registry entry ever disappears from an existing world.
        MinecraftForge.EVENT_BUS.register(new com.spege.insanetweaks.enchant.MmmmHandler());

        // Mmmm carrier guard: keeps the enchantment off rot items, i.e. stops another mod's
        // interaction swapping the enchanted food for minecraft:rotten_flesh and taking the
        // enchantment with it. Same unconditional registration + live self-gating as the handler
        // above (modules.enableMmmm plus enchantments.mmmm.protectCarrierFromSwap).
        MinecraftForge.EVENT_BUS.register(new com.spege.insanetweaks.enchant.MmmmCarrierGuard());

        // Auto Lock Picker: the item and its enchantment register unconditionally on the MOD bus,
        // and all the picking logic lives in the item's own use methods (no mixin, no tick handler
        // - Locks vetoes interaction with setUseBlock(DENY), which still lets Item#onItemUse run).
        // The only thing to hook up here is the client-side channel progress bar.
        // Registered without consulting modules.enableAutoLockPicker on purpose: that flag has no
        // @Config.RequiresMcRestart, and the handler re-reads it live on every frame. Gating the
        // registration too would make turning the module back on need a restart after all.
        if (com.spege.insanetweaks.util.LocksCompat.isLoaded()
                && event.getSide() == net.minecraftforge.fml.relauncher.Side.CLIENT) {
            MinecraftForge.EVENT_BUS.register(new com.spege.insanetweaks.client.AutoLockPickerHudHandler());
        }

        if (com.spege.insanetweaks.config.ModConfig.modules.enableSrpEbWizardryBridge) {
            electroblob.wizardry.util.WandHelper.registerSpecialUpgrade(
                    com.spege.insanetweaks.init.ModItems.ADAPTATION_UPGRADE, "adaptation");
            MinecraftForge.EVENT_BUS.register(new com.spege.insanetweaks.events.SpellbladeHitHandler());
            MinecraftForge.EVENT_BUS.register(new com.spege.insanetweaks.events.FleshboundEventHandler());
            // Arcane Sundering, the Sentient Spellblade's 1900-kill reward. Registered here rather
            // than behind its own module flag because the Spellblade is the only thing that grants
            // it; arcaneSundering.enabled is read per hit, so it toggles without a restart.
            MinecraftForge.EVENT_BUS.register(new com.spege.insanetweaks.events.ArcaneSunderingHandler());
            MinecraftForge.EVENT_BUS.register(new com.spege.insanetweaks.events.WandEventHandler());
            MinecraftForge.EVENT_BUS.register(new com.spege.insanetweaks.events.ArcaneBridgeEventHandler());
            // 🚨 SpellbladeTooltipHandler is @SideOnly(Side.CLIENT) at CLASS level, so merely
            // instantiating it on a dedicated server makes Forge's SideTransformer throw
            // ("Attempted to load class ... for invalid side SERVER"). Same for ArmorTooltipHandler
            // and WandTooltipHandler below. The guard is what keeps the `new` from ever executing.
            if (event.getSide() == net.minecraftforge.fml.relauncher.Side.CLIENT) {
                MinecraftForge.EVENT_BUS.register(new com.spege.insanetweaks.events.SpellbladeTooltipHandler());
            }
            MinecraftForge.EVENT_BUS.register(new com.spege.insanetweaks.events.AttackSpeedDebugHandler());
            MinecraftForge.EVENT_BUS.register(new com.spege.insanetweaks.events.ArmorEventHandler());
            if (event.getSide() == net.minecraftforge.fml.relauncher.Side.CLIENT) {
                MinecraftForge.EVENT_BUS.register(new com.spege.insanetweaks.events.ArmorTooltipHandler());
            }
            MinecraftForge.EVENT_BUS.register(new com.spege.insanetweaks.events.BattlemageAdaptationHandler());
            if (event.getSide() == net.minecraftforge.fml.relauncher.Side.CLIENT) {
                MinecraftForge.EVENT_BUS.register(new com.spege.insanetweaks.events.BattlemageTooltipHandler());
            }
            MinecraftForge.EVENT_BUS.register(new com.spege.insanetweaks.events.AegisEventHandler());
            MinecraftForge.EVENT_BUS.register(new com.spege.insanetweaks.events.AegisTooltipHandler());
            if (event.getSide() == net.minecraftforge.fml.relauncher.Side.CLIENT) {
                MinecraftForge.EVENT_BUS.register(new com.spege.insanetweaks.events.WandTooltipHandler());
            }
            MinecraftForge.EVENT_BUS.register(com.spege.insanetweaks.baubles.ItemInfernalCrownArtefact.class);
            if (event.getSide() == net.minecraftforge.fml.relauncher.Side.CLIENT) {
                MinecraftForge.EVENT_BUS.register(new com.spege.insanetweaks.events.SpellbladeSoundHandler());
            }
        }

        // The advanced-property tooltip ("Ashen Legacy" and friends) is generic, so it must not sit
        // inside the SRP-EBWizardry bridge block: Bauble Fruits are lava-proof property holders
        // whether or not the bridge is enabled, and with the bridge off they were silently missing
        // the line that says so. Sentient Codex and Property Books are listed for the same reason -
        // each can put a property on a stack with none of the other modules on.
        if (event.getSide() == net.minecraftforge.fml.relauncher.Side.CLIENT
                && (com.spege.insanetweaks.config.ModConfig.modules.enableSrpEbWizardryBridge
                        || com.spege.insanetweaks.config.ModConfig.modules.enableBaubleFruits
                        || com.spege.insanetweaks.config.ModConfig.modules.enableSentientCodex
                        || com.spege.insanetweaks.config.ModConfig.modules.enablePropertyBooks)) {
            MinecraftForge.EVENT_BUS.register(new com.spege.insanetweaks.events.GlobalPropertyTooltipHandler());
        }

        // Property Books: the anvil recipe that grants a property to one particular item.
        // The book ITEM registers unconditionally in ModItems (a registry object behind a config
        // flag vanishes from existing worlds); this flag gates the recipe handler only.
        if (com.spege.insanetweaks.config.ModConfig.modules.enablePropertyBooks) {
            MinecraftForge.EVENT_BUS.register(new com.spege.insanetweaks.events.PropertyBookAnvilHandler());
            // Grip is enforced by the Fleshbound handler. That is normally registered under the
            // SRP-EBWizardry bridge (its other route in is an evolved Sentient Spellblade), so
            // without this a Grip book would grant a property that nothing acts on.
            if (!com.spege.insanetweaks.config.ModConfig.modules.enableSrpEbWizardryBridge) {
                MinecraftForge.EVENT_BUS.register(new com.spege.insanetweaks.events.FleshboundEventHandler());
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

        // Sanctuary Dome: veto SRP natural spawns inside an active sanctuary. LOWEST-priority
        // CheckSpawn listener, not a mixin - overrides SRP's own spawn-check result.
        if (com.spege.insanetweaks.config.ModConfig.modules.enableSanctuary
                && Loader.isModLoaded(SRP_MODID)) {
            MinecraftForge.EVENT_BUS.register(new com.spege.insanetweaks.sanctuary.SanctuarySpawnVetoHandler());
            MinecraftForge.EVENT_BUS.register(new com.spege.insanetweaks.sanctuary.SanctuaryPurgeFireHandler());
            MinecraftForge.EVENT_BUS.register(new com.spege.insanetweaks.sanctuary.SanctuaryBlockBreakVetoHandler());
            // Suppress parasite drops + XP inside the dome (no free AFK farm from the purge).
            MinecraftForge.EVENT_BUS.register(new com.spege.insanetweaks.sanctuary.SanctuaryDropVetoHandler());
            // "Cost of Power": Layer A presence tax (max-HP tithe + regen suppression).
            MinecraftForge.EVENT_BUS.register(new com.spege.insanetweaks.sanctuary.SanctuaryCostHandler());
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
            MinecraftForge.EVENT_BUS.register(new com.spege.insanetweaks.events.DispatcherGraspRootHandler());
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
            // Client half of the enchant quest-gate; the server-side veto is registered
            // unconditionally above. Both flags it reads are live, so no config gate here.
            MinecraftForge.EVENT_BUS.register(new com.spege.insanetweaks.events.EnchantGrantTooltipHandler());
            MinecraftForge.EVENT_BUS.register(new com.spege.insanetweaks.events.SpellBookGuiHandler());
            MinecraftForge.EVENT_BUS.register(new com.spege.insanetweaks.events.SentinelClientInteractionHandler());
            MinecraftForge.EVENT_BUS.register(new com.spege.insanetweaks.events.ThrallClientInteractionHandler());
            if (com.spege.insanetweaks.config.ModConfig.modules.enableSpells) {
                MinecraftForge.EVENT_BUS.register(new com.spege.insanetweaks.client.YelloweyeChargeHandler());
                MinecraftForge.EVENT_BUS.register(new com.spege.insanetweaks.client.DispatcherGraspInputHandler());
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
            MinecraftForge.EVENT_BUS.register(new com.spege.insanetweaks.skills.StoneFistsHandler());
            MinecraftForge.EVENT_BUS.register(new com.spege.insanetweaks.skills.ChargeJumpHandler());
            if (Loader.isModLoaded(SRP_MODID)) {
                MinecraftForge.EVENT_BUS.register(new com.spege.insanetweaks.skills.ScarredFleshHandler());
                // Assimilated Warfare reads SRPPotions/SRPConfigSystems/SRPSaveData. Those sit in
                // method bodies (lazy resolution), so registering without SRP would not crash - but
                // the handler cannot fire either, since it bails on any non-srparasites entity.
                MinecraftForge.EVENT_BUS.register(new com.spege.insanetweaks.events.ParasiteXPFixHandler());
            }
            if (event.getSide() == net.minecraftforge.fml.relauncher.Side.CLIENT) {
                MinecraftForge.EVENT_BUS.register(new com.spege.insanetweaks.client.ChargeJumpClientHandler());
            }
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
    // postInit
    // -------------------------------------------------------------------------

    /**
     * Cross-mod reaching-in goes here, not in init: postInit is the first phase where every other
     * mod has finished its own init, so foreign singletons are fully built.
     */
    @Mod.EventHandler
    public void postInit(net.minecraftforge.fml.common.event.FMLPostInitializationEvent event) {
        com.spege.insanetweaks.skills.EffectTwistPairs.install();
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
        if (id == GUI_ID_SANCTUARY) {
            net.minecraft.tileentity.TileEntity te = world.getTileEntity(new net.minecraft.util.math.BlockPos(x, y, z));
            if (te instanceof com.spege.insanetweaks.sanctuary.TileEntitySanctuaryCore) {
                return new com.spege.insanetweaks.sanctuary.gui.ContainerSanctuaryCore(
                        player.inventory, (com.spege.insanetweaks.sanctuary.TileEntitySanctuaryCore) te);
            }
        }
        if (id == GUI_ID_CREATIVE_SANCTUARY) {
            net.minecraft.tileentity.TileEntity te = world.getTileEntity(new net.minecraft.util.math.BlockPos(x, y, z));
            if (te instanceof com.spege.insanetweaks.sanctuary.TileEntitySanctuaryCore) {
                return new com.spege.insanetweaks.sanctuary.gui.ContainerCreativeSanctuary(
                        (com.spege.insanetweaks.sanctuary.TileEntitySanctuaryCore) te);
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
        if (id == GUI_ID_SANCTUARY) {
            net.minecraft.tileentity.TileEntity te = world.getTileEntity(new net.minecraft.util.math.BlockPos(x, y, z));
            if (te instanceof com.spege.insanetweaks.sanctuary.TileEntitySanctuaryCore) {
                return new com.spege.insanetweaks.sanctuary.gui.GuiSanctuaryCore(
                        new com.spege.insanetweaks.sanctuary.gui.ContainerSanctuaryCore(
                                player.inventory, (com.spege.insanetweaks.sanctuary.TileEntitySanctuaryCore) te));
            }
        }
        if (id == GUI_ID_CREATIVE_SANCTUARY) {
            net.minecraft.tileentity.TileEntity te = world.getTileEntity(new net.minecraft.util.math.BlockPos(x, y, z));
            if (te instanceof com.spege.insanetweaks.sanctuary.TileEntitySanctuaryCore) {
                return new com.spege.insanetweaks.sanctuary.gui.GuiCreativeSanctuary(
                        new com.spege.insanetweaks.sanctuary.gui.ContainerCreativeSanctuary(
                                (com.spege.insanetweaks.sanctuary.TileEntitySanctuaryCore) te));
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
