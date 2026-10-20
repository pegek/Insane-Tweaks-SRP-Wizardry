package com.spege.insanetweaks.worldgen.sanctum;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.spege.insanetweaks.init.ModBlocks;

import net.minecraft.init.Blocks;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.World;
import net.minecraftforge.event.world.ChunkEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

/**
 * Dissolves a cleansed sanctum's arcane-wall hull into stone (stone, not air: no one-wide pockets
 * for mobs to spawn in). Loaded chunks go at once; a chunk that was unloaded at the cleanse is
 * queued when it loads and swept on the next world tick, never inside the load event. The sanctum
 * record remembers swept chunks, so each is scanned at most once after the cleanse.
 */
public class SanctumWalls {

    private static final Map<Integer, Set<Long>> QUEUE = new HashMap<Integer, Set<Long>>();

    /** Called when a sanctum clears: sweep every chunk of it that is loaded right now. */
    public static void dissolveLoaded(World world, long id) {
        if (world.isRemote) {
            return;
        }
        int[] b = SanctumWorldData.get(world).box(id);
        if (b == null) {
            return;
        }
        for (int cx = b[0] >> 4; cx <= b[2] >> 4; cx++) {
            for (int cz = b[1] >> 4; cz <= b[3] >> 4; cz++) {
                if (world.getChunkProvider().getLoadedChunk(cx, cz) != null) {
                    sweep(world, id, cx, cz, b);
                }
            }
        }
    }

    private static void sweep(World world, long id, int cx, int cz, int[] b) {
        int x0 = Math.max(b[0], cx << 4);
        int x1 = Math.min(b[2], (cx << 4) + 15);
        int z0 = Math.max(b[1], cz << 4);
        int z1 = Math.min(b[3], (cz << 4) + 15);
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int x = x0; x <= x1; x++) {
            for (int z = z0; z <= z1; z++) {
                for (int y = b[4]; y <= b[5]; y++) {
                    pos.setPos(x, y, z);
                    if (world.getBlockState(pos).getBlock() == ModBlocks.ARCANE_WALL) {
                        world.setBlockState(pos, Blocks.STONE.getDefaultState(), 2);
                    }
                }
            }
        }
        SanctumWorldData.get(world).markSwept(id, ChunkPos.asLong(cx, cz));
    }

    @SubscribeEvent
    public void onChunkLoad(ChunkEvent.Load event) {
        World world = event.getWorld();
        if (world.isRemote) {
            return;
        }
        int cx = event.getChunk().x;
        int cz = event.getChunk().z;
        if (SanctumWorldData.get(world).clearedNeedingSweep(cx, cz).isEmpty()) {
            return;
        }
        synchronized (QUEUE) {
            Integer dim = Integer.valueOf(world.provider.getDimension());
            Set<Long> chunks = QUEUE.get(dim);
            if (chunks == null) {
                chunks = new LinkedHashSet<Long>();
                QUEUE.put(dim, chunks);
            }
            chunks.add(Long.valueOf(ChunkPos.asLong(cx, cz)));
        }
    }

    @SubscribeEvent
    public void onWorldTick(TickEvent.WorldTickEvent event) {
        if (event.phase != TickEvent.Phase.END || event.world.isRemote) {
            return;
        }
        List<Long> chunks;
        synchronized (QUEUE) {
            Set<Long> queued = QUEUE.remove(Integer.valueOf(event.world.provider.getDimension()));
            if (queued == null) {
                return;
            }
            chunks = new ArrayList<Long>(queued);
        }
        SanctumWorldData data = SanctumWorldData.get(event.world);
        for (Long packed : chunks) {
            int cx = (int) packed.longValue();
            int cz = (int) (packed.longValue() >>> 32);
            if (event.world.getChunkProvider().getLoadedChunk(cx, cz) == null) {
                continue;
            }
            for (Long id : data.clearedNeedingSweep(cx, cz)) {
                int[] b = data.box(id.longValue());
                if (b != null) {
                    sweep(event.world, id.longValue(), cx, cz, b);
                }
            }
        }
    }
}
