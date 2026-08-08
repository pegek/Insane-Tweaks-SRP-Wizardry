package com.spege.srpwizmixins.util;

import java.util.Random;

import com.dhanantry.scapeandrunparasites.util.spawn.ParasiteSummon;
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
import net.minecraftforge.fml.common.Loader;

/**
 * Chooses where the parasite meteor lands, and tells everyone where it landed.
 *
 * <p>The two halves are independent on purpose - see {@link #place} and {@link #announce}. Both run
 * on the server thread inside a world tick.
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
     * <p>So we do not call it. The five-argument variant ends in
     * {@code spawnMeteor(srcX, srcY, srcZ, tgtX, tgtY, tgtZ, world)}, which takes no radii at all,
     * clamps nothing, and gives the impact point directly. That is what we call.
     *
     * <p>A meteor is a straight-line projectile - it accelerates along the source-to-target vector
     * and has no gravity - so aiming it is a matter of putting the source above the target and
     * letting it fly. It stops at the first block it meets, which is what makes the impact land on
     * the surface rather than at the target's Y.
     *
     * @param center       where SRP wanted it: the position of a player, preferably one under open sky
     * @param srpRadiusRand SRP's own random radius, used only when the guard is off
     * @param srpMinRadius  SRP's own minimum radius, used only when the guard is off
     */
    public static void place(BlockPos center, int srpRadiusRand, int srpMinRadius, World world) {
        if (!SrpWizMixinsConfig.srpCompat.meteorPlacementGuard) {
            ParasiteSummon.spawnMeteor(center, srpRadiusRand, srpMinRadius, world);
            return;
        }

        BlockPos target = pickTarget(center, world);

        int offset = SrpWizMixinsConfig.srpCompat.meteorEntryOffset;
        int sourceY = Math.max(world.getHeight(), target.getY() + MIN_DROP);
        int sourceX = target.getX();
        int sourceZ = target.getZ();
        if (offset > 0) {
            double angle = world.rand.nextDouble() * Math.PI * 2.0D;
            sourceX += (int) Math.round(Math.cos(angle) * offset);
            sourceZ += (int) Math.round(Math.sin(angle) * offset);
        }

        if (SrpWizMixinsConfig.srpCompat.debugLogging) {
            SrpWizMixins.LOGGER.info("Meteor guard: dim {} aiming at {}, {}, {} (SRP wanted within {}"
                    + " of {}, {}, {}), entering from {}, {}, {}",
                    world.provider.getDimension(), target.getX(), target.getY(), target.getZ(),
                    srpMinRadius + srpRadiusRand, center.getX(), center.getY(), center.getZ(),
                    sourceX, sourceY, sourceZ);
        }

        ParasiteSummon.spawnMeteor(sourceX, sourceY, sourceZ,
                target.getX(), target.getY(), target.getZ(), world);
    }

    /**
     * Picks an impact point in a ring around {@code center}, widening the ring every time a run of
     * attempts comes back protected.
     *
     * <p>Y is left at the player's, exactly as SRP does it. Reading the real surface height would
     * mean {@code World.getHeight(x, z)} on a position several hundred blocks away, which loads -
     * and quite possibly generates - a chunk in the middle of a world tick. The projectile finds the
     * surface by itself on the way down, so the value only matters as an aiming direction.
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
        long dx = pos.getX() - other.getX();
        long dz = pos.getZ() - other.getZ();
        return dx * dx + dz * dz <= limitSquared;
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
