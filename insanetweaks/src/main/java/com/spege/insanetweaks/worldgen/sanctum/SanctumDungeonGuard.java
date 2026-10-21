package com.spege.insanetweaks.worldgen.sanctum;

import com.spege.insanetweaks.config.ModConfig;

import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.World;

/**
 * Keeps other mods' big dungeons from generating into a sanctum: they overwrite its walls and the
 * arcane-wall hull and could let the parasites out. Called from a mixin on the dungeon mod's world
 * generator (MixinRoguelikeDungeonGenerator) with the chunk it is about to build a dungeon from.
 */
public final class SanctumDungeonGuard {

    /**
     * Chunks between a sanctum start and a foreign dungeon's start chunk: the sanctum reaches about
     * two chunks from its start, a Roguelike Dungeon several more from its own.
     */
    private static final int RADIUS = 8;

    private SanctumDungeonGuard() {
    }

    /** Whether a dungeon built from this chunk could reach a sanctum that exists or may still generate. */
    public static boolean blocks(World world, int chunkX, int chunkZ) {
        if (world == null || world.isRemote || !ModConfig.arcaneSanctum.enabled
                || !ModConfig.arcaneSanctum.blockForeignDungeons || !SanctumPlacement.dimensionAllowed(world)) {
            return false;
        }
        int[] start = SanctumRegions.startNear(world.getSeed(), chunkX, chunkZ, RADIUS,
                ModConfig.arcaneSanctum.regionSize, SanctumPlacement.MARGIN, ModConfig.arcaneSanctum.chance);
        if (start == null) {
            return false;
        }
        // A start already judged invalid (biome, integrity) holds no sanctum; undecided ones may.
        Boolean decided = SanctumWorldData.get(world).decision(ChunkPos.asLong(start[0], start[1]));
        return decided == null || decided.booleanValue();
    }
}
