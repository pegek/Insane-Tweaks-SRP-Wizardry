package com.spege.srpwizcore.util;

import java.util.List;
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;

import javax.annotation.Nullable;

import com.google.common.base.Predicate;
import com.spege.srpwizcore.SrpWizCore;

import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.Entity;
import net.minecraft.init.Biomes;
import net.minecraft.init.Blocks;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.EnumSkyBlock;
import net.minecraft.world.World;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.biome.BiomeProvider;
import net.minecraft.world.chunk.Chunk;

/**
 * The chunk {@link OffThreadChunkGuard} hands to an off-thread caller when the real one is not
 * loaded: an inert stand-in that reports air everywhere and swallows every mutation.
 *
 * <p>🚨 <b>Extends {@code Chunk}, not {@code EmptyChunk}, and must stay that way.</b> Vanilla
 * {@code net.minecraft.world.chunk.EmptyChunk} carries a <em>class-level</em>
 * {@code @SideOnly(Side.CLIENT)} — it exists only to serve {@code ChunkProviderClient} — so on a
 * dedicated server Forge's {@code SideTransformer} throws
 * {@code "Attempted to load class net/minecraft/world/chunk/EmptyChunk for invalid side SERVER"}
 * the moment anything tries to load a subclass of it. This class used to extend it, which armed a
 * server-side landmine in the worst possible spot: {@code new SrpEmptyChunk(...)} sits in a method
 * body, so it resolves lazily and the server boots perfectly — then dies at the first off-thread
 * chunk generation, i.e. exactly the event this whole guard exists to survive.
 *
 * <p>Every method below is a verbatim copy of the corresponding {@code EmptyChunk} override
 * (checked against {@code forge-1.12.2-14.23.5.2860_mapped_snapshot_20171003-1.12-sources.jar}),
 * so behaviour is unchanged from when this class inherited them. The copies are load-bearing, not
 * decoration — anything omitted silently falls through to {@code Chunk}'s real implementation and
 * touches the storage arrays and world state this substitute must never touch. {@code onLoad()} is
 * the sharpest example: {@code Chunk.onLoad} sets {@code loaded = true} and pushes the chunk's
 * tile entities and entities into the world, so the no-op is what keeps the stand-in invisible.
 * {@code isAtLocation} is byte-for-byte identical to {@code Chunk}'s and is kept anyway, so that a
 * third-party mixin on {@code Chunk} cannot start applying to this class through inheritance.
 *
 * <p>{@code EmptyChunk} was chosen over EntityThreading's own {@code SafeEmptyChunk} (which also
 * extends {@code Chunk} directly) because that one overrides <em>fewer</em> methods — it reports
 * light opacity 0 for ungenerated space where vanilla reports 255.
 *
 * <p>Two things {@code EmptyChunk} does <em>not</em> get right for this use are fixed on top:
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
public class SrpEmptyChunk extends Chunk {

    /** Cap on lost-write lines per game run. */
    private static final int LOG_LIMIT = 20;

    private static final AtomicInteger LOST_WRITES_LOGGED = new AtomicInteger();

    public SrpEmptyChunk(World world, int x, int z) {
        super(world, x, z);
    }

    // --- srpwizcore additions on top of the EmptyChunk shape ------------------

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

    // --- verbatim copies of the vanilla EmptyChunk overrides ------------------

    @Override
    public boolean isAtLocation(int x, int z) {
        return x == this.x && z == this.z;
    }

    @Override
    public int getHeightValue(int x, int z) {
        return 0;
    }

    @Override
    public void generateHeightMap() {
    }

    @Override
    public void generateSkylightMap() {
    }

    @Override
    public IBlockState getBlockState(BlockPos pos) {
        return Blocks.AIR.getDefaultState();
    }

    @Override
    public int getBlockLightOpacity(BlockPos pos) {
        return 255;
    }

    @Override
    public int getLightFor(EnumSkyBlock type, BlockPos pos) {
        return type.defaultLightValue;
    }

    @Override
    public void setLightFor(EnumSkyBlock type, BlockPos pos, int value) {
    }

    @Override
    public int getLightSubtracted(BlockPos pos, int amount) {
        return 0;
    }

    @Override
    public void addEntity(Entity entityIn) {
    }

    @Override
    public void removeEntity(Entity entityIn) {
    }

    @Override
    public void removeEntityAtIndex(Entity entityIn, int index) {
    }

    @Override
    public boolean canSeeSky(BlockPos pos) {
        return false;
    }

    @Override
    @Nullable
    public TileEntity getTileEntity(BlockPos pos, Chunk.EnumCreateEntityType creationType) {
        return null;
    }

    @Override
    public void addTileEntity(TileEntity tileEntityIn) {
    }

    @Override
    public void addTileEntity(BlockPos pos, TileEntity tileEntityIn) {
    }

    @Override
    public void removeTileEntity(BlockPos pos) {
    }

    @Override
    public void onLoad() {
    }

    @Override
    public void onUnload() {
    }

    @Override
    public void markDirty() {
    }

    @Override
    public void getEntitiesWithinAABBForEntity(@Nullable Entity entityIn, AxisAlignedBB aabb,
            List<Entity> listToFill, Predicate<? super Entity> filter) {
    }

    @Override
    public <T extends Entity> void getEntitiesOfTypeWithinAABB(Class<? extends T> entityClass,
            AxisAlignedBB aabb, List<T> listToFill, Predicate<? super T> filter) {
    }

    @Override
    public boolean needsSaving(boolean unused) {
        return false;
    }

    @Override
    public Random getRandomWithSeed(long seed) {
        return new Random(this.getWorld().getSeed() + (long) (this.x * this.x * 4987142)
                + (long) (this.x * 5947611) + (long) (this.z * this.z) * 4392871L
                + (long) (this.z * 389711) ^ seed);
    }

    @Override
    public boolean isEmpty() {
        return true;
    }

    @Override
    public boolean isEmptyBetween(int startY, int endY) {
        return true;
    }
}
