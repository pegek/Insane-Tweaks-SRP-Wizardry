package com.spege.insanetweaks.sanctuary;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.world.World;

/**
 * In-memory index of currently-loaded, active Sanctuary cores, keyed by dimension. Used by the
 * per-player cost handler (Layer A: max-HP tithe + regen suppression) to answer "is this position
 * inside a penalty-imposing Sanctuary?" without touching persistent WorldData or risking an
 * unloaded core TE. A TE self-registers while it ticks active and removes itself on invalidate /
 * removal / tier drop, so the index only ever holds live cores.
 */
public final class SanctuaryRegistry {

    private static final Map<Integer, List<TileEntitySanctuaryCore>> BY_DIM =
            new HashMap<Integer, List<TileEntitySanctuaryCore>>();

    private SanctuaryRegistry() { }

    public static void register(TileEntitySanctuaryCore te) {
        if (te == null || te.getWorld() == null) { return; }
        int dim = te.getWorld().provider.getDimension();
        List<TileEntitySanctuaryCore> list = BY_DIM.get(dim);
        if (list == null) { list = new ArrayList<TileEntitySanctuaryCore>(); BY_DIM.put(dim, list); }
        if (!list.contains(te)) { list.add(te); }
    }

    public static void unregister(TileEntitySanctuaryCore te) {
        if (te == null || te.getWorld() == null) { return; }
        List<TileEntitySanctuaryCore> list = BY_DIM.get(te.getWorld().provider.getDimension());
        if (list != null) { list.remove(te); }
    }

    /**
     * The active, penalty-imposing Sanctuary whose radius contains (x,z) in this world, or null.
     * A fully-ascended (U4) Sanctuary imposes no penalty, so it is skipped here. First match wins;
     * overlapping domes are rare and any containing penalty core is sufficient for Layer A.
     */
    public static TileEntitySanctuaryCore governing(World world, double x, double z) {
        if (world == null) { return null; }
        List<TileEntitySanctuaryCore> list = BY_DIM.get(world.provider.getDimension());
        if (list == null) { return null; }
        for (int i = 0; i < list.size(); i++) {
            TileEntitySanctuaryCore te = list.get(i);
            if (te.isInvalid() || te.getTier() < 1 || te.penaltiesSuppressed()) { continue; }
            int r = te.getEffectiveRadius();
            if (r <= 0) { continue; }
            double dx = x - (te.getPos().getX() + 0.5D);
            double dz = z - (te.getPos().getZ() + 0.5D);
            if (dx * dx + dz * dz <= (double) r * r) { return te; }
        }
        return null;
    }
}
