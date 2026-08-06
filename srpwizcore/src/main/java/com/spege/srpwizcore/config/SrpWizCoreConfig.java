package com.spege.srpwizcore.config;

import net.minecraftforge.common.config.Config;
import net.minecraftforge.common.config.ConfigManager;
import net.minecraftforge.fml.client.event.ConfigChangedEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

import com.spege.srpwizcore.SrpWizCore;
import com.spege.srpwizcore.config.categories.BbCompatCategory;
import com.spege.srpwizcore.config.categories.CqrIntegrationCategory;
import com.spege.srpwizcore.config.categories.DormantWaystonesCategory;
import com.spege.srpwizcore.config.categories.DragonRangedCategory;
import com.spege.srpwizcore.config.categories.IandfWorldgenCategory;
import com.spege.srpwizcore.config.categories.OtgCompatCategory;
import com.spege.srpwizcore.config.categories.FutureMcCompatCategory;
import com.spege.srpwizcore.config.categories.PerfGlueCategory;
import com.spege.srpwizcore.config.categories.SpawnEngineCategory;
import com.spege.srpwizcore.config.categories.ThreadingCompatCategory;
import com.spege.srpwizcore.config.categories.WhtCompatCategory;
import com.spege.srpwizcore.util.IandfWorldgenOverrides;

@Config(modid = SrpWizCore.MODID, name = SrpWizCore.MODID, category = "")
public class SrpWizCoreConfig {

    @Config.Name("otgCompat")
    @Config.Comment("Crash guards for OpenTerrainGenerator terrain generation, plus the option to hide its world type.")
    public static final OtgCompatCategory otgCompat = new OtgCompatCategory();

    @Config.Name("futureMcCompat")
    @Config.Comment("Crash guard for FutureMC's bamboo generation.")
    public static final FutureMcCompatCategory futureMcCompat = new FutureMcCompatCategory();

    @Config.Name("threadingCompat")
    @Config.Comment("Crash and save-corruption guards for mods that tick entities or generate chunks on more than one thread.")
    public static final ThreadingCompatCategory threadingCompat = new ThreadingCompatCategory();

    @Config.Name("perfGlue")
    @Config.Comment("Performance fixes for other pack mods: Doomlike Dungeons, Chocolate Quest Repoured, Raids-Backport and Defiled Lands.")
    public static final PerfGlueCategory perfGlue = new PerfGlueCategory();

    @Config.Comment("Take over natural spawning in chosen dimensions and hold their mob population to your own limits. Off by default - the shipped values are examples.")
    @Config.Name("spawnEngine")
    public static final SpawnEngineCategory spawnEngine = new SpawnEngineCategory();

    @Config.Name("iandfWorldgen")
    @Config.Comment("Decide per dimension which Ice & Fire structures and ores generate, and how often. Empty lists = untouched Ice & Fire.")
    public static final IandfWorldgenCategory iandfWorldgen = new IandfWorldgenCategory();

    @Config.Name("cqrIntegration")
    @Config.Comment("Arm Chocolate Quest Repoured dungeon mobs with Spartan Weaponry gear and working crossbows, plus optional boss-room and siege behaviour.")
    public static final CqrIntegrationCategory cqrIntegration = new CqrIntegrationCategory();

    @Config.Name("dormantWaystones")
    @Config.Comment("Rare surface waystones that carry you to another dimension and back. Off by default.")
    public static final DormantWaystonesCategory dormantWaystones = new DormantWaystonesCategory();

    @Config.Name("dragonRanged")
    @Config.Comment("Let the elemental dragon longbows, crossbows and bows apply their element to any ammunition, not only to dragonbone arrows and bolts.")
    public static final DragonRangedCategory dragonRanged = new DragonRangedCategory();

    @Config.Name("whtCompat")
    @Config.Comment("Make invincibility frames under WorseHurtTimer deterministic, and let items grant longer ones. Covers the Cross Necklace.")
    public static final WhtCompatCategory whtCompat = new WhtCompatCategory();

    @Config.Name("bbCompat")
    @Config.Comment("Put back Bountiful Baubles trinkets that another mod silently disabled. Covers the Broken Heart, which FirstAid makes completely inert.")
    public static final BbCompatCategory bbCompat = new BbCompatCategory();

    @Mod.EventBusSubscriber(modid = SrpWizCore.MODID)
    private static class EventHandler {
        @SubscribeEvent
        public static void onConfigChanged(final ConfigChangedEvent.OnConfigChangedEvent event) {
            if (event.getModID().equals(SrpWizCore.MODID)) {
                ConfigManager.sync(SrpWizCore.MODID, Config.Type.INSTANCE);
                // Re-parse the string lists so edits apply without a restart (the mixin gate
                // itself still needs one).
                IandfWorldgenOverrides.rebuild();
                com.spege.srpwizcore.spawnengine.SpawnEngine.reload();
                com.spege.srpwizcore.whtcompat.WhtDiag.syncFromConfig(
                        whtCompat.diagEnabled, whtCompat.diagVerbose, whtCompat.diagPlayersOnly);
            }
        }
    }
}
