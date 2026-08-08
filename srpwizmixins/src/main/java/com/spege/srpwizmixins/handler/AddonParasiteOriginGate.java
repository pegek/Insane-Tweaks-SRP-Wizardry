package com.spege.srpwizmixins.handler;

import com.dhanantry.scapeandrunparasites.entity.ai.misc.EntityParasiteBase;
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
 * Makes SRParasites <em>add-on</em> mobs obey the same infestation-anchor rule as the base mod's own.
 *
 * <p>Scape and Run: Parasites confines natural parasite spawning to the area around an infestation
 * source. Its custom spawner asks {@code SRPWorldParasiteSpawner.isPosWithinOrigin} before every
 * spawn, so away from any anchor the world stays clean - that is what makes "outbreaks" local
 * rather than global.
 *
 * <p>Add-ons do not go through that spawner at all. {@code energon.srpextra.init.SRPEPhases}
 * registers their mobs straight into the <em>vanilla</em> biome spawn lists
 * ({@code biome.getSpawnableList(type).add(new Biome$SpawnListEntry(...))}), so they are spawned by
 * {@code WorldEntitySpawner} and never see the anchor check. Neither SRPExtra nor SRPDeepSeaDanger
 * contains a single reference to {@code isPosWithinOrigin}, {@code nearestInfectionValue} or
 * {@code getTriggerMet}.
 *
 * <p>Measured 2026-08-08 on a fresh world: 3311 blocks from the only anchor (radius 471) there were
 * 14 {@code swimmer} + 2 {@code sim_fish} within 160 blocks of the player, while dry land at a
 * comparable distance held <b>zero</b>. After a purge the water population returned from 0 to 29 in
 * about three minutes with only two squid and one fish alive nearby - far too few to have been
 * converted, so they were being spawned. Every body of water in the world behaves like an outbreak.
 *
 * <p>This handler denies a natural spawn of an add-on parasite whose position fails the same check
 * the base mod applies. {@code isPosWithinOrigin} is private, so the rule is reproduced from
 * {@code SRPWorldData}'s public API in {@link #srpwizmixins$withinOrigin}; the order of the tests
 * mirrors the original, including the final one - when a dimension's trigger flag is off, unmodified
 * SRP allows spawning everywhere, and so do we.
 *
 * <p><b>Base-mod parasites are deliberately left alone.</b> They already pass through SRP's own
 * spawner, and its private check takes two extra flags whose call sites compute them; re-applying our
 * reconstruction on top could refuse spawns the mod meant to allow. Add-on classes are told apart by
 * package: everything under {@code com.dhanantry} is the base mod, anything else reaching this event
 * as an {@code EntityParasiteBase} came from an add-on ({@code energon.srpextra},
 * {@code energon.srpdeepseadanger}, ...). All add-on entities do descend from
 * {@code EntityParasiteBase} - verified through {@code SRPEPFeral} -> {@code EntityPFeral},
 * {@code SRPEPAdapted} -> {@code EntityPAdapted}, and {@code EntityRyba} directly.
 *
 * <p>Spawners and spawn eggs are exempt: only natural spawning is filtered, so anything a player or a
 * structure places still works.
 *
 * <p>Gated on {@code srpCompat.addonParasitesRespectOrigins}, default OFF.
 */
@Mod.EventBusSubscriber(modid = SrpWizMixins.MODID)
public final class AddonParasiteOriginGate {

    private AddonParasiteOriginGate() {
    }

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
        // Base mod handles its own; see the class javadoc for why we must not double-check it.
        if (entity.getClass().getName().startsWith("com.dhanantry.")) {
            return;
        }
        World world = event.getWorld();
        if (world == null || world.isRemote) {
            return;
        }
        BlockPos pos = new BlockPos(event.getX(), event.getY(), event.getZ());
        if (!srpwizmixins$withinOrigin(world, pos)) {
            event.setResult(Event.Result.DENY);
        }
    }

    /**
     * Reconstruction of {@code SRPWorldParasiteSpawner.isPosWithinOrigin} from public API, in the
     * original's order. The last line is not a mistake: with the dimension's trigger flag off, the
     * base mod treats every position as valid, and add-ons should not be stricter than it.
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
