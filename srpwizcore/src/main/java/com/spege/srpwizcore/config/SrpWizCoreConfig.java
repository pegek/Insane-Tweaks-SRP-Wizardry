package com.spege.srpwizcore.config;

import net.minecraftforge.common.config.Config;
import net.minecraftforge.common.config.ConfigManager;
import net.minecraftforge.fml.client.event.ConfigChangedEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

import com.spege.srpwizcore.SrpWizCore;
import com.spege.srpwizcore.config.categories.CqrIntegrationCategory;
import com.spege.srpwizcore.config.categories.DormantWaystonesCategory;
import com.spege.srpwizcore.config.categories.IandfWorldgenCategory;
import com.spege.srpwizcore.config.categories.OtgCompatCategory;
import com.spege.srpwizcore.config.categories.FutureMcCompatCategory;
import com.spege.srpwizcore.config.categories.PerfGlueCategory;
import com.spege.srpwizcore.config.categories.SpawnEngineCategory;
import com.spege.srpwizcore.config.categories.ThreadingCompatCategory;
import com.spege.srpwizcore.util.IandfWorldgenOverrides;

@Config(modid = SrpWizCore.MODID, name = SrpWizCore.MODID, category = "")
public class SrpWizCoreConfig {

    @Config.Name("otgCompat")
    @Config.Comment("OpenTerrainGenerator structure-gen null-biome guards.")
    public static final OtgCompatCategory otgCompat = new OtgCompatCategory();

    @Config.Name("futureMcCompat")
    @Config.Comment("FutureMC bamboo worldgen race guard.")
    public static final FutureMcCompatCategory futureMcCompat = new FutureMcCompatCategory();

    @Config.Name("threadingCompat")
    @Config.Comment("Concurrency patches for threading coremods (EntityTracker, MapStorage, IntCache).")
    public static final ThreadingCompatCategory threadingCompat = new ThreadingCompatCategory();

    @Config.Name("perfGlue")
    @Config.Comment("Performance/correctness guards for third-party pack mods (Doomlike, CQR, Raids).")
    public static final PerfGlueCategory perfGlue = new PerfGlueCategory();

    @Config.Name("spawnEngine")
    @Config.Comment("SpawnEngine v1 - native per-dimension spawn control (budgets, candidate-list filtering, refill rate limiting).")
    public static final SpawnEngineCategory spawnEngine = new SpawnEngineCategory();

    @Config.Name("iandfWorldgen")
    @Config.Comment("Per-dimension control over Ice&Fire worldgen (empty = native Ice&Fire).")
    public static final IandfWorldgenCategory iandfWorldgen = new IandfWorldgenCategory();

    @Config.Name("cqrIntegration")
    @Config.Comment("CQR x Spartan Weaponry integration: dungeon-mob gear swap + crossbow ranged AI.")
    public static final CqrIntegrationCategory cqrIntegration = new CqrIntegrationCategory();

    @Config.Name("dormantWaystones")
    @Config.Comment("Dormant-waystone travel system (configurable key item + dimension pair).")
    public static final DormantWaystonesCategory dormantWaystones = new DormantWaystonesCategory();

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
            }
        }
    }
}
