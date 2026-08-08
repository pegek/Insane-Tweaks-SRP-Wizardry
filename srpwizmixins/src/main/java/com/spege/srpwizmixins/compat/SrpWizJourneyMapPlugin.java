package com.spege.srpwizmixins.compat;

import com.spege.srpwizmixins.SrpWizMixins;

import journeymap.api.v2.common.JourneyMapPlugin;
import journeymap.api.v2.server.IServerAPI;
import journeymap.api.v2.server.IServerPlugin;

/**
 * Picks up JourneyMap's server-side API so {@link MeteorWaypoints} has something to push through.
 *
 * <p>JourneyMap finds this class itself: it scans the annotation table for {@code @JourneyMapPlugin}
 * on implementations of its plugin interface and instantiates whatever it finds. Nothing in this mod
 * references this class, which is the point - in a pack without JourneyMap nobody scans for it,
 * nobody loads it, and its unresolvable interface never becomes a problem.
 *
 * <p>The version string is not checked by JourneyMap 6.0.2 (its range test returns true
 * unconditionally); it is declared honestly anyway so a future release that does check has something
 * sensible to compare against.
 *
 * <p>Server-side only, and that is the whole reason this approach is worth having. The obvious
 * alternative - a custom packet carrying the coordinates to a client-side handler - would mean a
 * network channel, a message class, and the usual trap of a packet handler that must not be
 * {@code @SideOnly}. The server API does the same job with none of it.
 */
@JourneyMapPlugin(apiVersion = "2.0.0")
public class SrpWizJourneyMapPlugin implements IServerPlugin {

    /** Set once, on JourneyMap's own thread, during its plugin initialisation. */
    static volatile IServerAPI api;

    @Override
    public String getModId() {
        return SrpWizMixins.MODID;
    }

    @Override
    public void initialize(IServerAPI serverApi) {
        api = serverApi;
        SrpWizMixins.LOGGER.info("JourneyMap server API acquired; meteor waypoints are available");
    }
}
