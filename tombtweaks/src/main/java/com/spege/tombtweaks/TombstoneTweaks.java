package com.spege.tombtweaks;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.common.event.FMLServerStartingEvent;
import net.minecraftforge.fml.relauncher.Side;

/**
 * Tombstone Tweaks — standalone public mod carrying the Corail Tombstone module that used to live
 * inside Insane Tweaks (split out as stage R4).
 *
 * <p>What it does, all independently config-gated in
 * {@link com.spege.tombtweaks.config.TombTweaksConfig}: a whitelist for every random potion effect
 * Tombstone rolls, exact-slot inventory restore when a grave is recovered, grave item decay, the
 * Curse of Possession fix, cooldowns for the two ritual books, caps for the ten native perks, two
 * perks of its own, an alignment bridge for third-party raid mods, and a Knowledge of Death tab on
 * Reskillable's inventory strip.
 *
 * <p>Unlike this repo's other two extracted mods, this one is <b>not</b> mixin-only: it registers
 * two entries into Tombstone's {@code tombstone:perks} registry. They keep their original
 * {@code insanetweaks:} namespace on purpose — see
 * {@link com.spege.tombtweaks.perks.TombstonePerks}.
 */
@Mod(modid = TombstoneTweaks.MODID,
        name = TombstoneTweaks.NAME,
        version = TombstoneTweaks.VERSION,
        dependencies = "after:tombstone;after:enigmaticlegacy;after:reskillable;after:baubles",
        guiFactory = "com.spege.tombtweaks.client.gui.config.TombTweaksGuiFactory",
        acceptableRemoteVersions = "*")
public class TombstoneTweaks {

    public static final String MODID = "tombtweaks";
    public static final String NAME = "Tombstone Tweaks";
    public static final String VERSION = "1.4.0";

    public static final Logger LOGGER = LogManager.getLogger(MODID);

    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        // The perks are registered whatever the config says — see TombstonePerks for why a perk
        // must never appear and disappear with a flag. Gated only on Tombstone's presence, which
        // keeps TombstonePerks and the Perk type in its signature off the classloader otherwise.
        if (Loader.isModLoaded("tombstone")) {
            MinecraftForge.EVENT_BUS.register(new com.spege.tombtweaks.perks.TombstonePerks());
        }
    }

    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {
        if (!com.spege.tombtweaks.config.TombTweaksConfig.tombstone.enableTombstoneTweaks) {
            LOGGER.info("[TombstoneTweaks] Master switch is off — no handlers registered.");
            return;
        }

        if (com.spege.tombtweaks.config.TombTweaksConfig.tombstone.enableCurseOfPossessionPatch) {
            MinecraftForge.EVENT_BUS.register(new com.spege.tombtweaks.events.CurseOfPossessionHandler());
        }

        // These three identify Tombstone's items by registry name rather than by type, so they cost
        // nothing when Tombstone is absent and need no presence check.
        MinecraftForge.EVENT_BUS.register(new com.spege.tombtweaks.events.TombstoneDropEventHandler());
        MinecraftForge.EVENT_BUS.register(new com.spege.tombtweaks.events.TombstoneBooksHandler());
        MinecraftForge.EVENT_BUS.register(new com.spege.tombtweaks.events.GraveDecayHandler());

        if (Loader.isModLoaded("tombstone")) {
            // Runtime for the Assimilated Knowledge perk. The perk itself is registered in preInit
            // regardless of these flags — only its effect is gated here.
            MinecraftForge.EVENT_BUS.register(new com.spege.tombtweaks.events.AssimilatedKnowledgeHandler());

            // Alignment for the raiders of a third-party raid mod. Registered unconditionally
            // alongside it — the handler reads raiderAlignment.enabled live, and its entity list
            // simply matches nothing when the raid mod is absent.
            MinecraftForge.EVENT_BUS.register(new com.spege.tombtweaks.events.RaiderAlignmentHandler());

            // Exact slot restore: the snapshot is taken on LivingDeathEvent, the layout is
            // reapplied on Tombstone's own RestoreInventoryEvent. Both read restoreOriginalSlots
            // live.
            MinecraftForge.EVENT_BUS.register(new com.spege.tombtweaks.slots.SlotSnapshotHandler());
            MinecraftForge.EVENT_BUS.register(new com.spege.tombtweaks.slots.SlotRestoreHandler());
        }

        // Knowledge of Death tab in the inventory. Client only, and both mods must be present: the
        // handler names Reskillable AND Tombstone types in its signatures, so the branch must not
        // be taken otherwise — the JVM would resolve them on class load.
        if (com.spege.tombtweaks.config.TombTweaksConfig.tombstone.enableKnowledgeTab
                && event.getSide() == Side.CLIENT
                && Loader.isModLoaded("tombstone")
                && Loader.isModLoaded("reskillable")) {
            MinecraftForge.EVENT_BUS.register(new com.spege.tombtweaks.client.KnowledgeTabHandler());
        }
    }

    @Mod.EventHandler
    public void serverStarting(FMLServerStartingEvent event) {
        event.registerServerCommand(new com.spege.tombtweaks.commands.CommandTombTweaks());
    }
}
