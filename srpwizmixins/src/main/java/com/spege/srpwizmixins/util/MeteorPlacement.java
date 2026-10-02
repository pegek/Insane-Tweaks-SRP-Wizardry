package com.spege.srpwizmixins.util;

import java.util.Random;

import com.dhanantry.scapeandrunparasites.util.ParasiteEventWorld;
import com.dhanantry.scapeandrunparasites.util.config.SRPConfigWorld;
import com.dhanantry.scapeandrunparasites.util.spawn.ParasiteSummon;
import com.dhanantry.scapeandrunparasites.world.gen.feature.WorldGenParasiteMeteorCrash;
import com.spege.srpwizmixins.SrpWizMixins;
import com.spege.srpwizmixins.api.MeteorProtection;
import com.spege.srpwizmixins.compat.MeteorWaypoints;
import com.spege.srpwizmixins.config.SrpWizMixinsConfig;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.world.World;
import net.minecraft.world.gen.feature.WorldGenerator;
import net.minecraftforge.fml.common.Loader;

/**
 * Chooses where the parasite meteor lands, and tells everyone where it landed.
 *
 * <p>The three parts are independent on purpose - see {@link #place}, {@link #crash} and
 * {@link #announce}. All of them run on the server thread inside a world tick.
 */
public final class MeteorPlacement {

    /**
     * How many positions to try before giving up on finding an unprotected one. Each attempt is a
     * handful of arithmetic plus one query per registered provider, and this runs once per meteor -
     * which is once per world, ever - so the number can afford to be generous.
     */
    private static final int ATTEMPTS = 48;

    /**
     * Every eighth failed attempt pushes the search a whole spread further out. Without this a base
     * that happens to sit in the middle of the ring would send all 48 attempts into the same
     * protected area and the fallback would be a coin flip.
     */
    private static final int WIDEN_EVERY = 8;

    /** The meteor is aimed from at least this far above the target, whatever the world height says. */
    private static final int MIN_DROP = 120;

    /**
     * The crater size SRP uses for a root meteor. {@code EntityMeteor.onImpact} builds its generator
     * as {@code new WorldGenParasiteMeteorCrash(false, getRoot() ? 5 : 1)} - 5 is the real crash, 1
     * is one of the small fragments that break off the trail. {@link #crash} only ever stands in for
     * the root one.
     */
    private static final int ROOT_CRATER = 5;

    private MeteorPlacement() {
    }

    /**
     * Replaces SRP's own placement.
     *
     * <p>Unmodified SRP calls {@code ParasiteSummon.spawnMeteor(playerPos, rand(meteorRadius),
     * meteorMinRadius, world)}, and that method clamps both radii against the same two config values
     * before use - so the impact point can never be more than {@code meteorMinRadius +
     * meteorRadius} away, and Forge caps those at 110 and 120. Raising them is not an option and
     * passing bigger numbers in does not work either.
     *
     * <p>So we do not call it. Instead {@link #pickTarget} chooses the impact point and one of two
     * deliveries carries it out:
     *
     * <ul>
     * <li>{@link #launch} for a target close enough to stay loaded - SRP's own projectile, flown at
     *     the position we picked.
     * <li>{@link #crash} for anything further out - the impact applied directly, with no entity.
     * </ul>
     *
     * <p>The split exists because a meteor is an entity, and an entity in an unloaded chunk does not
     * tick. Aim one 400 blocks away and it is spawned, the chunk it is in unloads a moment later,
     * and it is written to disk in mid-flight. It resumes - and detonates - when a player next walks
     * into that chunk. Measured on 2026-08-17: a meteor aimed 250-600 blocks out went off twelve
     * blocks from the player eleven minutes later. Aiming a meteor out of range turns it from a
     * hazard into a landmine, which is worse than the problem the guard was written to fix.
     */
    public static void place(BlockPos center, int srpRadiusRand, int srpMinRadius, World world) {
        if (!SrpWizMixinsConfig.srpCompat.meteorPlacementGuard) {
            ParasiteSummon.spawnMeteor(center, srpRadiusRand, srpMinRadius, world);
            return;
        }

        BlockPos target = pickTarget(center, world);
        int range = SrpWizMixinsConfig.srpCompat.meteorProjectileRange;
        boolean fly = range > 0 && horizontalSquared(center, target) <= (long) range * range;

        // Unconditional, not gated on debugLogging: a meteor falls once per world, so this is one
        // line in the whole log, and it is the only record of where the outbreak was aimed and how
        // it was delivered. Gating it behind a debug flag meant the one time we needed it - working
        // out why a meteor had gone off in the wrong place - it was not there.
        SrpWizMixins.LOGGER.info("Meteor guard: dim {} aiming at {}, {}, {} - {} blocks out, {}"
                + " (SRP wanted within {} of {}, {}, {})",
                world.provider.getDimension(), target.getX(), target.getY(), target.getZ(),
                Math.round(Math.sqrt(horizontalSquared(center, target))),
                fly ? "flying it in" : "applying the impact directly",
                srpMinRadius + srpRadiusRand, center.getX(), center.getY(), center.getZ());

        if (fly) {
            launch(target, world);
        } else {
            crash(target, world);
        }
    }

    /**
     * Spawns SRP's meteor entity above the target and lets it fall.
     *
     * <p>The four-argument {@code spawnMeteor} clamps its radii; the six-integer variant it ends in
     * takes the impact point outright and clamps nothing, so that is the one we call.
     *
     * <p>A meteor is a straight-line projectile - it accelerates along the source-to-target vector
     * and has no gravity - so aiming it is a matter of putting the source above the target and
     * letting it fly. It stops at the first block it meets, which is what makes the impact land on
     * the surface rather than at the target's Y.
     */
    private static void launch(BlockPos target, World world) {
        int offset = SrpWizMixinsConfig.srpCompat.meteorEntryOffset;
        int sourceY = Math.max(world.getHeight(), target.getY() + MIN_DROP);
        int sourceX = target.getX();
        int sourceZ = target.getZ();
        if (offset > 0) {
            double angle = world.rand.nextDouble() * Math.PI * 2.0D;
            sourceX += (int) Math.round(Math.cos(angle) * offset);
            sourceZ += (int) Math.round(Math.sin(angle) * offset);
        }
        ParasiteSummon.spawnMeteor(sourceX, sourceY, sourceZ,
                target.getX(), target.getY(), target.getZ(), world);
    }

    /**
     * Applies the impact where the projectile could not safely be flown.
     *
     * <p>This is {@code EntityMeteor.onImpact} minus its entity, and it does the two things that
     * outlive the explosion, in the same order:
     *
     * <ol>
     * <li>the crater, {@code new WorldGenParasiteMeteorCrash(false, 5)} generated at the impact
     *     point;
     * <li>the infestation anchor, {@code ParasiteEventWorld.placeOriginInWorld} with SRP's own
     *     configured health and radius, and only when SRP has anchors switched on at all.
     * </ol>
     *
     * <p>What it deliberately leaves out is everything that only exists for whoever is watching: the
     * {@code EntityOrbBoom} explosion, the 400-block screen shake, the damage roll within
     * {@code Meteor Damage} blocks and the contagion effect within 800. All four are keyed to
     * distance from the impact, and this path only runs when the impact is further away than a
     * loaded chunk - so all four would find nobody. Spawning the explosion entity in particular
     * would recreate the exact bug this path exists to avoid.
     *
     * <p>The Y is resolved here rather than passed in, because {@link #pickTarget} has no business
     * touching terrain (see its note). {@code getTopSolidOrLiquidBlock} is the same lookup the
     * crater generator does internally, and it is where the entity would have stopped falling.
     *
     * <p><b>This is not free.</b> The crater generator reads and writes blocks without checking
     * whether their chunk is loaded, over a footprint that widens as it climbs, so calling it out in
     * unvisited terrain loads - and where necessary generates - every chunk it reaches. That is a
     * one-off stall of a second or several, once per meteor. It is the price of not leaving a live
     * entity in a chunk nobody is keeping loaded, and it is why {@code Meteor Projectile Range} is
     * set to keep the ordinary case on the {@link #launch} path.
     *
     * <p>{@link #announce} is called from here because the announcement normally rides on
     * {@code onImpact}, which never runs when there is no entity.
     */
    private static void crash(BlockPos target, World world) {
        BlockPos impact = world.getTopSolidOrLiquidBlock(target);

        // Typed as the vanilla WorldGenerator on purpose. SRP sits on this compile classpath as a
        // production jar - fg.deobf(files(...)) is a no-op here - so the override it declares is
        // named func_180709_b, and calling it through the SRP type would compile to a name that
        // reobfuscation then leaves alone. Going through the vanilla supertype makes it an ordinary
        // MCP call that reobf maps, and virtual dispatch still lands on SRP's override.
        WorldGenerator crater = new WorldGenParasiteMeteorCrash(false, ROOT_CRATER);
        crater.generate(world, world.rand, impact);

        if (SRPConfigWorld.originActivated) {
            ParasiteEventWorld.placeOriginInWorld(world, impact,
                    SRPConfigWorld.originHealth, SRPConfigWorld.originRadius);
        }

        SrpWizMixins.LOGGER.info("Meteor guard: dim {} impact resolved to the surface at {}, {}, {}",
                world.provider.getDimension(), impact.getX(), impact.getY(), impact.getZ());

        announce(world, impact);
    }

    /**
     * Picks an impact point in a ring around {@code center}, widening the ring every time a run of
     * attempts comes back protected.
     *
     * <p>Y is left at the player's, exactly as SRP does it. Reading the real surface height would
     * mean {@code World.getHeight(x, z)} on a position several hundred blocks away, which loads -
     * and quite possibly generates - a chunk in the middle of a world tick, on a candidate that may
     * well be discarded on the next line. Whichever delivery is chosen resolves the surface itself.
     *
     * <p>If every attempt is protected the last one is used anyway. Refusing to place the meteor
     * would silently disable the whole outbreak mechanic on a world that is heavily built up, and
     * "the meteor lands somewhere awkward" is a far smaller failure than "the meteor never falls".
     */
    private static BlockPos pickTarget(BlockPos center, World world) {
        Random rand = world.rand;
        int minDistance = SrpWizMixinsConfig.srpCompat.meteorMinDistance;
        int spread = Math.max(1, SrpWizMixinsConfig.srpCompat.meteorDistanceSpread);

        BlockPos candidate = center;
        for (int attempt = 0; attempt < ATTEMPTS; attempt++) {
            double angle = rand.nextDouble() * Math.PI * 2.0D;
            int distance = minDistance + rand.nextInt(spread + 1)
                    + (attempt / WIDEN_EVERY) * spread;
            candidate = new BlockPos(
                    center.getX() + (int) Math.round(Math.cos(angle) * distance),
                    center.getY(),
                    center.getZ() + (int) Math.round(Math.sin(angle) * distance));
            if (!isProtected(world, candidate)) {
                return candidate;
            }
        }

        SrpWizMixins.LOGGER.warn("Meteor guard: every one of {} candidate positions around {}, {}, {}"
                + " in dim {} was protected; using the last one anyway", ATTEMPTS,
                center.getX(), center.getY(), center.getZ(), world.provider.getDimension());
        return candidate;
    }

    /** True when the position is too close to anything the player is not supposed to lose. */
    private static boolean isProtected(World world, BlockPos pos) {
        int radius = SrpWizMixinsConfig.srpCompat.meteorProtectedRadius;
        if (radius > 0 && SrpWizMixinsConfig.srpCompat.meteorProtectSpawnpoint) {
            long limit = (long) radius * radius;
            if (withinSquared(pos, world.getSpawnPoint(), limit)) {
                return true;
            }
            for (EntityPlayer player : world.playerEntities) {
                if (withinSquared(pos, player.getBedLocation(), limit)) {
                    return true;
                }
            }
        }
        return MeteorProtection.hasProviders() && MeteorProtection.isProtected(world, pos);
    }

    /** Horizontal distance only - a base is a footprint, and the meteor's Y is not meaningful yet. */
    private static boolean withinSquared(BlockPos pos, BlockPos other, long limitSquared) {
        if (other == null) {
            return false;
        }
        return horizontalSquared(pos, other) <= limitSquared;
    }

    /** Squared horizontal distance. Long arithmetic - these are world coordinates, not offsets. */
    private static long horizontalSquared(BlockPos a, BlockPos b) {
        long dx = a.getX() - b.getX();
        long dz = a.getZ() - b.getZ();
        return dx * dx + dz * dz;
    }

    /**
     * Announces a new infestation anchor to everyone in the world it formed in.
     *
     * <p>Called from the meteor's impact rather than from {@link #place}, so the coordinates are the
     * ones it actually hit, not the ones it was aimed at - and so the message arrives when the
     * outbreak exists rather than several seconds before. It is also why this works with the
     * placement guard switched off.
     *
     * <p>Each player gets their own bearing and distance, measured from where they are standing.
     */
    public static void announce(World world, BlockPos impact) {
        if (world == null || world.isRemote || impact == null) {
            return;
        }
        boolean chat = SrpWizMixinsConfig.srpCompat.meteorAnnounceInChat;
        boolean waypoint = SrpWizMixinsConfig.srpCompat.meteorWaypoint
                && Loader.isModLoaded("journeymap");
        if (!chat && !waypoint) {
            return;
        }

        for (EntityPlayer player : world.playerEntities) {
            if (!(player instanceof EntityPlayerMP)) {
                continue;
            }
            EntityPlayerMP mp = (EntityPlayerMP) player;
            if (chat) {
                mp.sendMessage(new TextComponentString(
                        TextFormatting.DARK_RED + "An infestation vector has formed at "
                                + TextFormatting.RED + impact.getX() + ", " + impact.getY() + ", "
                                + impact.getZ() + TextFormatting.DARK_RED + " - "
                                + describeOffset(mp, impact) + "."));
            }
            if (waypoint) {
                MeteorWaypoints.push(mp, impact, world.provider.getDimension());
            }
        }
    }

    /** "430 blocks north-east", measured from the player. */
    private static String describeOffset(EntityPlayer player, BlockPos impact) {
        double dx = impact.getX() - player.posX;
        double dz = impact.getZ() - player.posZ;
        long distance = Math.round(Math.sqrt(dx * dx + dz * dz));
        return distance + " blocks " + compass(dx, dz);
    }

    /**
     * Eight-point compass. Minecraft's north is -Z and east is +X, so measuring the angle from -Z
     * towards +X puts zero on north and makes the sectors fall in clockwise order.
     */
    private static String compass(double dx, double dz) {
        String[] points = { "north", "north-east", "east", "south-east",
                            "south", "south-west", "west", "north-west" };
        double degrees = Math.toDegrees(Math.atan2(dx, -dz));
        int sector = (int) Math.floor(((degrees + 360.0D) % 360.0D) / 45.0D + 0.5D) % 8;
        return points[sector];
    }
}
