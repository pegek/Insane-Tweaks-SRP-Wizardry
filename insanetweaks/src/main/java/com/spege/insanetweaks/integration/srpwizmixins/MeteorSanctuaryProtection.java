package com.spege.insanetweaks.integration.srpwizmixins;

import com.spege.insanetweaks.sanctuary.SanctuaryRegionHelper;
import com.spege.srpwizmixins.api.MeteorProtection;

import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/**
 * Tells SRP&amp;Wiz mixins that the parasite meteor must not land on a sanctuary.
 *
 * <p>A sanctuary is the one place in the world a player has explicitly declared theirs, so it is
 * exactly what the meteor guard's protected-area hook is for. Without this the guard still keeps the
 * meteor away from the world spawn and from beds, but a sanctuary built somewhere else - which is
 * the normal case, since the whole point is to claim ground - would be fair game.
 *
 * <p>Loaded only from {@code InsaneTweaksMod.init} behind
 * {@code Loader.isModLoaded("srpwizmixins")}, so a pack without that mod never resolves
 * {@link MeteorProtection} and nothing here has to be reachable.
 *
 * <p>The query is answered from {@code SanctuaryWorldData}, which is saved world data rather than
 * the live tile-entity registry. That matters: the meteor is aimed several hundred blocks from the
 * player, almost certainly at unloaded chunks, and the hook is called during a world tick - so a
 * sanctuary whose chunk is not loaded still has to count, and finding out must not load anything.
 * {@code SanctuaryRegionHelper.isProtected} already works that way.
 */
public final class MeteorSanctuaryProtection {

    private MeteorSanctuaryProtection() {
    }

    public static void register() {
        MeteorProtection.register(MeteorSanctuaryProtection::covers);
    }

    private static boolean covers(World world, BlockPos pos) {
        return SanctuaryRegionHelper.isProtected(world, pos);
    }
}
