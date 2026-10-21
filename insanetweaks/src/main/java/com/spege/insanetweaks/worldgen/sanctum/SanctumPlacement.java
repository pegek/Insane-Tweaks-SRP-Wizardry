package com.spege.insanetweaks.worldgen.sanctum;

import java.util.LinkedHashMap;
import java.util.Map;

import com.spege.insanetweaks.config.ModConfig;
import com.spege.insanetweaks.config.categories.ArcaneSanctumCategory;

import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.World;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.gen.ChunkProviderServer;
import net.minecraftforge.common.BiomeDictionary;

/** Where sanctums go and whether a start may be built: dimension, biome, integrity, plus a layout cache. */
public final class SanctumPlacement {

    /** Starts further than this (chunks) from a writer chunk cannot reach it (footprint is ±26 blocks). */
    public static final int REACH = 3;
    public static final int MARGIN = 4;
    private static final int NO_SRP_DIMENSION = 150;
    private static final int CACHE_SIZE = 32;

    private static final Map<String, SanctumLayout> CACHE = new LinkedHashMap<String, SanctumLayout>(16, 0.75F, true) {
        private static final long serialVersionUID = 1L;

        @Override
        protected boolean removeEldestEntry(Map.Entry<String, SanctumLayout> eldest) {
            return size() > CACHE_SIZE;
        }
    };

    private SanctumPlacement() {
    }

    private static ArcaneSanctumCategory cfg() {
        return ModConfig.arcaneSanctum;
    }

    public static boolean dimensionAllowed(World world) {
        int dim = world.provider.getDimension();
        // 150: no SRP there, ever. Nether and End: no placement rules yet (spec 16.4).
        if (dim == NO_SRP_DIMENSION || dim == -1 || dim == 1) {
            return false;
        }
        for (int d : cfg().dimensions) {
            if (d == dim) {
                return true;
            }
        }
        return false;
    }

    /**
     * The lowest y a street can be at in this dimension (Street Level), or Integer.MIN_VALUE when
     * the dimension has no buildings over the ground and the shaft simply ends at the surface.
     */
    public static int streetLevel(World world) {
        int dim = world.provider.getDimension();
        for (String line : cfg().streetLevel) {
            String[] parts = line.trim().split("=");
            if (parts.length != 2) {
                continue;
            }
            try {
                if (Integer.parseInt(parts[0].trim()) == dim) {
                    return Integer.parseInt(parts[1].trim());
                }
            } catch (NumberFormatException e) {
                // malformed line: ignored
            }
        }
        return Integer.MIN_VALUE;
    }

    /** The start chunk of a region, or null. */
    public static int[] candidate(World world, int regionX, int regionZ) {
        return SanctumRegions.candidate(seed(world), regionX, regionZ, cfg().regionSize, MARGIN, cfg().chance);
    }

    /** The placement seed of this world's dimension (SanctumRegions.dimensionSeed). */
    public static long seed(World world) {
        return SanctumRegions.dimensionSeed(world.getSeed(), world.provider.getDimension());
    }

    public static synchronized SanctumLayout layout(World world, int chunkX, int chunkZ) {
        String key = world.provider.getDimension() + ":" + chunkX + ":" + chunkZ + ":" + world.getSeed();
        SanctumLayout l = CACHE.get(key);
        if (l == null) {
            l = SanctumLayout.generate(SanctumRegions.layoutSeed(seed(world), chunkX, chunkZ), chunkX * 16 + 8,
                    chunkZ * 16 + 8, cfg().floorYMin, cfg().floorYMax);
            CACHE.put(key, l);
        }
        return l;
    }

    public static boolean biomeAllowed(World world, int x, int z) {
        Biome biome = world.getBiomeProvider().getBiome(new BlockPos(x, 64, z));
        for (String name : cfg().excludedBiomeTypes) {
            for (BiomeDictionary.Type type : BiomeDictionary.Type.getAll()) {
                if (type.getName().equalsIgnoreCase(name.trim()) && BiomeDictionary.hasType(biome, type)) {
                    return false;
                }
            }
        }
        return true;
    }

    /**
     * Decided once per start, at its first writer: the biome must be allowed and no OTHER writer
     * chunk of the footprint may have been populated already (else part of the structure would be
     * missing). Persisted, so later writers only read it.
     */
    public static boolean isValid(World world, int startX, int startZ, SanctumLayout l, int writerX, int writerZ) {
        SanctumWorldData data = SanctumWorldData.get(world);
        long key = ChunkPos.asLong(startX, startZ);
        Boolean decided = data.decision(key);
        if (decided != null) {
            return decided.booleanValue();
        }
        boolean ok = biomeAllowed(world, l.originX, l.originZ) && integrityOk(world, l, writerX, writerZ);
        if (ok) {
            register(world, key, l);
        }
        data.decide(key, ok);
        return ok;
    }

    /** Records a sanctum's anchors and containers for the Arcane Lock (idempotent). */
    public static void register(World world, long sanctumId, SanctumLayout l) {
        java.util.List<Long> anchors = new java.util.ArrayList<Long>();
        for (int[] a : l.anchors) {
            anchors.add(Long.valueOf(new BlockPos(a[0], a[1], a[2]).toLong()));
        }
        java.util.List<Long> containers = new java.util.ArrayList<Long>();
        for (int[] c : l.chests) {
            containers.add(Long.valueOf(new BlockPos(c[0], c[1], c[2]).toLong()));
        }
        for (int[] p : l.props) {
            if (p[3] == SanctumLayout.PROP_EBW_SHELF || p[3] == SanctumLayout.PROP_SHULKER
                    || p[3] == SanctumLayout.PROP_ARCANE_WORKBENCH) {
                containers.add(Long.valueOf(new BlockPos(p[0], p[1], p[2]).toLong()));
            }
        }
        for (int[] d : l.decor) {
            if (d[3] == SanctumLayout.DECOR_SHULKER) {
                containers.add(Long.valueOf(new BlockPos(d[0], d[1], d[2]).toLong()));
            }
        }
        SanctumWorldData.get(world).register(sanctumId, anchors, containers, l.bounds.minX, l.bounds.minZ, l.bounds.maxX,
                l.bounds.maxZ, l.hullMinY, l.hullMaxY,
                SanctumVariants.element(world.provider.getDimension(), l.seed));
    }

    private static boolean integrityOk(World world, SanctumLayout l, int writerX, int writerZ) {
        if (!(world.getChunkProvider() instanceof ChunkProviderServer)) {
            return false;
        }
        ChunkProviderServer provider = (ChunkProviderServer) world.getChunkProvider();
        int minWX = Math.floorDiv(l.bounds.minX - 8, 16);
        int maxWX = Math.floorDiv(l.bounds.maxX - 8, 16);
        int minWZ = Math.floorDiv(l.bounds.minZ - 8, 16);
        int maxWZ = Math.floorDiv(l.bounds.maxZ - 8, 16);
        for (int wx = minWX; wx <= maxWX; wx++) {
            for (int wz = minWZ; wz <= maxWZ; wz++) {
                if ((wx == writerX && wz == writerZ) || !provider.isChunkGeneratedAt(wx, wz)) {
                    continue;
                }
                Chunk chunk = provider.getLoadedChunk(wx, wz);
                if (chunk == null || chunk.isTerrainPopulated()) {
                    return false;
                }
            }
        }
        return true;
    }

    /** The populate box of a writer chunk: the vanilla structure convention, offset by 8. */
    public static SanctumBox writerBox(int chunkX, int chunkZ) {
        return new SanctumBox(chunkX * 16 + 8, 0, chunkZ * 16 + 8, chunkX * 16 + 23, 255, chunkZ * 16 + 23);
    }
}
