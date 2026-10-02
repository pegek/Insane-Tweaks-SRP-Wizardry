package com.spege.srpwizmixins.handler;

import com.dhanantry.scapeandrunparasites.entity.ai.misc.EntityParasiteBase;
import com.dhanantry.scapeandrunparasites.world.SRPSaveData;
import com.dhanantry.scapeandrunparasites.world.SRPWorldData;
import com.dhanantry.scapeandrunparasites.world.biome.BiomeParasiteBase;
import com.spege.srpwizmixins.SrpWizMixins;
import com.spege.srpwizmixins.config.SrpWizMixinsConfig;

import net.minecraft.entity.EntityLivingBase;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.event.entity.living.LivingSpawnEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.Event;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

/**
 * Makes parasites stay near infestation sources - optionally including the base mod's own, and only
 * up to a configurable evolution phase.
 *
 * <h2>There are two spawn channels, and only one of them looks at anchors</h2>
 *
 * <p>Scape and Run: Parasites confines <em>its own</em> spawner to the area around an infestation
 * source: {@code SRPWorldParasiteSpawner} asks {@code isPosWithinOrigin} before every spawn, which
 * is what makes outbreaks local rather than global.
 *
 * <p>The other channel does not. {@code SRPSpawning} registers parasites into the <b>vanilla</b>
 * biome spawn lists, so they are produced by {@code WorldEntitySpawner} and pass through SRP's
 * {@code SRPSpawning$DimensionHandler.onSpawn} - which checks evolution phase, ID limits,
 * development level and colonies, but <b>never calls {@code isPosWithinOrigin} at all</b>.
 *
 * <p>Add-ons ({@code SRPExtra}, {@code SRPDeepSeaDanger}) use that second channel, which is where
 * this handler started. But so does the base mod, for its own entities - verified by disassembly:
 * 39 references to the biome spawn-list API in {@code SRPSpawning}, registering among others
 * {@code EntityAlafha}, {@code EntityBano}, {@code EntityCanra} and the whole {@code Fer*} family.
 *
 * <p>Measured 2026-08-16 on a fresh world: player standing on {@code minecraft:plains}, zero
 * anchors, zero colonies, zero nodes, the dimension's trigger flag true, and no parasite biome
 * within 128 blocks - with five ruptures, three sim_humans and two buglins alongside. None of those
 * could have come from the anchor-gated spawner.
 *
 * <h2>Why the gate lifts at a phase instead of applying forever</h2>
 *
 * <p>Suppressing the vanilla channel outright would fight the mod rather than fix it: evolution
 * phase is SRP's global difficulty dial, and the late game is <em>supposed</em> to stop being about
 * geography. So the gate applies below {@code originGateOpensAtPhase} and lifts at or above it.
 * Early on, an outbreak is a place you can find, avoid, or burn out; past the threshold the world
 * itself is lost, which is the point of the endgame this pack is built around.
 *
 * <p>Set the threshold to a negative number to keep the gate on at every phase.
 *
 * <h2>Failure direction</h2>
 *
 * <p>If the phase cannot be read the lookup returns {@link Integer#MIN_VALUE}, which is below any
 * threshold, so the gate stays <b>closed</b>. A broken read must not silently open the world.
 *
 * <p>{@code isPosWithinOrigin} is private, so the rule is reproduced from {@code SRPWorldData}'s
 * public API in {@link #srpwizmixins$withinOrigin}; the order of the tests mirrors the original,
 * including the last one - when a dimension's trigger flag is off, unmodified SRP allows spawning
 * everywhere, and so do we.
 *
 * <p>Spawners and spawn eggs are exempt: only natural spawning is filtered, so anything a player or
 * a structure places still works.
 */
@Mod.EventBusSubscriber(modid = SrpWizMixins.MODID)
public final class ParasiteOriginGate {

    private ParasiteOriginGate() {
    }

    /** SRP's own magic dimension id for the global save data; see {@code SRPEventHandlerBus}. */
    private static final int GLOBAL_SAVE_DATA_DIM = -549;

    // LOWEST so this runs last and a refusal cannot be turned back into an allow by another mod.
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onCheckSpawn(LivingSpawnEvent.CheckSpawn event) {
        if (!SrpWizMixinsConfig.srpCompat.addonParasitesRespectOrigins) {
            return;
        }
        // Someone already refused it - nothing to add.
        if (event.getResult() == Event.Result.DENY) {
            return;
        }
        // Mob spawners and spawn eggs are a deliberate act, not natural spawning.
        if (event.getSpawner() != null) {
            return;
        }
        EntityLivingBase entity = event.getEntityLiving();
        if (!(entity instanceof EntityParasiteBase)) {
            return;
        }
        World world = event.getWorld();
        if (world == null || world.isRemote) {
            return;
        }

        // Before the phase lookup, and before the class-name test that allocates a String: some
        // dimensions are supposed to be infested end to end and the gate has no business there.
        if (srpwizmixins$isExempt(world.provider.getDimension())) {
            return;
        }

        // Base-mod entities are told apart by package; everything else reaching this event as an
        // EntityParasiteBase came from an add-on.
        if (entity.getClass().getName().startsWith("com.dhanantry.")
                && !SrpWizMixinsConfig.srpCompat.originGateIncludesBaseMod) {
            return;
        }

        int opensAt = SrpWizMixinsConfig.srpCompat.originGateOpensAtPhase;
        if (opensAt >= 0 && srpwizmixins$phaseOf(world) >= opensAt) {
            return;
        }

        BlockPos pos = new BlockPos(event.getX(), event.getY(), event.getZ());
        if (!srpwizmixins$withinOrigin(world, pos)) {
            event.setResult(Event.Result.DENY);
        }
    }

    /**
     * True for a dimension listed in {@code Origin Gate Exempt Dimensions}.
     *
     * <p>A linear scan rather than a Set, deliberately. This runs once per parasite spawn attempt -
     * a hot path - and the list is expected to hold a handful of entries at most; walking three ints
     * beats a hash lookup and allocates nothing. It also re-reads the config field every call, so
     * the option takes effect without a restart.
     */
    private static boolean srpwizmixins$isExempt(int dimension) {
        int[] exempt = SrpWizMixinsConfig.srpCompat.originGateExemptDimensions;
        if (exempt == null) {
            return false;
        }
        for (int id : exempt) {
            if (id == dimension) {
                return true;
            }
        }
        return false;
    }

    /**
     * The dimension's evolution phase, or {@link Integer#MIN_VALUE} when it cannot be read - which
     * keeps the gate closed rather than opening the world on a failed lookup.
     */
    private static int srpwizmixins$phaseOf(World world) {
        try {
            SRPSaveData data = SRPSaveData.get(world, GLOBAL_SAVE_DATA_DIM);
            if (data == null) {
                return Integer.MIN_VALUE;
            }
            return data.getEvolutionPhase(world.provider.getDimension());
        } catch (Throwable t) {
            return Integer.MIN_VALUE;
        }
    }

    /**
     * Reconstruction of {@code SRPWorldParasiteSpawner.isPosWithinOrigin} from public API, in the
     * original's order. The last line is not a mistake: with the dimension's trigger flag off, the
     * base mod treats every position as valid, and we should not be stricter than it.
     */
    private static boolean srpwizmixins$withinOrigin(World world, BlockPos pos) {
        if (world.getBiome(pos) instanceof BiomeParasiteBase) {
            return true;
        }
        SRPWorldData data = SRPWorldData.get(world);
        if (data == null) {
            return false;
        }
        if (data.nearestColonyPosition(pos, true) != null) {
            return true;
        }
        if (data.nearestInfectionValue(pos, false) > 0) {
            return true;
        }
        return !data.getTriggerMet();
    }
}
