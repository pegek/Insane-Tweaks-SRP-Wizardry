package com.spege.srpwizcore.util;

import java.util.concurrent.atomic.AtomicInteger;

import com.spege.srpwizcore.SrpWizCore;

import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Biomes;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.biome.BiomeProvider;
import net.minecraft.world.chunk.EmptyChunk;

/**
 * The chunk {@link OffThreadChunkGuard} hands to an off-thread caller when the real one is not
 * loaded. Vanilla {@link EmptyChunk} with two overrides.
 *
 * <p>Vanilla {@code EmptyChunk} is already the right shape and was kept deliberately over
 * EntityThreading's own {@code SafeEmptyChunk}, which extends {@code Chunk} directly and
 * overrides <em>fewer</em> methods — it reports light opacity 0 for ungenerated space where
 * vanilla reports 255. Verified with {@code javap -p -c} against
 * {@code forge-1.12.2-14.23.5.2860-srg.jar}: {@code getBlockState} returns air,
 * {@code getTileEntity} returns null, {@code addEntity}/{@code removeEntity} are no-ops,
 * {@code isEmpty()} is true, {@code isLoaded()} stays false, and nothing throws.
 *
 * <p>The two things it does <em>not</em> get right for this use are fixed here:
 *
 * <ol>
 * <li>{@code setBlockState} is not overridden — by {@code EmptyChunk} or by
 * {@code SafeEmptyChunk} — so an off-thread block write runs against a throwaway object and is
 * lost <em>silently</em>. EntityThreading solved this one level up, deferring the write to its
 * {@code DeferredActionQueue}; we do not rebuild that layer, so the least we can do is make the
 * loss visible in the log instead of invisible.</li>
 * <li>{@code getBiome} is not overridden either, and {@code Chunk}'s constructor fills the biome
 * array with {@code -1}, so the first call falls through to {@code BiomeProvider} — straight into
 * the racy {@code BiomeCache} this whole guard exists to stay out of. Returning a fixed biome
 * keeps the substitute chunk inert, consistent with it reporting air for every block.</li>
 * </ol>
 */
public class SrpEmptyChunk extends EmptyChunk {

    /** Cap on lost-write lines per game run. */
    private static final int LOG_LIMIT = 20;

    private static final AtomicInteger LOST_WRITES_LOGGED = new AtomicInteger();

    public SrpEmptyChunk(World world, int x, int z) {
        super(world, x, z);
    }

    /**
     * Drops the write, as vanilla {@code EmptyChunk} silently would, but says so. Returning
     * {@code null} is what {@code Chunk.setBlockState} returns when nothing changed.
     */
    @Override
    public IBlockState setBlockState(BlockPos pos, IBlockState state) {
        if (LOST_WRITES_LOGGED.incrementAndGet() <= LOG_LIMIT) {
            SrpWizCore.LOGGER.warn(
                    "[srpwizcore] off-thread setBlockState({}) on a guarded empty chunk was "
                            + "DROPPED — thread {}, chunk {},{}. Not deferred: recovering the "
                            + "EntityThreading deferral layer is the real fix (note §6B).",
                    state, Thread.currentThread().getName(), Integer.valueOf(this.x),
                    Integer.valueOf(this.z), new Throwable("dropped off-thread block write"));
        }
        return null;
    }

    /** Never consults the {@code BiomeProvider}; that is the racy path we are avoiding. */
    @Override
    public Biome getBiome(BlockPos pos, BiomeProvider provider) {
        return Biomes.PLAINS;
    }
}
