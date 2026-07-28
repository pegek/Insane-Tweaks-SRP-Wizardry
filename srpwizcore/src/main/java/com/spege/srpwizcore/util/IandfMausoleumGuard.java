package com.spege.srpwizcore.util;

import com.github.alexthe666.iceandfire.structures.WorldGenMausoleum;
import com.spege.srpwizcore.SrpWizCore;
import com.spege.srpwizcore.config.SrpWizCoreConfig;

import net.minecraft.block.state.IBlockState;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/**
 * Tolerant replacement for {@code WorldGenMausoleum.checkIfCanGenAt}, called by
 * {@code MixinIandfWorldGenMausoleum}.
 *
 * <p>The vanilla check (javap on Ice&amp;Fire 2.2.9, 2026-07-28) samples the block directly below
 * the four edge midpoints of the template footprint and passes only when ALL FOUR are opaque
 * cubes at exactly origin-Y−1. On anything but perfectly flat ground one edge sits over air and
 * the whole attempt dies — measured live: 0 mausoleums in 20 217 chunks (2026-07-21) and 0 in
 * 5 941 chunks of the flattened-WinterDread world (2026-07-28). The structure does not need the
 * strictness: its own generate() pours a 3-deep dread-stone foundation box after placement.
 *
 * <p>Tolerant mode keeps the shape of the vanilla check (same four sample columns, same overlap
 * veto) but accepts an edge whose first opaque block lies up to {@code tolerance} blocks below
 * origin level, i.e. it allows the footprint to hang over a gentle slope.
 */
public final class IandfMausoleumGuard {

    /** Counts vetoes/passes for the throttled diag line. */
    public static int passes;
    public static int rejects;

    private IandfMausoleumGuard() {
    }

    public static boolean check(WorldGenMausoleum gen, World world, BlockPos pos,
            int xSize, int zSize, EnumFacing facing) {
        int tolerance = SrpWizCoreConfig.iandfWorldgen.mausoleumFoundationTolerance;
        if (tolerance <= 0) {
            return gen.checkIfCanGenAt(world, pos, xSize, zSize, facing);
        }
        BlockPos[] edges = new BlockPos[] {
                pos.offset(facing, zSize / 2),
                pos.offset(facing.rotateY(), xSize / 2),
                pos.offset(facing.getOpposite(), zSize / 2),
                pos.offset(facing.rotateYCCW(), xSize / 2),
        };
        boolean ok = true;
        for (int i = 0; i < edges.length && ok; i++) {
            boolean found = false;
            for (int dy = 1; dy <= 1 + tolerance; dy++) {
                IBlockState state = world.getBlockState(edges[i].down(dy));
                if (WorldGenMausoleum.isPartOfMausoleum(state)) {
                    // Same overlap veto as vanilla: never build into an existing mausoleum.
                    return false;
                }
                if (state.isOpaqueCube()) {
                    found = true;
                    break;
                }
            }
            ok = found;
        }
        if (ok) {
            passes++;
            SrpWizCore.LOGGER.info("[srpwizcore] Mausoleum foundation check PASSED at {} "
                    + "(tolerance {}, total passes {})", pos, Integer.valueOf(tolerance),
                    Integer.valueOf(passes));
        } else {
            int n = ++rejects;
            if (n == 1 || (n & 0x0F) == 0) {
                SrpWizCore.LOGGER.info("[srpwizcore] Mausoleum foundation check rejected at {} "
                        + "even with tolerance {} (total rejects {})", pos,
                        Integer.valueOf(tolerance), Integer.valueOf(n));
            }
        }
        return ok;
    }
}
