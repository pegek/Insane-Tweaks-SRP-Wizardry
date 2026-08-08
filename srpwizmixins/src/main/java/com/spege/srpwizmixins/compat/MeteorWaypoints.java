package com.spege.srpwizmixins.compat;

import java.util.Collections;

import com.spege.srpwizmixins.SrpWizMixins;

import journeymap.api.v2.common.waypoint.Waypoint;
import journeymap.api.v2.common.waypoint.WaypointFactory;
import journeymap.api.v2.server.IServerAPI;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.math.BlockPos;

/**
 * Drops a JourneyMap waypoint on a meteor impact, from the server.
 *
 * <p>Everything that names a JourneyMap type lives in this class and in
 * {@link SrpWizJourneyMapPlugin}. Callers guard on {@code Loader.isModLoaded("journeymap")} before
 * reaching either, so in a pack without it neither class is ever loaded and the missing types never
 * have to resolve.
 *
 * <p>Failures here are logged once and swallowed. A waypoint is a convenience; the chat message is
 * the actual notification, and it must not be lost because the map mod changed an API.
 */
public final class MeteorWaypoints {

    private static final String NAME = "Infestation Vector";

    /** Deep red, so it does not read as one of the player's own markers. */
    private static final int COLOUR = 0xC81E1E;

    private static boolean warned;

    private MeteorWaypoints() {
    }

    public static void push(EntityPlayerMP player, BlockPos pos, int dimension) {
        IServerAPI api = SrpWizJourneyMapPlugin.api;
        if (api == null) {
            // JourneyMap is installed but has not initialised its plugins yet, or this is a version
            // older than 6.0.2 that has no server API at all. Neither is worth a warning.
            return;
        }
        try {
            String dim = String.valueOf(dimension);
            Waypoint waypoint = WaypointFactory.createWaypoint(
                    SrpWizMixins.MODID, pos, NAME, dim, true);
            // Set explicitly rather than trusting the factory overload: the four string-ish
            // parameters are easy to get the wrong way round, and a waypoint filed under the wrong
            // dimension simply never appears.
            waypoint.setName(NAME);
            waypoint.setDimensions(Collections.singletonList(dim));
            waypoint.setPrimaryDimension(dim);
            waypoint.setColor(COLOUR);
            waypoint.setEnabled(true);
            waypoint.setPersistent(true);
            api.addPlayerWaypoint(player.getUniqueID(), waypoint);
        } catch (Throwable t) {
            if (!warned) {
                warned = true;
                SrpWizMixins.LOGGER.warn("Could not create a JourneyMap waypoint for the meteor"
                        + " impact; chat announcements are unaffected. Further failures are not"
                        + " logged.", t);
            }
        }
    }
}
